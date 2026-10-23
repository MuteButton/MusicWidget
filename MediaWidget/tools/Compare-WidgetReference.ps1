param(
    [string]$Reference = 'artifacts/reference.png',
    [Parameter(Mandatory=$true)][string]$Screenshot,
    [Parameter(Mandatory=$true)][int]$X,
    [Parameter(Mandatory=$true)][int]$Y,
    [Parameter(Mandatory=$true)][int]$Width,
    [Parameter(Mandatory=$true)][int]$Height,
    [switch]$Idle,
    [string]$OutputDirectory = 'artifacts/media-widget-reference-comparison'
)
# Diagnostic only: never used as widget rendering or substituted for an emulator capture.
Add-Type -AssemblyName System.Drawing
if (-not ('WidgetPixelComparison' -as [type])) { Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
public static class WidgetPixelComparison {
    public static double Run(string reference, string screenshot, int x, int y, int width, int height, bool idle, string output) {
        int h = idle ? 235 : 257;
        using (var source = new Bitmap(reference))
        using (var capture = new Bitmap(screenshot))
        using (var target = source.Clone(new Rectangle(38,idle ? 327 : 605,865,h), PixelFormat.Format32bppArgb))
        using (var actual = new Bitmap(865,h))
        using (var difference = new Bitmap(865,h))
        using (var panel = new Bitmap(2595,h+28)) {
            using (var g = Graphics.FromImage(actual)) {
                g.InterpolationMode = InterpolationMode.HighQualityBicubic;
                g.DrawImage(capture, new Rectangle(0,0,865,h), new Rectangle(x,y,width,height), GraphicsUnit.Pixel);
            }
            long error=0; long count=0;
            for (int j=0;j<h;j++) for (int i=0;i<865;i++) {
                var a=target.GetPixel(i,j); var b=actual.GetPixel(i,j);
                int r=Math.Abs(a.R-b.R), green=Math.Abs(a.G-b.G), blue=Math.Abs(a.B-b.B);
                difference.SetPixel(i,j,Color.FromArgb(Math.Min(255,r*3),Math.Min(255,green*3),Math.Min(255,blue*3)));
                // Ignore wallpaper outside the rounded card, include the actual edge and all controls.
                double dx=Math.Max(Math.Max(110-i,i-754),0), dy=Math.Max(Math.Max(110-j,j-(h-111)),0);
                if (dx*dx+dy*dy<=110*110) { error+=r+green+blue; count+=3; }
            }
            using (var g=Graphics.FromImage(panel)) using(var font=new Font("Arial",12)) {
                g.Clear(Color.FromArgb(15,18,30));
                g.DrawString("Reference",font,Brushes.White,8,3);
                g.DrawString("Emulator crop (rescaled to reference width)",font,Brushes.White,873,3);
                g.DrawString("Absolute RGB difference (3x contrast)",font,Brushes.White,1738,3);
                g.DrawImageUnscaled(target,0,28); g.DrawImageUnscaled(actual,865,28); g.DrawImageUnscaled(difference,1730,28);
            }
            panel.Save(output,ImageFormat.Png);
            return (double)error/count;
        }
    }
}
'@
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$name=if ($Idle) { 'comparison-idle' } else { 'comparison' }
$errorValue=[WidgetPixelComparison]::Run($Reference,$Screenshot,$X,$Y,$Width,$Height,$Idle.IsPresent,(Join-Path $OutputDirectory "$name.png"))
@{
    meanAbsoluteChannelError=$errorValue
    channelRange='0..255; zero required for identical pixels'
    comparison='Card crop rescaled to reference dimensions; rounded exterior excluded from metric'
    state=$(if ($Idle) { 'idle' } else { 'playback' })
    sourceScreenshot=$Screenshot
    actualBounds=@($X,$Y,$Width,$Height)
    pixelPerfect=($errorValue -eq 0)
} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory "$name.json")
Write-Output "Mean absolute RGB channel error: $errorValue / 255"
