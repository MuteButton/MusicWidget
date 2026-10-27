param(
    [Parameter(Mandatory=$true)][string]$Screenshot,
    [Parameter(Mandatory=$true)][int]$X,
    [Parameter(Mandatory=$true)][int]$Y,
    [Parameter(Mandatory=$true)][int]$Width,
    [Parameter(Mandatory=$true)][int]$Height,
    [switch]$Idle,
    [string]$SourceDirectory = 'artifacts/v5-source/oncue-v5',
    [string]$OutputDirectory = 'artifacts/media-widget-v5-comparison'
)
Add-Type -AssemblyName System.Drawing
if (-not ('WidgetV5Comparison' -as [type])) { Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
public static class WidgetV5Comparison {
    public static double[] Run(string reference,string screenshot,int x,int y,int width,int height,string output) {
        using(var target=new Bitmap(reference))
        using(var capture=new Bitmap(screenshot))
        using(var actual=new Bitmap(1030,225))
        using(var diff=new Bitmap(1030,225))
        using(var panel=new Bitmap(1030,735)) {
            using(var g=Graphics.FromImage(actual)) {
                g.InterpolationMode=InterpolationMode.HighQualityBicubic;
                g.PixelOffsetMode=PixelOffsetMode.Half;
                g.DrawImage(capture,new Rectangle(0,0,1030,225),new Rectangle(x,y,width,height),GraphicsUnit.Pixel);
            }
            long total=0,different=0; int maximum=0;
            for(int j=0;j<225;j++) for(int i=0;i<1030;i++) {
                var a=target.GetPixel(i,j); var b=actual.GetPixel(i,j);
                int r=Math.Abs(a.R-b.R),g=Math.Abs(a.G-b.G),bl=Math.Abs(a.B-b.B);
                total+=r+g+bl; if(r+g+bl>0)different++;
                maximum=Math.Max(maximum,Math.Max(r,Math.Max(g,bl)));
                diff.SetPixel(i,j,Color.FromArgb(Math.Min(255,3*r),Math.Min(255,3*g),Math.Min(255,3*bl)));
            }
            using(var g=Graphics.FromImage(panel)) using(var font=new Font("Arial",11)) {
                g.Clear(Color.FromArgb(10,14,25));
                g.DrawString("V5 reference",font,Brushes.White,5,2); g.DrawImageUnscaled(target,0,20);
                g.DrawString("Actual emulator crop normalized to 1030 x 225",font,Brushes.White,5,247); g.DrawImageUnscaled(actual,0,265);
                g.DrawString("Absolute RGB difference, amplified 3x",font,Brushes.White,5,492); g.DrawImageUnscaled(diff,0,510);
            }
            panel.Save(output,ImageFormat.Png);
            return new double[]{(double)total/(1030*225*3),different,maximum};
        }
    }
}
'@ }
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$state=if($Idle){'idle'}else{'playing'}
$values=[WidgetV5Comparison]::Run((Join-Path $SourceDirectory "REFERENCE-$state.png"),$Screenshot,$X,$Y,$Width,$Height,(Join-Path $OutputDirectory "$state-comparison.png"))
@{
    target="REFERENCE-$state.png"; sourceScreenshot=$Screenshot; actualBounds=@($X,$Y,$Width,$Height)
    comparison='All RGB pixels included; actual crop normalized to 1030x225, no exterior exclusions'
    meanAbsoluteChannelError=$values[0]; differentPixels=$values[1]; maxChannelDifference=$values[2]
    nativeResolution=($Width -eq 1030 -and $Height -eq 225)
    pixelPerfect=($values[1] -eq 0 -and $Width -eq 1030 -and $Height -eq 225)
} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory "$state-comparison.json")
Write-Output "$state RGB mean error: $($values[0]); differing pixels: $($values[1]) / 231750"
