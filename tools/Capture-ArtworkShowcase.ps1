param([string]$OutputDirectory='artifacts/rim-fix/after')
. ([scriptblock]::Create((Get-Content tools/Preview-ArtworkColors.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
$FixturePackage='io.github.mutebutton.oncue.testplayer'
Add-Type -AssemblyName System.Drawing
Invoke-Adb @('shell','am','start','-W','-n',"$FixturePackage/io.github.mutebutton.oncue.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
Set-GlassMode 'Artwork glass'
$cards=[Collections.Generic.List[Drawing.Bitmap]]::new()
try {
    foreach($track in 0..2){
        if($track -gt 0){Fixture 'next'}
        Fixture 'buffering'; Fixture 'all-actions'; Fixture 'seek' @('--el','position','84000')
        $expected=(State).title
        Assert-Eventually { (Find-Node (Read-Ui) 'track_title').text -eq $expected } "Capture $track shows current track"
        $name="track-$track-artwork"
        Screenshot $name
        $node=Find-Node (Read-Ui) 'media_state'
        $b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
        $rect=[Drawing.Rectangle]::new($b[0],$b[1],$b[2]-$b[0],$b[3]-$b[1])
        $source=[Drawing.Bitmap]::new((Join-Path (Resolve-Path $OutputDirectory).Path "$name.png"))
        try{$card=$source.Clone($rect,[Drawing.Imaging.PixelFormat]::Format32bppArgb)}finally{$source.Dispose()}
        $card.Save((Join-Path (Resolve-Path $OutputDirectory).Path "$name-crop.png"))
        $cards.Add($card)
    }
    $poster=[Drawing.Bitmap]::new($cards[0].Width,($cards[0].Height*3+24))
    $g=[Drawing.Graphics]::FromImage($poster)
    try {
        $g.Clear([Drawing.Color]::FromArgb(11,15,28))
        for($i=0;$i -lt 3;$i++){$g.DrawImageUnscaled($cards[$i],0,$i*($cards[0].Height+12))}
        $poster.Save((Join-Path (Resolve-Path $OutputDirectory).Path 'artwork-glass-showcase.png'))
    }finally{$g.Dispose();$poster.Dispose()}
}finally{foreach($card in $cards){$card.Dispose()};Fixture 'stop'}