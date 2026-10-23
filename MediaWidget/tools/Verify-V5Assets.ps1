param([string]$SourceDirectory='artifacts/v5-source/music-widget-v5',
      [string]$OutputDirectory='artifacts/media-widget-v5-source-audit')
# Audit image layers only; never executes any code from the handoff archive.
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$results=@{}
foreach($state in @('idle','playing')) {
    [xml]$svg=Get-Content -LiteralPath (Join-Path $SourceDirectory "$state-pixel-exact-layered.svg")
    $canvas=[Drawing.Bitmap]::new(1030,225)
    $g=[Drawing.Graphics]::FromImage($canvas)
    foreach($element in $svg.SelectNodes('//*[local-name()="image"]')) {
        $href=$element.GetAttribute('href','http://www.w3.org/1999/xlink')
        if(-not $href){$href=$element.GetAttribute('href')}
        if($href -notmatch '^data:image/png;base64,'){throw 'Expected embedded PNG layer'}
        $bytes=[Convert]::FromBase64String($href.Substring($href.IndexOf(',')+1))
        $stream=[IO.MemoryStream]::new($bytes,$false)
        $layer=[Drawing.Bitmap]::new($stream)
        if($layer.Width -ne [int]$element.width -or $layer.Height -ne [int]$element.height){throw 'Unexpected scaled layer'}
        $g.DrawImageUnscaled($layer,[int]$element.x,[int]$element.y)
        $layer.Dispose(); $stream.Dispose()
    }
    $g.Dispose()
    $canvas.Save((Join-Path $OutputDirectory "$state-reconstructed.png"),[Drawing.Imaging.ImageFormat]::Png)
    $reference=[Drawing.Bitmap]::new((Join-Path $SourceDirectory "REFERENCE-$state.png"))
    $different=0; [long]$errorSum=0
    for($y=0;$y -lt 225;$y++){for($x=0;$x -lt 1030;$x++){
        $a=$canvas.GetPixel($x,$y); $b=$reference.GetPixel($x,$y)
        $d=[Math]::Abs([int]$a.R-$b.R)+[Math]::Abs([int]$a.G-$b.G)+[Math]::Abs([int]$a.B-$b.B)
        if($d -ne 0){$different++}; $errorSum+=$d
    }}
    $results[$state]=@{differentPixels=$different;meanAbsoluteChannelError=$errorSum/(1030*225*3);pixelExact=($different -eq 0)}
    $canvas.Dispose(); $reference.Dispose()
}
@{kind='Source-layer audit only; not an emulator result';states=$results} | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $OutputDirectory 'independent-verification.json')
$results | ConvertTo-Json -Depth 3
