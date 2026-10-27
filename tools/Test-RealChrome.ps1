param([string]$OutputDirectory='artifacts/real-chrome-regression', [switch]$ReleaseApk, [string]$AppPackage='io.github.mutebutton.oncue')
. ([scriptblock]::Create((Get-Content tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -AppPackage $AppPackage -OutputDirectory $OutputDirectory
$OutputDirectory=(Resolve-Path $OutputDirectory).Path
$events=Join-Path $OutputDirectory 'events.jsonl'
$serverScript=(Resolve-Path 'tools/browser-media-server.cjs').Path
$server=$null
function Tap-BrowserNode([string]$XPath) {
    $node=(Read-Ui).SelectSingleNode($XPath)
    if(-not $node){throw "Missing browser node: $XPath"}
    $b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Invoke-Adb @('shell','input','tap',"$([int](($b[0]+$b[2])/2))","$([int](($b[1]+$b[3])/2))") | Out-Null
}
try {
    [IO.File]::WriteAllText($events,'')
    $server=Start-Process -FilePath (Get-Command node).Source -ArgumentList @(('"'+$serverScript+'"'),('"'+$events+'"')) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $OutputDirectory 'server.log') -RedirectStandardError (Join-Path $OutputDirectory 'server-error.log')
    Start-Sleep -Seconds 1
    if($server.HasExited){throw 'Local server failed to start; check port 8765 and server-error.log'}
    Invoke-Adb @('reverse','tcp:8765','tcp:8765') | Out-Null
    # Release the runner's competing debug session before testing Chrome itself.
    Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.musicwidget.testplayer') | Out-Null
    Invoke-Adb @('shell','am','force-stop',$AppPackage) | Out-Null
    Invoke-Adb @('shell','am','start','-W','-n',"$AppPackage/io.github.mutebutton.musicwidget.MainActivity") | Out-Null
    Invoke-Adb @('shell','am','force-stop','com.android.chrome') | Out-Null
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.VIEW','-d','http://127.0.0.1:8765/player','-p','com.android.chrome') | Out-Null
    Assert-Eventually { $null -ne (Read-Ui).SelectSingleNode("//node[@text='Play test audio']") } 'Local Chrome media page loads (Chrome onboarding must already be complete)'
    Tap-BrowserNode "//node[@text='Play test audio']"
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Browser Acceptance Track' } 'Real Chrome media metadata appears in widget'
    Invoke-Adb @('shell','dumpsys','media_session') | Set-Content (Join-Path $OutputDirectory 'chrome-session.txt')
    Screenshot 'chrome-widget'
    Tap-Node 'btn_play'
    Assert-Eventually { (Get-Content $events -Raw) -match 'session-pause' } 'Widget Pause reaches real browser JavaScript handler'
    Tap-Node 'btn_play'
    Assert-Eventually { (Get-Content $events -Raw) -match 'session-play' } 'Widget Play reaches real browser JavaScript handler'
    Tap-Node 'track_title'
    Assert-Eventually { $null -ne (Read-Ui).SelectSingleNode("//node[@text='Widget Browser Acceptance']") } 'Widget opens actual Chrome media page'
    Tap-BrowserNode "//node[@content-desc='New tab']"
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.VIEW','-d','http://127.0.0.1:8765/other','-p','com.android.chrome') | Out-Null
    Assert-Eventually { $null -ne (Read-Ui).SelectSingleNode("//node[@text='Unrelated browser tab']") } 'Unrelated Chrome tab is foreground'
    Screenshot 'other-tab'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Browser Acceptance Track' } 'Browser playback survives selecting another tab'
    Tap-Node 'track_title'
    Assert-Eventually { $null -ne (Read-Ui).SelectSingleNode("//node[@text='Widget Browser Acceptance']") } 'Title restores the playing Chrome tab from another tab'
    Screenshot 'restored-playing-tab'
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.VIEW','-d','http://127.0.0.1:8765/other','-p','com.android.chrome') | Out-Null
    Assert-Eventually { $null -ne (Read-Ui).SelectSingleNode("//node[@text='Unrelated browser tab']") } 'Other tab selected before widget process restart'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    $mainPid=(Invoke-Adb @('shell','pidof',$AppPackage)).Trim()
    if($mainPid -notmatch '^\d+$'){throw "Unexpected widget process id: $mainPid"}
    if($ReleaseApk) {
        # Shipping builds correctly disallow run-as; exercise an actual stop/relaunch.
        Invoke-Adb @('shell','am','force-stop',$AppPackage) | Out-Null
        Invoke-Adb @('shell','am','start','-W','-n',"$AppPackage/io.github.mutebutton.musicwidget.MainActivity") | Out-Null
        Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
        Show-WidgetPage
    } else {
        Invoke-Adb @('shell','run-as',$AppPackage,'kill','-9',$mainPid) | Out-Null
    }
    Assert-Eventually {
        # pidof exits 1 during the expected interval before Android restarts it.
        $newPid=(& $Adb shell pidof $AppPackage) -join ''
        $LASTEXITCODE -eq 0 -and $newPid -match '^\d+$' -and $newPid -ne $mainPid
    } 'Widget listener process restarts while Chrome keeps playing'
    Start-Sleep -Seconds 2
    Tap-Node 'album_art'
    Assert-Eventually { $null -ne (Read-Ui).SelectSingleNode("//node[@text='Widget Browser Acceptance']") } 'Artwork restores playing Chrome tab after widget process death'
} finally {
    Invoke-Adb @('shell','am','force-stop','com.android.chrome') | Out-Null
    Invoke-Adb @('reverse','--remove','tcp:8765') | Out-Null
    if($server -and -not $server.HasExited){Stop-Process -Id $server.Id}
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
}
