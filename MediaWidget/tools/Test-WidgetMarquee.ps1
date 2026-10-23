param([string]$OutputDirectory='artifacts/auto-marquee-acceptance', [string]$FixturePackage='com.example.mediawidget')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -FixturePackage $FixturePackage
Add-Type -AssemblyName System.Drawing
function Title-Capture([string]$Name) {
    Screenshot $Name
    $source=[Drawing.Bitmap]::new((Join-Path $OutputDirectory "$Name.png"))
    $crop=$source.Clone($script:titleBounds,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $file=Join-Path $OutputDirectory "$Name-title.png"
    $crop.Save($file);$crop.Dispose();$source.Dispose()
    return $file
}
$comparisons=[Collections.Generic.List[object]]::new()
function Title-Difference([string]$First,[string]$Second) {
    $a=[Drawing.Bitmap]::new($First); $b=[Drawing.Bitmap]::new($Second)
    try {
        if($a.Size -ne $b.Size){throw 'Title capture dimensions changed'}
        $changed=0
        for($y=0;$y -lt $a.Height;$y++){for($x=0;$x -lt $a.Width;$x++){
            $c=$a.GetPixel($x,$y);$d=$b.GetPixel($x,$y)
            $firstGlyph=$c.R -gt 210 -and $c.G -gt 210 -and $c.B -gt 210
            $secondGlyph=$d.R -gt 210 -and $d.G -gt 210 -and $d.B -gt 210
            if($firstGlyph -ne $secondGlyph){$changed++}
        }}
        # Ignore small antialiasing changes on reinflation, not glyph movement.
        # Preserve the measured ratio and original PNGs for independent review.
        $ratio=$changed/[double]($a.Width*$a.Height)
        $comparisons.Add(@{first=[IO.Path]::GetFileName($First);second=[IO.Path]::GetFileName($Second);changedGlyphFraction=$ratio;threshold=0.01})
        $comparisons | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'title-comparisons.json')
        return $ratio
    } finally {$a.Dispose();$b.Dispose()}
}
Invoke-Adb @('shell','am','force-stop','com.example.mediawidget') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','com.example.mediawidget/.MainActivity') | Out-Null
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n',"$FixturePackage/com.example.mediawidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
Start-Sleep -Seconds 3
Fixture 'pause'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
$node=Find-Node (Read-Ui) 'track_title'
$b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
$script:titleBounds=[Drawing.Rectangle]::new($b[0],$b[1],$b[2]-$b[0],$b[3]-$b[1])
$long='Midnight_City_Extended_Night_Drive_Mix_With_The_Complete_Concert_Recording'
Fixture 'metadata' @('--es','title',$long,'--es','artist','Test_Ensemble')
Start-Sleep -Seconds 2
$moving=Title-Capture '01-auto-moving'
# Real metadata/state refresh while the native animation is running.
Fixture 'seek' @('--el','position','84000')
Start-Sleep -Seconds 2
$movingLater=Title-Capture '02-auto-moving-later'
Assert-True ((Title-Difference $moving $movingLater) -gt 0.01) 'Long title moves automatically and survives a seek refresh'
Start-Sleep -Seconds 40
$finished=Title-Capture '03-restored'
Assert-True (((Title-Difference $finished $moving) -gt 0.01) -and ((Title-Difference $finished $movingLater) -gt 0.01)) 'Marquee finishes and restores static title'
Start-Sleep -Seconds 3
Assert-True ((Title-Difference (Title-Capture '04-still-restored') $finished) -lt 0.01) 'Finished title remains still instead of looping'
Fixture 'metadata' @('--es','title',$long,'--es','artist','Test_Ensemble')
Fixture 'play'
Start-Sleep -Seconds 2
Assert-True ((Title-Difference (Title-Capture '05-no-restart') $finished) -lt 0.01) 'Duplicate metadata and resume do not restart marquee'
Start-Sleep -Seconds 3
Assert-True ((Title-Difference (Title-Capture '05b-still-no-restart') $finished) -lt 0.01) 'Duplicate metadata title remains static in a second sample'
$time=(Find-Node (Read-Ui) 'elapsed_time').text
Start-Sleep -Seconds 2
Assert-True ((Find-Node (Read-Ui) 'elapsed_time').text -ne $time) 'Live timestamps still advance'
Tap-Node 'track_title'
Assert-True ($null -ne (Read-Ui).SelectSingleNode("//node[contains(@text,'Media widget test player')]")) 'Long title tap opens active media player'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Fixture 'pause'
Fixture 'metadata' @('--es','title','Short')
$short=Title-Capture '06-short'
Start-Sleep -Seconds 3
Assert-True ((Title-Difference (Title-Capture '07-short-still') $short) -lt 0.01) 'Short title stays still'
Tap-Node 'track_title'
Assert-True ($null -ne (Read-Ui).SelectSingleNode("//node[contains(@text,'Media widget test player')]")) 'Short title tap opens active media player'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Fixture 'metadata' @('--es','title',$long)
Start-Sleep -Seconds 2
Assert-True ((Title-Difference (Title-Capture '08-new-track-scroll') $finished) -gt 0.01) 'Returning to a long title starts a new automatic pass'
Fixture 'next'
Start-Sleep -Seconds 40
Assert-True ((Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies') 'Track change cancels old delayed title restoration'
Write-Output "Completed $($results.Count) automatic marquee checks."
