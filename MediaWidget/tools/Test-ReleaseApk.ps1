param([Parameter(Mandatory=$true)][string]$Apk,[string]$OutputDirectory='artifacts/oncue-release/acceptance')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -FixturePackage 'io.github.mutebutton.musicwidget.testplayer'
$localHash=(Get-FileHash $Apk -Algorithm SHA256).Hash
$remote=(Invoke-Adb @('shell','pm','path','io.github.mutebutton.musicwidget')).Trim().Substring(8)
$installed=(Invoke-Adb @('shell','sha256sum',$remote)).Split(' ')[0]
Assert-True ($installed -eq $localHash) 'Installed release APK is byte-identical to the final deliverable'
[pscustomobject]@{apk=(Resolve-Path $Apk).Path;sha256=$localHash;api=(Invoke-Adb @('shell','getprop','ro.build.version.sdk'));commit=(& git -c "safe.directory=$((Get-Location).Path.Replace('\','/'))" rev-parse HEAD);fixture=$FixturePackage} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'identity.json')
function Home { Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null; Show-WidgetPage }
function Tap-Text([string]$Text) {
    $n=(Read-Ui).SelectSingleNode("//node[@text='$Text']")
    if(-not $n){throw "Missing text: $Text"}
    $b=[regex]::Matches($n.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Invoke-Adb @('shell','input','tap',"$([int](($b[0]+$b[2])/2))","$([int](($b[1]+$b[3])/2))") | Out-Null
}
function Mode([string]$Name) {
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.MainActivity') | Out-Null
    Tap-Node 'color_mode_button'; Tap-Text $Name
    Assert-Eventually { (Find-Node (Read-Ui) 'color_mode_button').text -eq "Colors: $Name" } "Release settings select $Name"
    Home
}
Invoke-Adb @('shell','cmd','notification','allow_listener','io.github.mutebutton.musicwidget/.MediaListenerService') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n',"$FixturePackage/io.github.mutebutton.musicwidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
try {
    Home
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Release receives external-player metadata'
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').'content-desc' -eq 'Pause' } 'Release shows playing controls'
    $t=(Find-Node (Read-Ui) 'elapsed_time').text
    Start-Sleep -Seconds 2
    Assert-Eventually { (Find-Node (Read-Ui) 'elapsed_time').text -ne $t } 'Release elapsed time advances'
    Tap-Node 'btn_play'
    Assert-Eventually { (State).pauseCount -eq 1 -and -not (State).playing } 'Release Pause reaches external session'
    $t=(Find-Node (Read-Ui) 'progress_bar').'content-desc'
    Start-Sleep -Seconds 2
    Assert-True ((Find-Node (Read-Ui) 'progress_bar').'content-desc' -eq $t) 'Release progress freezes while paused'
    Tap-Node 'btn_play'
    Assert-Eventually { (State).playCount -eq 1 -and (State).playing } 'Release Play resumes external session'
    Tap-Node 'btn_next'
    Assert-Eventually { (State).nextCount -eq 1 -and (Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies' } 'Release Next updates track and artwork'
    Tap-Node 'btn_prev'
    Assert-Eventually { (State).previousCount -eq 1 -and (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Release Previous restores track'
    Fixture 'pause'; Fixture 'seek' @('--el','position','120000')
    Assert-Eventually { (Find-Node (Read-Ui) 'progress_bar').'content-desc' -like '2:00 of*' } 'Release follows position-only seek'
    Screenshot 'playing'
    Fixture 'limited'
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_next').enabled -eq 'false' } 'Release disables unsupported skip'
    $count=(State).nextCount;Tap-Node 'btn_next'
    Assert-True ((State).nextCount -eq $count) 'Disabled release control cannot dispatch'
    Fixture 'unsupported'
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').enabled -eq 'false' } 'Release disables unsupported play/pause'
    Fixture 'all-actions';Fixture 'buffering'
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').'content-desc' -eq 'Pause' } 'Buffering exposes supported Pause'
    $count=(State).pauseCount;Tap-Node 'btn_play'
    Assert-Eventually { (State).pauseCount -gt $count } 'Release can cancel buffering'
    Fixture 'connecting';$count=(State).pauseCount;Tap-Node 'btn_play'
    Assert-Eventually { (State).pauseCount -gt $count } 'Release can cancel connecting'
    Fixture 'near-end' @('--el','remaining','500')
    Assert-Eventually { (Find-Node (Read-Ui) 'remaining_time').text -eq '-0:00' } 'Release clamps progress at track end'
    Fixture 'metadata' @('--el','duration','0')
    Assert-Eventually { $null -eq (Find-Node (Read-Ui) 'progress_bar') } 'Unknown-duration release media hides progress'
    Fixture 'missing-metadata'
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Unknown title' } 'Null metadata has a safe release fallback'
    Fixture 'reset';Fixture 'pause';Fixture 'second-play'
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -ne 'Afterglow' } 'Release selects competing playing session'
    $first=(State).nextCount;$second=(State).secondNextCount;Tap-Node 'btn_next'
    Assert-Eventually { (State).secondNextCount -gt $second -and (State).nextCount -eq $first } 'Release transport is isolated to selected session'
    Fixture 'second-stop'
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Release falls back when selected session disappears'
    Fixture 'rapid'
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Rapid release updates settle on latest track'
    Fixture 'pause'
    foreach($id in @('track_title','track_artist','album_art')) {
        Tap-Node $id
        $target=Wait-Activity 'ResumedActivity.*io.github.mutebutton.musicwidget.debug.DebugMediaActivity'
        Assert-True ($target -match 'ResumedActivity.*io.github.mutebutton.musicwidget.debug.DebugMediaActivity') "Release $id opens external player destination"
        Home
    }
    Mode 'Classic glass'; Screenshot 'classic'
    Mode 'Artwork glass'; Screenshot 'adaptive'
    Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.musicwidget') | Out-Null
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.MainActivity') | Out-Null
    Assert-Eventually { (Find-Node (Read-Ui) 'color_mode_button').text -eq 'Colors: Artwork glass' } 'Release color preference survives restart'
    Home
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Release restores media after app restart'
    Fixture 'no-art';Screenshot 'missing-art'
    Assert-True ($null -ne (Find-Node (Read-Ui) 'album_art')) 'Release missing-art fallback remains actionable'
    Fixture 'art';Fixture 'play'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_SLEEP') | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
    Home
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').'content-desc' -eq 'Pause' } 'Release playback recovers after screen-off/wake'
    Invoke-Adb @('shell','cmd','notification','disallow_listener','io.github.mutebutton.musicwidget/.MediaListenerService') | Out-Null
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_open').text -eq 'Connect' } 'Release exposes permission recovery'
    Tap-Node 'btn_open'
    Assert-True ((Wait-Activity 'ResumedActivity.*io.github.mutebutton.musicwidget/.MainActivity') -match 'MainActivity') 'Release Connect opens setup'
    Invoke-Adb @('shell','cmd','notification','allow_listener','io.github.mutebutton.musicwidget/.MediaListenerService') | Out-Null
    Home
    Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq 'Afterglow' } 'Release recovers after permission restoration'
    Fixture 'stop'
    Assert-Eventually { $null -ne (Find-Node (Read-Ui) 'idle_state') } 'Release returns to idle after final session stops'
    Screenshot 'idle'
} finally {
    Invoke-Adb @('shell','cmd','notification','allow_listener','io.github.mutebutton.musicwidget/.MediaListenerService') | Out-Null
    Invoke-Adb @('shell','am','force-stop',$FixturePackage) | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
}
Write-Output "Completed $($results.Count) signed-release acceptance checks."
