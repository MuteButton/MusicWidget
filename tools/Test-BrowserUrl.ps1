param([string]$OutputDirectory='artifacts/browser-url-test', [switch]$ReleaseApk, [string]$AppPackage='io.github.mutebutton.oncue')
. ([scriptblock]::Create((Get-Content tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -AppPackage $AppPackage -OutputDirectory $OutputDirectory -FixturePackage $(if ($ReleaseApk) { 'io.github.mutebutton.oncue.testplayer' } else { $AppPackage })
function Tap-XmlNode($node) {
    if (-not $node) { throw 'Missing UI node' }
    $b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Invoke-Adb @('shell','input','tap',[string][int](($b[0]+$b[2])/2),[string][int](($b[1]+$b[3])/2)) | Out-Null
    Start-Sleep -Milliseconds 400
}
function Select-Player([string]$Label) {
    Tap-Node 'choose_player_button'
    $node=$null
    for($i=0;$i -lt 4;$i++) {
        $node=(Read-Ui).SelectSingleNode("//node[@text='$Label']")
        if($node){break}
        Invoke-Adb @('shell','input','swipe','540','1900','540','500','350') | Out-Null
    }
    Tap-XmlNode $node
}
function Set-Url([string]$Value) {
    Tap-Node 'browser_url_button'
    Tap-XmlNode ((Read-Ui).SelectSingleNode("//node[@class='android.widget.EditText']"))
    Invoke-Adb @('shell','input','keyevent','KEYCODE_MOVE_END') | Out-Null
    Invoke-Adb @('shell','input','keycombination','113','29') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_DEL') | Out-Null
    if($Value) { Invoke-Adb @('shell','input','text',$Value) | Out-Null }
    Tap-XmlNode ((Read-Ui).SelectSingleNode("//node[@resource-id='android:id/button1']"))
}
Invoke-Adb @('shell','am','force-stop',$AppPackage) | Out-Null
if (-not $ReleaseApk) { Invoke-Adb @('shell','run-as',$AppPackage,'cp','shared_prefs/media_widget.xml','files/browser-test-prefs.xml') | Out-Null }
try {
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.MAIN','-f','0x10008000','-n',"$AppPackage/io.github.mutebutton.oncue.MainActivity") | Out-Null
    Select-Player 'YouTube'
    Assert-True ($null -eq (Find-Node (Read-Ui) 'browser_url_button')) 'YouTube hides Open URL'
    Screenshot 'youtube-no-url'
    Select-Player 'Chrome'
    Assert-True ($null -ne (Find-Node (Read-Ui) 'browser_url_button')) 'Chrome shows Open URL'
    Set-Url 'music.youtube.com'
    Assert-True ((Find-Node (Read-Ui) 'browser_url_status').text -eq 'https://music.youtube.com') 'Bare domain saved as HTTPS'
    Screenshot 'chrome-url'
    Select-Player 'YouTube'
    Assert-True ($null -eq (Find-Node (Read-Ui) 'browser_url_button')) 'Switching away hides URL'
    Select-Player 'Chrome'
    Assert-True ((Find-Node (Read-Ui) 'browser_url_status').text -eq 'https://music.youtube.com') 'Browser URL retained across app selection'
    Set-Url 'javascript:alert'
    Assert-True ($null -ne (Read-Ui).SelectSingleNode("//node[@class='android.widget.EditText']")) 'Invalid scheme keeps editor open'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_BACK') | Out-Null
    Tap-XmlNode ((Read-Ui).SelectSingleNode("//node[@resource-id='android:id/button2']"))
    Invoke-Adb @('shell','am','force-stop',$AppPackage) | Out-Null
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.MAIN','-f','0x10008000','-n',"$AppPackage/io.github.mutebutton.oncue.MainActivity") | Out-Null
    Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n',"$FixturePackage/io.github.mutebutton.oncue.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
    Start-Sleep -Seconds 3
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Tap-Node 'track_title'
    $titleTarget=Wait-Activity 'ResumedActivity.*DebugMediaActivity'
    Assert-True ($titleTarget -match 'ResumedActivity.*DebugMediaActivity') 'Active title opens session player despite saved browser URL'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Tap-Node 'album_art'
    $active=Wait-Activity 'ResumedActivity.*DebugMediaActivity'
    Assert-True ($active -match 'ResumedActivity.*DebugMediaActivity') 'Active artwork opens session player despite saved browser URL'
    Invoke-Adb @('shell','am','start','-W','-n',"$AppPackage/io.github.mutebutton.oncue.MainActivity",'-a','io.github.mutebutton.oncue.OPEN_PLAYER') | Out-Null
    Assert-True ((Wait-Activity 'ResumedActivity.*DebugMediaActivity') -match 'ResumedActivity.*DebugMediaActivity') 'Fallback action prioritizes active session over preferred browser URL'
    Fixture 'stop'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Assert-True ($null -ne (Find-Node (Read-Ui) 'btn_open')) 'Stopped final session exposes idle Open App'
    Tap-Node 'btn_open'
    $activities=Wait-Activity 'dat=https://music.youtube.com'
    $activities | Set-Content (Join-Path $OutputDirectory 'browser-launch.txt')
    Assert-True ($activities -match 'dat=https://music.youtube.com' -and $activities -match 'pkg=com.android.chrome') 'Idle Open App dispatches URL explicitly to Chrome'
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.MAIN','-f','0x10008000','-n',"$AppPackage/io.github.mutebutton.oncue.MainActivity") | Out-Null
    Invoke-Adb @('shell','am','force-stop',$AppPackage) | Out-Null
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.MAIN','-f','0x10008000','-n',"$AppPackage/io.github.mutebutton.oncue.MainActivity") | Out-Null
    Set-Url ''
    Assert-True ((Find-Node (Read-Ui) 'browser_url_status').text -like 'No URL set*') 'Blank input clears browser URL'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Tap-Node 'btn_open'
    $normalLaunch=Wait-Activity 'ResumedActivity.*com.android.chrome'
    Assert-True ($normalLaunch -match 'ResumedActivity.*com.android.chrome') 'Idle tap without URL opens the chosen browser normally'
} finally {
    Invoke-Adb @('shell','am','force-stop',$AppPackage) | Out-Null
    if (-not $ReleaseApk) { Invoke-Adb @('shell','run-as',$AppPackage,'cp','files/browser-test-prefs.xml','shared_prefs/media_widget.xml') | Out-Null }
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.MAIN','-f','0x10008000','-n',"$AppPackage/io.github.mutebutton.oncue.MainActivity") | Out-Null
}
