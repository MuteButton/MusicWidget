param(
    [string]$Adb = (Join-Path $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }) 'platform-tools/adb.exe'),
    [string]$OutputDirectory = 'artifacts/media-widget-redesign',
    [switch]$DefinitionsOnly,
    [string]$FixturePackage = 'io.github.mutebutton.musicwidget'
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$results = [System.Collections.Generic.List[string]]::new()
function Invoke-Adb([string[]]$Arguments) {
    # Windows PowerShell treats successful adb progress on stderr as an ErrorRecord.
    $ErrorActionPreference = 'Continue'
    $output = & $Adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $Arguments`n$output" }
    return ($output -join "`n")
}
function Pass([string]$Message) {
    $results.Add("PASS: $Message")
    Write-Output "PASS: $Message"
    $results | Set-Content -LiteralPath (Join-Path $OutputDirectory 'acceptance-results.txt')
}
function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAILED: $Message" }
    Pass $Message
}
function Assert-Eventually([scriptblock]$Condition, [string]$Message, [int]$TimeoutSeconds=20) {
    $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if(& $Condition){Pass $Message;return}
        Start-Sleep -Milliseconds 500
    } while([DateTime]::UtcNow -lt $deadline)
    throw "FAILED after waiting for state: $Message"
}
function Read-Ui {
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    do {
        # A stalled Android compositor can leave instrumentation waiting forever.
        # Bound the client wait so this is recorded as infrastructure failure.
        $probe = [Diagnostics.Process]::new()
        $probe.StartInfo = [Diagnostics.ProcessStartInfo]::new($Adb, 'shell am instrument -w io.github.mutebutton.musicwidget.uiprobe/.UiProbe')
        $probe.StartInfo.UseShellExecute = $false
        $probe.StartInfo.CreateNoWindow = $true
        $probe.StartInfo.RedirectStandardOutput = $true
        $probe.StartInfo.RedirectStandardError = $true
        try {
            [void]$probe.Start()
            $stdout=$probe.StandardOutput.ReadToEndAsync()
            $stderr=$probe.StandardError.ReadToEndAsync()
            if(-not $probe.WaitForExit(45000)) {
                $probe.Kill()
                throw 'UI probe timed out after 45 seconds; emulator UI verification cannot proceed.'
            }
            $capture=$stdout.Result + $stderr.Result
            if($probe.ExitCode -ne 0){throw "UI probe ADB failed: $capture"}
        } finally { $probe.Dispose() }
        if ($capture -notmatch 'No active window') { break }
        Start-Sleep -Milliseconds 600
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($capture -notmatch 'UI captured') { throw "UI probe failed: $capture" }
    $xml = Invoke-Adb @('shell','run-as','io.github.mutebutton.musicwidget.uiprobe','cat','files/window.xml')
    $uiPath=Join-Path $OutputDirectory 'latest-ui.xml'
    for($attempt=0; ; $attempt++) {
        try { [IO.File]::WriteAllText($uiPath,$xml); break }
        catch [IO.IOException] {
            if($attempt -ge 4){throw}
            # Editors/evidence readers can briefly hold the preceding capture open.
            Start-Sleep -Milliseconds 100
        }
    }
    if($xml -match 'resource-id="android:id/aerr_(close|wait)"') {
        throw 'Android crash/ANR dialog blocks UI verification; see latest-ui.xml. No widget assertion can be accepted from this screen.'
    }
    return [xml]$xml
}
function Wait-Activity([string]$Pattern, [int]$TimeoutSeconds=30) {
    $deadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $activities=Invoke-Adb @('shell','dumpsys','activity','activities')
        if($activities -match $Pattern){return $activities}
        Start-Sleep -Milliseconds 500
    } while([DateTime]::UtcNow -lt $deadline)
    $activities | Set-Content (Join-Path $OutputDirectory 'activity-timeout.txt')
    throw "Timed out waiting for activity: $Pattern"
}
function Find-Node([xml]$Ui, [string]$Id) {
    return $Ui.SelectSingleNode("//node[@resource-id='io.github.mutebutton.musicwidget:id/$Id']")
}
function Show-WidgetPage {
    # Pinning/resizing can put the widget on a different launcher page.
    Start-Sleep -Milliseconds 600
    foreach ($direction in @('left','left','left','right','right','right','right','right','right')) {
        if (Find-Node (Read-Ui) 'widget_container') { return }
        if ($direction -eq 'left') {
            Invoke-Adb @('shell','input','swipe','930','1050','130','1050','400') | Out-Null
        } else {
            Invoke-Adb @('shell','input','swipe','130','1050','930','1050','400') | Out-Null
        }
        Start-Sleep -Milliseconds 400
    }
    throw 'No Media Widget found on the launcher pages'
}
function Tap-Node([string]$Id) {
    $deadline=[DateTime]::UtcNow.AddSeconds(20)
    do {
        $node = Find-Node (Read-Ui) $Id
        if($node){break}
        Start-Sleep -Milliseconds 500
    } while([DateTime]::UtcNow -lt $deadline)
    if (-not $node) { throw "Missing visible control: $Id" }
    $coords = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($coords[0] + $coords[2]) / 2)
    $y = [int](($coords[1] + $coords[3]) / 2)
    Invoke-Adb @('shell','input','tap',"$x","$y") | Out-Null
    Start-Sleep -Milliseconds 350
}
function Fixture([string]$Command, [string[]]$Extra = @()) {
    $deadline=[DateTime]::UtcNow.AddSeconds(25)
    do {
        $result = Invoke-Adb (@('shell','am','broadcast','-n',"$FixturePackage/io.github.mutebutton.musicwidget.debug.DebugMediaReceiver",'--es','command',$Command) + $Extra)
        if ($result -match 'Fixture command accepted') {
            Start-Sleep -Milliseconds 350
            return
        }
        # Only retry an explicitly unexecuted command while the service starts.
        if($result -notmatch 'fixture is not running'){throw "Fixture rejected $Command : $result"}
        Start-Sleep -Milliseconds 500
    } while([DateTime]::UtcNow -lt $deadline)
    throw "Fixture failed to become ready for $Command : $result"
}
function State {
    return (Invoke-Adb @('shell','run-as',$FixturePackage,'cat','files/debug-media-state.json') | ConvertFrom-Json)
}
function Screenshot([string]$Name) {
    Invoke-Adb @('shell','screencap','-p','/sdcard/widget-test.png') | Out-Null
    Invoke-Adb @('pull','/sdcard/widget-test.png',(Join-Path $OutputDirectory "$Name.png")) | Out-Null
}
if ($DefinitionsOnly) { return }
Invoke-Adb @('shell','cmd','notification','allow_listener','io.github.mutebutton.musicwidget/.MediaListenerService') | Out-Null
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','io.github.mutebutton.musicwidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
Start-Sleep -Seconds 2
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Start-Sleep -Seconds 2
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; ((Find-Node $ui 'track_title').text -eq 'Afterglow') } 'Playing session metadata appears'
Assert-Eventually { $ui=Read-Ui; ((Find-Node $ui 'btn_play').'content-desc' -eq 'Pause') } 'Playing state exposes Pause action'
Assert-Eventually { $ui=Read-Ui; ($null -ne (Find-Node $ui 'progress_bar')) } 'Known duration displays progress'
Assert-Eventually { $ui=Read-Ui; (((Find-Node $ui 'elapsed_time').text -match '^(0|[1-9][0-9]*):[0-5][0-9]$') -and ((Find-Node $ui 'remaining_time').text -match '^-(0|[1-9][0-9]*):[0-5][0-9]$')) } 'Live elapsed and remaining times omit padded minute zeroes'
$playingTime = (Find-Node $ui 'elapsed_time').text
Start-Sleep -Milliseconds 1300
Assert-Eventually { ((Find-Node (Read-Ui) 'elapsed_time').text -ne $playingTime) } 'Elapsed display advances during real playback'
Screenshot '01-playing-debug-fixture'

Tap-Node 'btn_play'
$state = State
Assert-Eventually { (((State).pauseCount -eq 1) -and (-not (State).playing)) } 'Pause tap reaches real MediaSession callback'
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; ((Find-Node $ui 'btn_play').'content-desc' -eq 'Play') } 'Paused state exposes Play action'
$pausedTime = (Find-Node $ui 'progress_bar').'content-desc'
Start-Sleep -Seconds 2
Assert-Eventually { ((Find-Node (Read-Ui) 'progress_bar').'content-desc' -eq $pausedTime) } 'Paused elapsed time remains stable'
Screenshot '02-paused-debug-fixture'

Tap-Node 'btn_play'
Assert-Eventually { (((State).playCount -eq 1) -and (State).playing) } 'Play tap resumes real MediaSession'
Tap-Node 'btn_next'
Assert-Eventually { (((State).nextCount -eq 1) -and ((Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies')) } 'Next tap changes track and artwork'
Screenshot '03-next-track-debug-fixture'
Tap-Node 'btn_prev'
Assert-Eventually { (((State).previousCount -eq 1) -and ((Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow')) } 'Previous tap restores track'

Fixture 'pause'
Fixture 'seek' @('--el','position','120000')
Assert-Eventually { ((Find-Node (Read-Ui) 'progress_bar').'content-desc' -like '2:00 of*') } 'Position-only seek updates elapsed time'
Fixture 'limited'
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; (((Find-Node $ui 'btn_prev').enabled -eq 'false') -and ((Find-Node $ui 'btn_next').enabled -eq 'false')) } 'Unsupported previous/next controls are disabled'
$nextCount = (State).nextCount
Tap-Node 'btn_next'
Assert-Eventually { ((State).nextCount -eq $nextCount) } 'Disabled skip cannot dispatch a command'
Fixture 'unsupported'
Assert-Eventually { ((Find-Node (Read-Ui) 'btn_play').enabled -eq 'false') } 'Unsupported play/pause is disabled'
Screenshot '04-disabled-controls-debug-fixture'
Fixture 'all-actions'

Fixture 'no-art'
Screenshot '05-missing-artwork-debug-fixture'
Fixture 'rapid'
Start-Sleep -Seconds 2
Assert-Eventually { (((Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow') -and (State).artwork) } 'Rapid metadata changes settle on final track'
Screenshot '06-rapid-changes-settled'

Fixture 'metadata' @('--es','title','A_very_long_track_title_that_must_ellipsize_without_covering_the_buttons','--es','artist','A_very_long_artist_name','--el','duration','0')
Assert-Eventually { ($null -eq (Find-Node (Read-Ui) 'progress_bar')) } 'Unknown duration hides progress safely'
Screenshot '07-long-title-unknown-duration'
Fixture 'missing-metadata'
Assert-Eventually { ((Find-Node (Read-Ui) 'track_title').text -eq 'Unknown title') } 'Missing metadata uses a safe title fallback'

Fixture 'reset'
Fixture 'pause'
Fixture 'second-play'
Assert-Eventually { ((Find-Node (Read-Ui) 'track_title').text -ne 'Afterglow') } 'Playing second session takes precedence over paused first session'
Screenshot '08-second-session'
Fixture 'second-stop'
Assert-Eventually { ((Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow') } 'Destroying selected session falls back to remaining session'

Tap-Node 'album_art'
$ui = Read-Ui
Assert-Eventually { $ui=Read-Ui; ($null -ne $ui.SelectSingleNode("//node[contains(@text,'Media widget test player')]")) } 'Artwork opens the selected player activity'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Fixture 'stop'
Assert-Eventually { ((Find-Node (Read-Ui) 'idle_title').text -eq 'No Media Playing') } 'Destroyed final session returns to idle'
Screenshot '09-idle'

Invoke-Adb @('shell','cmd','notification','disallow_listener','io.github.mutebutton.musicwidget/.MediaListenerService') | Out-Null
Start-Sleep -Milliseconds 500
Assert-Eventually { ((Find-Node (Read-Ui) 'idle_title').text -eq 'Connect your music') } 'Revoked notification access shows recovery action'
Screenshot '10-permission-needed'
Tap-Node 'btn_open'
Assert-Eventually { ($null -ne (Find-Node (Read-Ui) 'grant_button')) } 'Connect action opens setup'
Invoke-Adb @('shell','cmd','notification','allow_listener','io.github.mutebutton.musicwidget/.MediaListenerService') | Out-Null
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Start-Sleep -Milliseconds 500
Assert-Eventually { ((Find-Node (Read-Ui) 'idle_title').text -eq 'No Media Playing') } 'Permission restoration recovers widget'
$crashes = Invoke-Adb @('logcat','-d','-b','crash','-t','100')
$crashes | Set-Content -LiteralPath (Join-Path $OutputDirectory 'crash-log.txt')
Assert-Eventually { ($crashes -notmatch 'io.github.mutebutton.musicwidget') } 'No Media Widget crashes recorded'
Write-Output "Completed $($results.Count) checks. Screenshots: $OutputDirectory"
