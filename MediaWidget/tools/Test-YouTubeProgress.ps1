param([string]$OutputDirectory='C:/Users/David/Projects/Android/artifacts/youtube-widget-test')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
Add-Type -AssemblyName System.Drawing
$samples=[Collections.Generic.List[object]]::new()
function Capture([string]$Name) {
    $ui=Read-Ui
    $session=Invoke-Adb @('shell','dumpsys','media_session')
    $session | Set-Content (Join-Path $OutputDirectory "$Name-session.txt")
    Assert-True ($session -match 'package=com.google.android.youtube') 'Real YouTube media session is present'
    $bar=Find-Node $ui 'progress_bar'
    if(-not $bar){throw 'YouTube did not provide a determinate progress bar'}
    $coords=[regex]::Matches($bar.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Screenshot $Name
    $image=[Drawing.Bitmap]::new((Join-Path $OutputDirectory "$Name.png"))
    $row=[int](($coords[1]+$coords[3])/2)
    $last=$coords[0]-1
    for($x=$coords[0];$x -lt $coords[2];$x++) {
        $color=$image.GetPixel($x,$row)
        if($color.B -gt 180 -and ($color.R+$color.G+$color.B) -gt 470){$last=$x}
    }
    $image.Dispose()
    $sample=[pscustomobject]@{name=$Name;utc=[DateTime]::UtcNow.ToString('o');title=(Find-Node $ui 'track_title').text;elapsed=(Find-Node $ui 'elapsed_time').text;remaining=(Find-Node $ui 'remaining_time').text;description=$bar.'content-desc';fillPixels=$last-$coords[0]+1;barWidth=$coords[2]-$coords[0];action=(Find-Node $ui 'btn_play').'content-desc'}
    $samples.Add($sample)
    $samples | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'progress-samples.json')
    Write-Output ($sample | ConvertTo-Json -Compress)
}
Show-WidgetPage
Capture '01-playing'
Start-Sleep -Seconds 12
Capture '02-playing-later'
Assert-True ($samples[1].elapsed -ne $samples[0].elapsed) 'YouTube elapsed timestamp advances'
Assert-True ($samples[1].remaining -ne $samples[0].remaining) 'YouTube remaining timestamp counts down'
Assert-True ($samples[1].fillPixels -gt $samples[0].fillPixels) 'Visible YouTube progress fill grows in actual screenshots'
Tap-Node 'btn_play'
Start-Sleep -Seconds 2
Capture '03-paused'
Start-Sleep -Seconds 8
Capture '04-paused-later'
Assert-True ($samples[2].action -eq 'Play') 'Widget Pause pauses the YouTube session'
Assert-True (($samples[2].elapsed -eq $samples[3].elapsed) -and ($samples[2].remaining -eq $samples[3].remaining)) 'Both YouTube timestamps remain stable while paused'
Assert-True ($samples[2].fillPixels -eq $samples[3].fillPixels) 'Visible YouTube progress fill stays fixed while paused'
Tap-Node 'btn_play'
Start-Sleep -Seconds 12
Capture '05-resumed'
Assert-True (($samples[4].action -eq 'Pause') -and ($samples[4].fillPixels -gt $samples[3].fillPixels)) 'Widget Play resumes YouTube and visible progress advances'
Tap-Node 'btn_play'
