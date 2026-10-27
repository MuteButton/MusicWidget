param([string]$OutputDirectory='artifacts/artwork-glass-preview',[switch]$DefinitionsOnly)
$onlyDefine=$DefinitionsOnly
. ([scriptblock]::Create((Get-Content tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
function Set-GlassMode([string]$Mode) {
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.MainActivity') | Out-Null
    Tap-Node 'color_mode_button'
    $ui=Read-Ui
    $choice=$ui.SelectSingleNode("//node[@text='$Mode']")
    if(-not $choice){throw "Missing color mode: $Mode"}
    $b=[regex]::Matches($choice.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Invoke-Adb @('shell','input','tap',[string][int](($b[0]+$b[2])/2),[string][int](($b[1]+$b[3])/2)) | Out-Null
    $ui=Read-Ui
    Assert-True ((Find-Node $ui 'color_mode_button').text -eq "Colors: $Mode") "Settings select $Mode"
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
}
if($onlyDefine){return}
Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','io.github.mutebutton.musicwidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
Start-Sleep -Seconds 3
foreach($track in 0..2) {
    if($track -gt 0){Fixture 'next'}
    Fixture 'buffering'
    Fixture 'all-actions'
    Fixture 'seek' @('--el','position','84000')
    foreach($mode in @('Classic glass','Artwork glass')) {
        Set-GlassMode $mode
        Screenshot ("track-$track-"+($mode.Split(' ')[0].ToLower()))
    }
}
Fixture 'no-art'
Set-GlassMode 'Artwork glass'
Screenshot 'missing-art-adaptive'
Set-GlassMode 'Classic glass'
Screenshot 'missing-art-classic'
Fixture 'reset'
Fixture 'pause'
