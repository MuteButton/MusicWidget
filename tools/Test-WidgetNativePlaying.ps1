param([string]$OutputDirectory='artifacts/media-widget-v5-native-playing')
. ([scriptblock]::Create((Get-Content -LiteralPath 'tools/Test-WidgetEmulator.ps1' -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
try {
    Invoke-Adb @('shell','am','start','-W','-f','0x10008000','-n','io.github.mutebutton.musicwidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
    Start-Sleep -Seconds 2
    Fixture 'buffering'
    Fixture 'all-actions'
    Fixture 'seek' @('--el','position','84000')
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.debug.IdleReferenceActivity','--ez','playing','true') | Out-Null
    $ui=Read-Ui
    $node=Find-Node $ui 'glass_surface'
    if(-not $node){throw 'Native reference host did not render'}
    $b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Assert-True (($b[2]-$b[0] -eq 1030) -and ($b[3]-$b[1] -eq 225)) 'Production playing RemoteViews rendered at exactly 1030x225'
    Assert-True (((Find-Node $ui 'track_title').text -eq 'Afterglow') -and ((Find-Node $ui 'elapsed_time').text -eq '1:24')) 'Native host uses live fixture metadata and position'
    Screenshot 'playing-native'
    & ([scriptblock]::Create((Get-Content -LiteralPath 'tools/Compare-WidgetV5.ps1' -Raw))) -Screenshot (Join-Path $OutputDirectory 'playing-native.png') -X $b[0] -Y $b[1] -Width 1030 -Height 225 -OutputDirectory $OutputDirectory
    @{context='Actual emulator capture of shared production RemoteViews with real MediaSession in debug diagnostic host; not launcher placement';bounds=$node.bounds} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'capture-context.json')
    # This records fidelity; functional assertions above do not imply pixel identity.
} finally {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_BACK') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
}
