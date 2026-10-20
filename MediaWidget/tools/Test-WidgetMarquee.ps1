param([string]$OutputDirectory='C:/Users/David/Projects/Android/artifacts/marquee-acceptance')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
Add-Type -AssemblyName System.Drawing
function Title-Capture([string]$Name) {
    $ui=Read-Ui
    $title=Find-Node $ui 'track_title'
    Assert-True ($null -ne $title) 'Title remains on the launcher after tapping'
    $b=[regex]::Matches($title.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Screenshot $Name
    $source=[Drawing.Bitmap]::new((Join-Path $OutputDirectory "$Name.png"))
    $crop=$source.Clone([Drawing.Rectangle]::new($b[0],$b[1],$b[2]-$b[0],$b[3]-$b[1]),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $file=Join-Path $OutputDirectory "$Name-title.png"
    $crop.Save($file);$crop.Dispose();$source.Dispose()
    return (Get-FileHash $file -Algorithm SHA256).Hash
}
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','com.example.mediawidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
Start-Sleep -Seconds 3
Fixture 'pause'
$long='Midnight_City_Extended_Night_Drive_Mix'
Fixture 'metadata' @('--es','title',$long,'--es','artist','Test_Ensemble')
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
$before=Title-Capture '01-before'
Tap-Node 'track_title'
Start-Sleep -Seconds 2
$moving=Title-Capture '02-moving'
Assert-True ($before[-1] -ne $moving[-1]) 'Actual title pixels move after tapping a long title'
Assert-True ((Find-Node (Read-Ui) 'track_title').text -eq $long) 'Full title remains available to accessibility while scrolling'
Start-Sleep -Seconds 17
$finished=Title-Capture '03-restored'
Assert-True ($before[-1] -eq $finished[-1]) 'One-pass marquee restores the exact original ellipsized title pixels'
Tap-Node 'track_title'
Start-Sleep -Seconds 2
$again=Title-Capture '04-repeated'
Assert-True ($before[-1] -ne $again[-1]) 'A second tap starts a new scroll'
Tap-Node 'btn_next'
Start-Sleep -Seconds 17
Assert-True ((Find-Node (Read-Ui) 'track_title').text -eq 'Emerald Skies') 'Track change cancels delayed restoration of the previous title'
Fixture 'pause'
Fixture 'metadata' @('--es','title','Short')
$short=Title-Capture '05-short-before'
Tap-Node 'track_title'
Start-Sleep -Seconds 3
$shortAfter=Title-Capture '06-short-after'
Assert-True ($short[-1] -eq $shortAfter[-1]) 'Short title stays visually unchanged when tapped'
Fixture 'metadata' @('--es','title',$long)
Fixture 'play'
$time=(Find-Node (Read-Ui) 'elapsed_time').text
Tap-Node 'track_title'
Start-Sleep -Seconds 3
Assert-True ((Find-Node (Read-Ui) 'elapsed_time').text -ne $time) 'Playback timestamps continue updating during title animation'
Screenshot '07-playing-marquee'
Fixture 'pause'
Tap-Node 'album_art'
$ui=Read-Ui
Assert-True ($null -ne $ui.SelectSingleNode("//node[contains(@text,'Media widget test player')]")) 'Artwork still opens the media player'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Write-Output "Completed $($results.Count) marquee checks."
