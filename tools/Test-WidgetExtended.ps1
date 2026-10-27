param([string]$OutputDirectory = 'artifacts/media-widget-redesign/extended')
. ([scriptblock]::Create((Get-Content -LiteralPath 'tools/Test-WidgetEmulator.ps1' -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','io.github.mutebutton.oncue/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
Start-Sleep -Seconds 2
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Start-Sleep -Seconds 1
Fixture 'buffering'
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; (((Find-Node $ui 'btn_play').'content-desc' -eq 'Pause') -and ((Find-Node $ui 'btn_play').enabled -eq 'true')) } 'Buffering offers supported Pause action'
Tap-Node 'btn_play'
Assert-Eventually { (((State).pauseCount -eq 1) -and (-not (State).playing)) } 'Buffering can be cancelled via widget Pause'
Fixture 'connecting'
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; (((Find-Node $ui 'btn_play').'content-desc' -eq 'Pause') -and ((Find-Node $ui 'btn_play').enabled -eq 'true')) } 'Connecting offers supported Pause action'
Tap-Node 'btn_play'
Assert-Eventually { (((State).pauseCount -eq 2) -and (-not (State).playing)) } 'Connecting can be cancelled via widget Pause'
Fixture 'near-end' @('--el','remaining','1500')
Start-Sleep -Seconds 3
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; ((Find-Node $ui 'elapsed_time').text -eq '4:42') } 'Elapsed time clamps at track duration'
Assert-Eventually { $ui=Read-Ui; ((Find-Node $ui 'remaining_time').text -eq '-0:00') } 'Remaining countdown stops at zero'
Screenshot '11-end-of-track'

Fixture 'reset'
Fixture 'pause'
$mainPid = (Invoke-Adb @('shell','pidof','io.github.mutebutton.oncue')).Trim()
if ($mainPid -notmatch '^\d+$') { throw "Unexpected main process id: $mainPid" }
Invoke-Adb @('shell','run-as','io.github.mutebutton.oncue','kill','-9',$mainPid) | Out-Null
Start-Sleep -Seconds 3
Assert-Eventually { ((Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow') } 'Widget repopulates after main process death while player survives'
Tap-Node 'btn_next'
Assert-Eventually { (((State).nextCount -eq 1) -and ((Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies')) } 'Controls work after widget process restart'

Fixture 'reset'
try {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_SLEEP') | Out-Null
    Start-Sleep -Seconds 3
} finally {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_MENU') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
}
Start-Sleep -Seconds 1
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; (((Find-Node $ui 'track_title').text -eq 'Afterglow') -and ((Find-Node $ui 'btn_play').'content-desc' -eq 'Pause')) } 'Playback widget recovers after screen off/on'
Fixture 'pause'
try {
    Invoke-Adb @('shell','settings','put','system','font_scale','1.3') | Out-Null
    Start-Sleep -Seconds 1
    Fixture 'metadata' @('--es','title','A_very_long_track_title_that_should_ellipsize','--es','artist','A_very_long_artist_name')
    $ui = Read-Ui
    $title = Find-Node $ui 'track_title'
    $previous = Find-Node $ui 'btn_prev'
    $titleBounds = [regex]::Matches($title.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    $buttonBounds = [regex]::Matches($previous.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    Assert-True (($titleBounds[2] -le $buttonBounds[0]) -and ($null -ne (Find-Node $ui 'btn_next'))) 'Large text stays separate from all playback controls'
    Screenshot '12-large-font-long-title'
} finally { Invoke-Adb @('shell','settings','put','system','font_scale','1.0') | Out-Null }
Fixture 'reset'
Fixture 'pause'
Screenshot '13-final-paused'
Write-Output "Completed $($results.Count) extended checks."
