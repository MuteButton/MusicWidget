param([string]$OutputDirectory='artifacts/artwork-colors-acceptance')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Preview-ArtworkColors.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
Add-Type -AssemblyName System.Drawing
function Crop-Hash([string]$File,[Drawing.Rectangle]$Bounds) {
    $image=[Drawing.Bitmap]::new($File)
    $crop=$image.Clone($Bounds,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $stream=[IO.MemoryStream]::new()
    try {
        $crop.Save($stream,[Drawing.Imaging.ImageFormat]::Png)
        return [Convert]::ToBase64String([Security.Cryptography.SHA256]::Create().ComputeHash($stream.ToArray()))
    } finally {$stream.Dispose();$crop.Dispose();$image.Dispose()}
}
$pixelComparisons=[Collections.Generic.List[object]]::new()
function Compare-Crop([string]$First,[string]$Second,[Drawing.Rectangle]$Bounds,[string]$Name) {
    $a=[Drawing.Bitmap]::new($First);$b=[Drawing.Bitmap]::new($Second)
    try {
        $sum=0L;$maximum=0
        for($y=$Bounds.Top;$y -lt $Bounds.Bottom;$y++){for($x=$Bounds.Left;$x -lt $Bounds.Right;$x++){
            $c=$a.GetPixel($x,$y);$d=$b.GetPixel($x,$y)
            foreach($delta in @([Math]::Abs([int]$c.R-$d.R),[Math]::Abs([int]$c.G-$d.G),[Math]::Abs([int]$c.B-$d.B))){$sum+=$delta;$maximum=[Math]::Max($maximum,$delta)}
        }}
        $result=[pscustomobject]@{name=$Name;meanChannelDifference=$sum/(3.0*$Bounds.Width*$Bounds.Height);maximumChannelDifference=$maximum}
        $pixelComparisons.Add($result)
        $pixelComparisons | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'color-pixel-comparisons.json')
        return $result
    } finally {$a.Dispose();$b.Dispose()}
}
Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.musicwidget') | Out-Null
$prefs=[xml](Invoke-Adb @('shell','run-as','io.github.mutebutton.musicwidget','cat','shared_prefs/media_widget.xml'))
$setting=$prefs.SelectSingleNode("/map/boolean[@name='artwork_glass']")
if($setting){[void]$setting.ParentNode.RemoveChild($setting)}
$defaultPrefs=Join-Path $OutputDirectory 'default-colors-prefs.xml'
$prefs.Save($defaultPrefs)
Invoke-Adb @('push',$defaultPrefs,'/data/local/tmp/default-colors-prefs.xml') | Out-Null
Invoke-Adb @('shell','run-as','io.github.mutebutton.musicwidget','cp','/data/local/tmp/default-colors-prefs.xml','shared_prefs/media_widget.xml') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.MainActivity') | Out-Null
Assert-Eventually { (Find-Node (Read-Ui) 'color_mode_button').text -eq 'Colors: Artwork glass' } 'Absent color preference defaults to Artwork glass'
Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
Start-Sleep -Seconds 3
Fixture 'pause'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Assert-Eventually { $null -ne (Find-Node (Read-Ui) 'media_state') } 'Fixture media becomes visible before color sampling'
$node=Find-Node (Read-Ui) 'media_state'
$b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
$scale=($b[2]-$b[0])/1030.0
# Glass-only strip and artwork center exclude text, progress, and adaptive artwork fade.
$glass=[Drawing.Rectangle]::new([int]($b[0]+350*$scale),[int]($b[1]+12*$scale),[int](250*$scale),[int](16*$scale))
$cover=[Drawing.Rectangle]::new([int]($b[0]+70*$scale),[int]($b[1]+60*$scale),[int](100*$scale),[int](100*$scale))
$adaptiveHashes=@()
foreach($track in 0..2) {
    if($track -gt 0){Fixture 'next';Fixture 'pause'}
    Set-GlassMode 'Classic glass'
    Screenshot "track-$track-classic"
    Set-GlassMode 'Artwork glass'
    Screenshot "track-$track-artwork"
    $classic=Join-Path $OutputDirectory "track-$track-classic.png"
    $artwork=Join-Path $OutputDirectory "track-$track-artwork.png"
    $adaptiveHashes+=Crop-Hash $artwork $glass
    $glassDifference=Compare-Crop $classic $artwork $glass "track-$track-glass"
    Assert-True ($glassDifference.meanChannelDifference -gt 2) "Artwork color changes the glass for track $track"
    $coverDifference=Compare-Crop $classic $artwork $cover "track-$track-cover"
    # Software compositing can round a few channels by one level on redraw.
    # Reject any larger channel change or a systematic tint across the cover.
    Assert-True ($coverDifference.maximumChannelDifference -le 1 -and $coverDifference.meanChannelDifference -lt 0.02) "Artwork color preserves the cover within compositor rounding for track $track"
}
Assert-True (@($adaptiveHashes | Select-Object -Unique).Count -eq 3) 'Three different covers produce three different glass palettes'
Fixture 'no-art'
Set-GlassMode 'Artwork glass'
Screenshot 'missing-art-adaptive'
Set-GlassMode 'Classic glass'
Screenshot 'missing-art-classic'
Assert-True ((Crop-Hash (Join-Path $OutputDirectory 'missing-art-adaptive.png') $glass) -eq (Crop-Hash (Join-Path $OutputDirectory 'missing-art-classic.png') $glass)) 'Missing artwork falls back to Classic glass'
Set-GlassMode 'Artwork glass'
Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.musicwidget') | Out-Null
Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.MainActivity') | Out-Null
Assert-True ((Find-Node (Read-Ui) 'color_mode_button').text -eq 'Colors: Artwork glass') 'Color preference survives app process restart'
Set-GlassMode 'Classic glass'
Write-Output "Completed $($results.Count) artwork color checks."

