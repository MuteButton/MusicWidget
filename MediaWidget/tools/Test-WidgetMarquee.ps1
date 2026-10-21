param([string]$OutputDirectory='C:/Users/David/Projects/Android/artifacts/auto-marquee-acceptance')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
Add-Type -AssemblyName System.Drawing
function Title-Capture([string]$Name) {
    Screenshot $Name
    $source=[Drawing.Bitmap]::new((Join-Path $OutputDirectory "$Name.png"))
    $crop=$source.Clone($script:titleBounds,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $file=Join-Path $OutputDirectory "$Name-title.png"
    $crop.Save($file);$crop.Dispose();$source.Dispose()
    return (Get-FileHash $file -Algorithm SHA256).Hash
}
Invoke-Adb @('shell','am','force-stop','com.example.mediawidget') | Out-Null
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','com.example.mediawidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
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
Assert-True ($moving -ne $movingLater) 'Long title moves automatically and survives a seek refresh'
Start-Sleep -Seconds 40
$finished=Title-Capture '03-restored'
Assert-True ($finished -ne $moving -and $finished -ne $movingLater) 'Marquee finishes and restores static title'
Start-Sleep -Seconds 3
Assert-True ((Title-Capture '04-still-restored') -eq $finished) 'Finished title remains still instead of looping'
Fixture 'metadata' @('--es','title',$long,'--es','artist','Test_Ensemble')
Fixture 'play'
Start-Sleep -Seconds 2
Assert-True ((Title-Capture '05-no-restart') -eq $finished) 'Duplicate metadata and resume do not restart marquee'
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
Assert-True ((Title-Capture '07-short-still') -eq $short) 'Short title stays still'
Tap-Node 'track_title'
Assert-True ($null -ne (Read-Ui).SelectSingleNode("//node[contains(@text,'Media widget test player')]")) 'Short title tap opens active media player'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Fixture 'metadata' @('--es','title',$long)
Start-Sleep -Seconds 2
Assert-True ((Title-Capture '08-new-track-scroll') -ne $finished) 'Returning to a long title starts a new automatic pass'
Fixture 'next'
Start-Sleep -Seconds 40
Assert-True ((Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies') 'Track change cancels old delayed title restoration'
Write-Output "Completed $($results.Count) automatic marquee checks."
