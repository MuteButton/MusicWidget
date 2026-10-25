param(
    [string]$Apk='artifacts/oncue-release/OnCue-1.2.apk',
    [string]$OutputDirectory='artifacts/oncue-release/background',
    [int]$Cycles=3,
    [string]$AppPackage='io.github.mutebutton.oncue'
)
# Dedicated, already configured emulator only. Uses the actual signed release.
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -AppPackage $AppPackage -OutputDirectory $OutputDirectory -FixturePackage 'io.github.mutebutton.musicwidget.testplayer'
$package=$AppPackage
if((Invoke-Adb @('shell','getprop','ro.kernel.qemu')).Trim() -ne '1'){throw 'This test is restricted to an emulator'}
if($Cycles -lt 1 -or $Cycles -gt 10){throw 'Cycles must be between 1 and 10'}
$remote=(Invoke-Adb @('shell','pm','path',$package)).Trim().Substring(8)
$hash=(Get-FileHash $Apk).Hash
Assert-True ((Invoke-Adb @('shell','sha256sum',$remote)).Split(' ')[0] -eq $hash) 'Background suite uses byte-identical final signed APK'
$originalBucket=(Invoke-Adb @('shell','am','get-standby-bucket',$package)).Trim()
$originalInactive=(Invoke-Adb @('shell','am','get-inactive',$package)) -match 'Idle=true'
$originalDialogs=(Invoke-Adb @('shell','settings','get','global','hide_error_dialogs')).Trim()
$initialBattery=Invoke-Adb @('shell','dumpsys','battery')
if($initialBattery -match 'UPDATES STOPPED'){throw 'Battery simulation already active; refusing to replace another test state'}
if((Invoke-Adb @('shell','dumpsys','deviceidle','get','deep')).Trim() -ne 'ACTIVE'){throw 'Start this suite with the emulator awake and device idle state ACTIVE'}
$completed=$false
$clock=[Diagnostics.Stopwatch]::StartNew()
$samples=[Collections.Generic.List[object]]::new()
function Home {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
}
function Wake {
    Invoke-Adb @('shell','dumpsys','deviceidle','unforce') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
    Home
}
function Snapshot([string]$Name) {
    foreach($service in @('power','alarm','jobscheduler','cpuinfo','deviceidle')){
        Invoke-Adb @('shell','dumpsys',$service) | Set-Content (Join-Path $OutputDirectory "$Name-$service.txt")
    }
}
function Memory([string]$Name) {
    $info=Invoke-Adb @('shell','dumpsys','meminfo',$package)
    $info | Set-Content (Join-Path $OutputDirectory "$Name-memory.txt")
    $value=[regex]::Match($info,'TOTAL PSS:\s+(\d+)')
    if(-not $value.Success){throw 'Missing memory sample'}
    $samples.Add([pscustomobject]@{name=$Name;pssKiB=[int]$value.Groups[1].Value})
    $samples | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'memory.json')
}
function CpuSample {
    $appPid=(Invoke-Adb @('shell','pidof',$package)).Trim()
    $stat=Invoke-Adb @('shell','cat',"/proc/$appPid/stat")
    $fields=($stat -replace '^.*\) ','') -split '\s+'
    [pscustomobject]@{pid=$appPid;ticks=([long]$fields[11]+[long]$fields[12]);time=[DateTime]::UtcNow}
}
function QuietSample([string]$Name) {
    Start-Sleep -Seconds 2
    $before=CpuSample
    Start-Sleep -Seconds 15
    $after=CpuSample
    $hz=[double](Invoke-Adb @('shell','getconf','CLK_TCK')).Trim()
    $percent=100*($after.ticks-$before.ticks)/$hz/($after.time-$before.time).TotalSeconds
    [pscustomobject]@{pid=$after.pid;durationSeconds=($after.time-$before.time).TotalSeconds;cpuPercentOneCore=$percent;limitPercent=5} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory "$Name-cpu-sample.json")
    Assert-True ($before.pid -eq $after.pid -and $percent -ge 0 -and $percent -lt 5) "$Name CPU stays below 5 percent of one core over a 15-second quiet sample"
    Snapshot $Name
    $power=Get-Content (Join-Path $OutputDirectory "$Name-power.txt") -Raw
    $uidText=Invoke-Adb @('shell','pm','list','packages','-U',$package)
    $appUid=[regex]::Match($uidText,"package:$([regex]::Escape($package)) uid:(\d+)").Groups[1].Value
    if(-not $appUid){throw 'Missing package UID'}
    $ownedLocks=@(($power -split "`n") | Where-Object {$_ -match 'WAKE_LOCK' -and ($_ -match "(?<!\d)$appUid(?!\d)" -or $_ -match [regex]::Escape($package))})
    Assert-True ($ownedLocks.Count -eq 0) "$Name has no observed app-owned wake lock"
}
function CheckLive([string]$Label) {
    $expected=(State).title
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq $expected } "$Label restores current title"
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').'content-desc' -eq 'Pause' } "$Label restores playing state"
    $elapsed=(Find-Node (Read-Ui) 'elapsed_time').text
    Start-Sleep -Seconds 2
    Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $elapsed } "$Label progress advances"
    $count=(State).pauseCount
    Tap-Node 'btn_play'
    Assert-Eventually { (State).pauseCount -eq $count+1 } "$Label Pause dispatches exactly once"
    $frozen=(Find-Node (Read-Ui) 'elapsed_time').text
    Start-Sleep -Seconds 2
    Assert-True ((Find-Node (Read-Ui) 'elapsed_time').text -eq $frozen) "$Label paused progress is stable"
    Tap-Node 'btn_play'
    Assert-Eventually { (State).playing } "$Label resumes playback"
}
try {
    Invoke-Adb @('shell','am','start','-W','-n',"$FixturePackage/io.github.mutebutton.musicwidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
    Home
    CheckLive 'Baseline'
    Memory 'warm'
    Invoke-Adb @('shell','dumpsys','battery','unplug') | Out-Null
    foreach($cycle in 1..$Cycles){
        Fixture 'next'
        Invoke-Adb @('shell','input','keyevent','KEYCODE_SLEEP') | Out-Null
        Invoke-Adb @('shell','dumpsys','deviceidle','force-idle') | Out-Null
        Assert-True ((Invoke-Adb @('shell','dumpsys','deviceidle','get','deep')).Trim() -eq 'IDLE') "Cycle $cycle actually enters deep Doze"
        if($cycle -eq 1){QuietSample 'screen-off-doze'}else{Start-Sleep -Seconds 3}
        Wake
        CheckLive "Doze cycle $cycle"
        Memory "cycle-$cycle"
    }
    Fixture 'pause'
    Invoke-Adb @('shell','am','set-inactive',$package,'true') | Out-Null
    $inactive=Invoke-Adb @('shell','am','get-inactive',$package)
    $inactive | Set-Content (Join-Path $OutputDirectory 'standby-state.txt')
    $bucket=(Invoke-Adb @('shell','am','get-standby-bucket',$package)).Trim()
    $standbyRestricted=$inactive -match 'Idle=true'
    [pscustomobject]@{requestedInactive=$true;effectiveInactive=$standbyRestricted;bucket=$bucket;note='Bound home-screen widgets are kept at least ACTIVE by Android; an exempt request is not restrictive-standby coverage.'} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'standby-eligibility.json')
    if($standbyRestricted){Pass 'Android confirms forced app standby'}
    else {
        $hosts=Invoke-Adb @('shell','dumpsys','appwidget')
        $hosts | Set-Content (Join-Path $OutputDirectory 'standby-widget-hosts.txt')
        Assert-True ($bucket -match '^\d+$' -and [int]$bucket -le 10 -and $hosts -match [regex]::Escape($package)) 'Standby request remains ACTIVE with bound widget; restrictive standby is not claimed'
    }
    Start-Sleep -Seconds 3
    Fixture 'next'
    Fixture 'play'
    Home
    CheckLive 'After standby request'
    Invoke-Adb @('shell','am','set-inactive',$package,'false') | Out-Null
    # A bound notification listener is not normally eligible for am kill.
    # Induce one explicit VM crash to exercise unplanned process loss instead.
    $oldPid=(Invoke-Adb @('shell','pidof',$package)).Trim()
    Invoke-Adb @('shell','settings','put','global','hide_error_dialogs','1') | Out-Null
    Invoke-Adb @('shell','am','crash',$oldPid) | Set-Content (Join-Path $OutputDirectory 'injected-crash.txt')
    Assert-Eventually {
        $newPid=(& $Adb shell pidof $package) -join ''
        $LASTEXITCODE -eq 0 -and $newPid.Trim() -match '^\d+$' -and $newPid.Trim() -ne $oldPid
    } 'Notification listener restarts automatically after injected process crash' 60
    Fixture 'next'
    Home
    CheckLive 'Process-loss recovery'
    $beforeStop=(Invoke-Adb @('shell','pidof',$package)).Trim()
    Invoke-Adb @('shell','am','force-stop',$package) | Out-Null
    # A system-bound notification listener may be rebound immediately. Verify
    # termination, but do not assume Android retains the stopped flag forever.
    $afterStop=(& $Adb shell pidof $package) -join ''
    Assert-True ($afterStop.Trim() -ne $beforeStop) 'Force-stop terminates the original app process'
    Invoke-Adb @('shell','dumpsys','package',$package) | Set-Content (Join-Path $OutputDirectory 'after-force-stop-package.txt')
    Invoke-Adb @('shell','am','start','-W','-n',"$package/io.github.mutebutton.musicwidget.MainActivity") | Out-Null
    Home
    Fixture 'next'
    CheckLive 'Explicit reopen after force-stop'
    Fixture 'stop'
    Assert-Eventually { $null -ne (Find-Node (Read-Ui) 'idle_state') } 'No-media state returns after lifecycle tests'
    QuietSample 'no-media-awake'
    Memory 'end'
    Assert-True (($samples[-1].pssKiB-$samples[0].pssKiB) -lt 32768) 'Lifecycle final memory growth stays below 32 MiB (includes intentional process replacement)'
    Invoke-Adb @('shell','dumpsys','activity','exit-info',$package) | Set-Content (Join-Path $OutputDirectory 'exit-history.txt')
    Screenshot 'final-idle'
    $completed=$true
} finally {
    Invoke-Adb @('shell','dumpsys','deviceidle','unforce') | Out-Null
    Invoke-Adb @('shell','dumpsys','battery','reset') | Out-Null
    Invoke-Adb @('shell','am','set-standby-bucket',$package,$originalBucket) | Out-Null
    Invoke-Adb @('shell','am','set-inactive',$package,$originalInactive.ToString().ToLowerInvariant()) | Out-Null
    if($originalDialogs -eq 'null'){Invoke-Adb @('shell','settings','delete','global','hide_error_dialogs') | Out-Null}
    else{Invoke-Adb @('shell','settings','put','global','hide_error_dialogs',$originalDialogs) | Out-Null}
    Invoke-Adb @('shell','am','force-stop',$FixturePackage) | Out-Null
    Wake
    [pscustomobject]@{completed=$completed;apkSha256=$hash;cycles=$Cycles;elapsedSeconds=$clock.Elapsed.TotalSeconds;passed=$results.Count;deepIdle=(Invoke-Adb @('shell','dumpsys','deviceidle','get','deep')).Trim();standby=(Invoke-Adb @('shell','am','get-inactive',$package)).Trim()} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'run-summary.json')
}
