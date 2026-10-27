param([string]$OutputDirectory='artifacts/title-transition/focused')
. ([scriptblock]::Create((Get-Content tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -FixturePackage 'io.github.mutebutton.musicwidget.testplayer' -AppPackage 'io.github.mutebutton.oncue'
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n',"$FixturePackage/io.github.mutebutton.musicwidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Initial title is displayed'
$t=(Find-Node (Read-Ui) 'elapsed_time').text
Fixture 'metadata' @('--es','title','m.youtube.com')
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'YouTube' } 'Persistent hostname expires to neutral YouTube title without another metadata event'
Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $t } 'Timestamp continues advancing across title grace period and expiry'
Fixture 'seek' @('--el','position','120000')
Assert-Eventually { (Find-Node (Read-Ui) 'progress_bar').'content-desc' -like '2:* of*' } 'Position-only seek reaches progress while placeholder is displayed'
Fixture 'metadata' @('--es','title','Next_song')
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Next_song' } 'Real title replaces the neutral fallback'
Fixture 'pause'
$t=(Find-Node (Read-Ui) 'elapsed_time').text
Fixture 'metadata' @('--es','title','music.youtube.com')
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'YouTube' } 'Paused placeholder also expires without the playback ticker'
Assert-True ((Find-Node (Read-Ui) 'elapsed_time').text -eq $t) 'Title expiry does not advance paused playback'
Fixture 'play'
Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $t } 'Playback resumes advancing after title expiry'
Fixture 'next'
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies' } 'Next still updates real track metadata'
Screenshot 'verified'
