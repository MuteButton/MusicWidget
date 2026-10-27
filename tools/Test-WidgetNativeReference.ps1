param([string]$OutputDirectory='artifacts/media-widget-v5-native-idle')
. ([scriptblock]::Create((Get-Content -LiteralPath 'tools/Test-WidgetEmulator.ps1' -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
try {
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.oncue/.debug.IdleReferenceActivity') | Out-Null
    $ui=Read-Ui
    $node=Find-Node $ui 'glass_surface'
    if(-not $node){throw 'Native reference host did not render'}
    $b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Assert-True (($b[2]-$b[0] -eq 1030) -and ($b[3]-$b[1] -eq 225)) 'Production idle RemoteViews rendered at exactly 1030x225'
    Screenshot 'idle-native'
    & ([scriptblock]::Create((Get-Content -LiteralPath 'tools/Compare-WidgetV5.ps1' -Raw))) -Screenshot (Join-Path $OutputDirectory 'idle-native.png') -X $b[0] -Y $b[1] -Width 1030 -Height 225 -Idle -OutputDirectory $OutputDirectory
    @{context='Actual emulator capture of production RemoteViews in debug diagnostic host; not launcher placement';bounds=$node.bounds} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'capture-context.json')
    $result=Get-Content (Join-Path $OutputDirectory 'idle-comparison.json') -Raw | ConvertFrom-Json
    Assert-True ($result.pixelPerfect -eq $true) 'Native-resolution Android idle render is RGB-identical to the v5 reference'
} finally {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_BACK') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
}
