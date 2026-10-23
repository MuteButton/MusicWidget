param([string]$OutputDirectory = 'artifacts/media-widget-v5-comparison')
. ([scriptblock]::Create((Get-Content -LiteralPath 'MediaWidget/tools/Test-WidgetEmulator.ps1' -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
$packagePaths = Invoke-Adb @('shell','pm','path','com.example.mediawidget')
$packageLine = ($packagePaths -split "`n" | Where-Object { $_ -match '^package:/data/app/.*/base\.apk$' } | Select-Object -First 1)
if (-not $packageLine) { throw "Unexpected installed APK path: $packagePaths" }
$installedPath = $packageLine.Substring(8).Trim()
$installedHash = (Invoke-Adb @('shell','sha256sum',$installedPath)).Split(' ')[0]
$localHash = (Get-FileHash 'MediaWidget/app/build/outputs/apk/debug/app-debug.apk' -Algorithm SHA256).Hash
Assert-True ($installedHash -eq $localHash) 'Installed APK matches the final local build'
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','com.example.mediawidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
Start-Sleep -Seconds 3
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
$ui = Read-Ui
$button = Find-Node $ui 'btn_play'
$buttonBounds = [regex]::Matches($button.bounds,'\d+') | ForEach-Object { [int]$_.Value }
$rowBounds = [regex]::Matches((Find-Node $ui 'media_state').bounds,'\d+') | ForEach-Object { [int]$_.Value }
Assert-True (($buttonBounds[0] -ge $rowBounds[0]) -and ($buttonBounds[1] -ge $rowBounds[1]) -and ($buttonBounds[2] -le $rowBounds[2]) -and ($buttonBounds[3] -le $rowBounds[3])) 'Full playback button touch area lies inside its row'
$before = (State).pauseCount
Invoke-Adb @('shell','input','tap',[string][int](($buttonBounds[0]+$buttonBounds[2])/2),[string]($buttonBounds[3]-2)) | Out-Null
Start-Sleep -Milliseconds 500
Assert-Eventually { (State).pauseCount -eq ($before+1) } 'Bottom edge of Pause reaches the real MediaSession'
Tap-Node 'btn_play'
Assert-Eventually { (State).playing } 'Center of Play resumes playback'
Fixture 'buffering'
Fixture 'all-actions'
Fixture 'seek' @('--el','position','84000')
$ui = Read-Ui
$playbackBounds = (Find-Node $ui 'media_state').bounds
Screenshot 'emulator'
Fixture 'stop'
$ui = Read-Ui
Assert-Eventually { (Find-Node (Read-Ui) 'idle_title').text -eq 'No Media Playing' } 'Final glass surface returns to idle'
$ui = Read-Ui
$idleBounds = (Find-Node $ui 'idle_state').bounds
Screenshot 'idle'
$launchPreferences=[xml](Invoke-Adb @('shell','run-as','com.example.mediawidget','cat','shared_prefs/media_widget.xml'))
$destination=@($launchPreferences.map.string | Where-Object {$_.name -eq 'preferred_player'} | ForEach-Object {$_.'#text'}) | Select-Object -First 1
if(-not $destination){$destination=@($launchPreferences.map.string | Where-Object {$_.name -eq 'last_player'} | ForEach-Object {$_.'#text'}) | Select-Object -First 1}
Tap-Node 'btn_open'
if($destination){Wait-Activity ('ResumedActivity.*'+[regex]::Escape($destination)) | Out-Null}
$ui = Read-Ui
$chooser=$null -ne $ui.SelectSingleNode("//node[@text='Choose media app']")
$playerVisible=$false
if (-not $chooser) {
    $preferences=[xml](Invoke-Adb @('shell','run-as','com.example.mediawidget','cat','shared_prefs/media_widget.xml'))
    $players=@($preferences.map.string | Where-Object {$_.name -in @('preferred_player','last_player')} | ForEach-Object {$_.'#text'})
    $playerVisible=@($ui.SelectNodes('//node') | Where-Object {$_.package -in $players}).Count -gt 0
}
Assert-True ($chooser -or $playerVisible) 'Open App opens the chooser or configured/remembered media player'
Invoke-Adb @('shell','input','keyevent','KEYCODE_BACK') | Out-Null
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
@{playback=$playbackBounds; idle=$idleBounds; installedApkSha256=$installedHash} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'capture-bounds.json')
foreach ($capture in @(@{file='emulator';bounds=$playbackBounds;idle=$false},@{file='idle';bounds=$idleBounds;idle=$true})) {
    $b=[regex]::Matches($capture.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    & ([scriptblock]::Create((Get-Content -LiteralPath 'MediaWidget/tools/Compare-WidgetV5.ps1' -Raw))) -Screenshot (Join-Path $OutputDirectory ($capture.file+'.png')) -X $b[0] -Y $b[1] -Width ($b[2]-$b[0]) -Height ($b[3]-$b[1]) -Idle:$capture.idle -OutputDirectory $OutputDirectory
}
Write-Output "Completed $($results.Count) final glass checks."
