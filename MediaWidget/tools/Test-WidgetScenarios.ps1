param([string]$OutputDirectory='artifacts/additional-feature-scenarios')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
function Home-Widget {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
}
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','io.github.mutebutton.musicwidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
Fixture 'pause'
Home-Widget
Tap-Node 'track_artist'
Wait-Activity 'ResumedActivity.*io.github.mutebutton.musicwidget/.debug.DebugMediaActivity' | Out-Null
Assert-True ($null -ne (Read-Ui).SelectSingleNode("//node[contains(@text,'Media widget test player')]")) 'Artist tap opens the active media player'
Home-Widget
Fixture 'second-play'
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Second Session 1' } 'Playing second session supersedes paused first session'
$before=State
Tap-Node 'btn_next'
Assert-Eventually { $s=State; $s.secondNextCount -eq ($before.secondNextCount+1) -and $s.nextCount -eq $before.nextCount } 'Next reaches only the selected second session'
Tap-Node 'btn_prev'
Assert-Eventually { $s=State; $s.secondPreviousCount -eq ($before.secondPreviousCount+1) -and $s.previousCount -eq $before.previousCount } 'Previous reaches only the selected second session'
Tap-Node 'btn_play'
Assert-Eventually { $s=State; $s.secondPauseCount -eq ($before.secondPauseCount+1) -and $s.pauseCount -eq $before.pauseCount } 'Pause reaches only the selected second session'
Fixture 'second-play'
Fixture 'second-stop'
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Destroying the selected session restores the surviving session'
$long='Midnight_City_Extended_Night_Drive_Mix_With_The_Complete_Concert_Recording'
Fixture 'metadata' @('--es','title',$long)
Start-Sleep -Seconds 2
try {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_SLEEP') | Out-Null
    Fixture 'metadata' @('--es','title','Changed_While_Asleep')
} finally {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_MENU') | Out-Null
    Home-Widget
}
Start-Sleep -Seconds 40
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Changed_While_Asleep' } 'Interrupted marquee cannot restore an old title after screen wake'
Screenshot 'screen-wake-new-title'
Fixture 'metadata' @('--es','title',$long)
Start-Sleep -Seconds 2
Fixture 'stop'
Start-Sleep -Seconds 40
Assert-Eventually { $ui=Read-Ui; $null -ne (Find-Node $ui 'btn_open') -and $null -eq (Find-Node $ui 'track_title') } 'Session disappearance during marquee remains idle after delayed callbacks'
Screenshot 'session-stopped-idle'
Write-Output "Completed $($results.Count) additional scenario checks."
