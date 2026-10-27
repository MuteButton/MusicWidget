param([string]$OutputDirectory='artifacts/ticker-optimization/acceptance')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -AppPackage io.github.mutebutton.oncue -FixturePackage io.github.mutebutton.musicwidget.testplayer
Invoke-Adb @('shell','cmd','notification','allow_listener',"$AppPackage/io.github.mutebutton.musicwidget.MediaListenerService") | Out-Null
Invoke-Adb @('shell','am','start','-W','-n',"$AppPackage/io.github.mutebutton.musicwidget.MainActivity") | Out-Null
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n',"$FixturePackage/io.github.mutebutton.musicwidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').'content-desc' -eq 'Pause' } 'Playing fixture selected'
$t=(Find-Node (Read-Ui) 'elapsed_time').text
Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $t } 'Normal playing timestamps continue advancing'
Fixture 'metadata' @('--el','position','-1')
Assert-Eventually { $null -eq (Find-Node (Read-Ui) 'progress_bar') } 'Raw unknown position hides timeline without fabricated time'
Fixture 'seek' @('--el','position','120000')
Assert-Eventually { $null -ne (Find-Node (Read-Ui) 'progress_bar') } 'Seek callback recovers from unknown position'
Fixture 'metadata' @('--el','duration','0')
Assert-Eventually { $null -eq (Find-Node (Read-Ui) 'progress_bar') } 'Unknown duration hides unusable timeline'
Fixture 'metadata' @('--el','duration','282000','--el','position','120000')
Assert-Eventually { (Find-Node (Read-Ui) 'progress_bar').'content-desc' -like '2:* of*' } 'Metadata callback restores timeline after unknown duration'
$t=(Find-Node (Read-Ui) 'elapsed_time').text
Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $t } 'Ticker restarts when usable timing returns'
Fixture 'pause'
Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').'content-desc' -eq 'Play' } 'Pause delivered'
$t=(Find-Node (Read-Ui) 'elapsed_time').text
Start-Sleep -Seconds 3
Assert-True ((Find-Node (Read-Ui) 'elapsed_time').text -eq $t) 'Paused timestamp remains stable'
Fixture 'play'
Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $t } 'Resume restarts timing'
Fixture 'near-end' @('--el','remaining','500')
Assert-Eventually { (Find-Node (Read-Ui) 'remaining_time').text -eq '-0:00' } 'Final timestamp still delivered and clamped'
Fixture 'next'
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies' } 'Next track restores metadata'
$t=(Find-Node (Read-Ui) 'elapsed_time').text
Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $t } 'Next track restarts ticker after end'

