param(
    [string]$OutputDirectory = ('artifacts/full-regression-' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
    [string]$Adb = (Join-Path $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }) 'platform-tools/adb.exe'),
    [ValidateSet('core','lifecycle','marquee','browser','additional_scenarios','idle_routing','colors','launcher_glass','native_idle','native_playing','real_chrome')]
    [string[]]$Suites = @('core','lifecycle','marquee','browser','additional_scenarios','idle_routing','colors','launcher_glass','native_idle','native_playing','real_chrome')
)
$ErrorActionPreference='Stop'
$repo=if($PSScriptRoot){Split-Path $PSScriptRoot -Parent}else{(Get-Location).Path}
if(-not $repo -or -not (Test-Path (Join-Path $repo 'settings.gradle'))) { $repo=(Get-Location).Path }
Set-Location $repo
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
function Adb-Run([string[]]$Arguments) {
    $ErrorActionPreference='Continue'
    $out=& $Adb @Arguments 2>&1
    if($LASTEXITCODE -ne 0){throw "ADB failed: $Arguments : $out"}
    return ($out -join "`n")
}
$devices=(Adb-Run @('devices')) -split "`n" | ForEach-Object { $_.Trim() }
if(@($devices -split "`n" | Where-Object {$_ -match '^emulator-\d+\s+device'}).Count -ne 1 -or
   @($devices -split "`n" | Where-Object {$_ -match '\s+device$'}).Count -ne 1) {
    throw 'Connect exactly one running Android emulator and no other ADB devices.'
}
$apk=Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk'
$remote=((Adb-Run @('shell','pm','path','io.github.mutebutton.oncue')) -split "`n" | Where-Object {$_ -like 'package:*/base.apk'} | Select-Object -First 1)
if(-not $remote){throw 'Install the debug APK first.'}
$installed=(Adb-Run @('shell','sha256sum',$remote.Substring(8).Trim())).Split(' ')[0]
if($installed -ne (Get-FileHash $apk -Algorithm SHA256).Hash){throw 'Installed APK does not match the local debug build.'}
if((Adb-Run @('shell','pm','path','io.github.mutebutton.oncue.uiprobe')) -notmatch 'package:'){throw 'Install the UI probe first (tools/ui-probe/Build-Probe.ps1).'}
$summary=[Collections.Generic.List[object]]::new()
$context=[ordered]@{
    startedUtc=[DateTime]::UtcNow.ToString('o')
    commit=(& git -c "safe.directory=$($repo.Replace('\','/'))" rev-parse HEAD | Select-Object -First 1)
    apkSha256=$installed
    device=(Adb-Run @('shell','getprop','ro.product.model')).Trim()
    api=(Adb-Run @('shell','getprop','ro.build.version.sdk')).Trim()
    resolution=(Adb-Run @('shell','wm','size')).Trim()
    density=(Adb-Run @('shell','wm','density')).Trim()
}
$context | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'environment.json')
@(
    @{area='Real YouTube integration';status='NOT_RUN';reason='Requires prepared live video; run Test-YouTubeProgress.ps1 separately'}
    @{area='Other launchers/devices/API versions';status='NOT_RUN';reason='This run is scoped to the connected emulator'}
    @{area='Drag resize, orientation, multiple widgets';status='NOT_RUN';reason='Launcher-specific scenarios not automated by this suite'}
    @{area='Battery/CPU/long-duration profiling';status='NOT_RUN';reason='Functional regression is not a performance benchmark'}
) | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'coverage-limits.json')
Get-ChildItem tools/*.ps1 | Get-FileHash -Algorithm SHA256 |
    Select-Object Path,Hash | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'test-script-hashes.json')
$font=(Adb-Run @('shell','settings','get','system','font_scale')).Trim()
$listeners=(Adb-Run @('shell','settings','get','secure','enabled_notification_listeners')).Trim()
$listener='io.github.mutebutton.oncue/.MediaListenerService'
Adb-Run @('shell','am','force-stop','io.github.mutebutton.oncue') | Out-Null
Adb-Run @('shell','run-as','io.github.mutebutton.oncue','cp','shared_prefs/media_widget.xml','files/full-regression-prefs.xml') | Out-Null
$stages=[ordered]@{
    core='Test-WidgetEmulator.ps1'
    lifecycle='Test-WidgetExtended.ps1'
    marquee='Test-WidgetMarquee.ps1'
    browser='Test-BrowserUrl.ps1'
    additional_scenarios='Test-WidgetScenarios.ps1'
    idle_routing='Test-IdleRouting.ps1'
    colors='Test-ArtworkColors.ps1'
    launcher_glass='Test-WidgetGlass.ps1'
    native_idle='Test-WidgetNativeReference.ps1'
    native_playing='Test-WidgetNativePlaying.ps1'
    real_chrome='Test-RealChrome.ps1'
}
try {
    Adb-Run @('shell','cmd','notification','allow_listener',$listener) | Out-Null
    foreach($stage in @($stages.GetEnumerator() | Where-Object { $_.Key -in $Suites })) {
        $stageDirectory=Join-Path $OutputDirectory $stage.Key
        New-Item -ItemType Directory -Force $stageDirectory | Out-Null
        Get-ChildItem tools/*.ps1 | Get-FileHash -Algorithm SHA256 |
            Select-Object Path,Hash | ConvertTo-Json | Set-Content (Join-Path $stageDirectory 'test-script-hashes.json')
        $started=[DateTime]::UtcNow
        Write-Output "START: $($stage.Key)"
        try {
            # This is a dedicated test emulator; release browsers/players left by navigation checks.
            Adb-Run @('shell','am','force-stop','com.android.chrome') | Out-Null
            Adb-Run @('shell','am','force-stop','com.google.android.youtube') | Out-Null
            # Each group gets a fresh service; an earlier group may have stopped it.
            Adb-Run @('shell','am','force-stop','io.github.mutebutton.oncue') | Out-Null
            Adb-Run @('shell','am','start','-W','-n','io.github.mutebutton.oncue/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
            Start-Sleep -Seconds 5
            & ([scriptblock]::Create((Get-Content (Join-Path $repo "tools/$($stage.Value)") -Raw))) -OutputDirectory $stageDirectory |
                Tee-Object -FilePath (Join-Path $stageDirectory 'run.log')
            $checks=@(Get-Content (Join-Path $stageDirectory 'acceptance-results.txt') | Where-Object {$_ -like 'PASS:*'}).Count
            $summary.Add([pscustomobject]@{suite=$stage.Key;status='PASS';checks=$checks;seconds=[int]([DateTime]::UtcNow-$started).TotalSeconds;error=$null})
        } catch {
            $summary.Add([pscustomobject]@{suite=$stage.Key;status='FAIL';checks=@(Get-Content (Join-Path $stageDirectory 'acceptance-results.txt') -ErrorAction SilentlyContinue | Where-Object {$_ -like 'PASS:*'}).Count;seconds=[int]([DateTime]::UtcNow-$started).TotalSeconds;error=$_.Exception.Message})
            Write-Output "FAIL: $($stage.Key): $($_.Exception.Message)"
        }
        $summary | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $OutputDirectory 'summary.json')
    }
} finally {
    Adb-Run @('shell','am','force-stop','io.github.mutebutton.oncue') | Out-Null
    Adb-Run @('shell','run-as','io.github.mutebutton.oncue','cp','files/full-regression-prefs.xml','shared_prefs/media_widget.xml') | Out-Null
    Adb-Run @('shell','settings','put','system','font_scale',$font) | Out-Null
    if($listeners -notmatch 'io.github.mutebutton.oncue/') {Adb-Run @('shell','cmd','notification','disallow_listener',$listener) | Out-Null}
    Adb-Run @('shell','am','start','-W','-n','io.github.mutebutton.oncue/.MainActivity') | Out-Null
}
$summary | Format-Table suite,status,checks,seconds -AutoSize
Write-Output "Evidence: $OutputDirectory"
Write-Output 'NOT RUN: Real-player integration (Test-YouTubeProgress.ps1 requires a playing real video), Samsung One UI/physical hardware, other API levels, launcher resize/multiple-widget interactions.'
if(@($summary | Where-Object status -eq 'FAIL').Count -gt 0){throw 'One or more emulator regression suites failed. See summary.json.'}
