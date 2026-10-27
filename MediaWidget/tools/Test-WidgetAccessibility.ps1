param([string]$OutputDirectory='artifacts/accessibility-conservative/edges')
# Set ANDROID_SERIAL to the prepared test emulator before running.
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -AppPackage io.github.mutebutton.oncue -FixturePackage io.github.mutebutton.musicwidget.testplayer
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n',"$FixturePackage/io.github.mutebutton.musicwidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Fixture is selected before edge testing'
Fixture 'metadata' @('--es','title','A_very_long_title_for_accessibility_that_must_remain_complete_when_visually_clipped')
$node=Find-Node (Read-Ui) 'track_title'
Assert-True ($node.'content-desc' -eq 'Open player for A_very_long_title_for_accessibility_that_must_remain_complete_when_visually_clipped') 'Accessibility label exposes the full title and open-player action'
foreach($item in @(@{id='btn_prev';counter='previousCount'},@{id='btn_next';counter='nextCount'},@{id='btn_play';counter='pauseCount'})) {
 $node=Find-Node (Read-Ui) $item.id
 $bounds=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
 $before=(State).($item.counter)
 # Upper edge lies outside the old target, inside the expanded target.
 $x=[int](($bounds[0]+$bounds[2])/2);$y=$bounds[1]+1
 Invoke-Adb @('shell','input','tap',"$x","$y") | Out-Null
 Assert-Eventually { (State).($item.counter) -eq $before+1 } "Expanded edge dispatches $($item.id) exactly once"
}
Fixture 'stop'
