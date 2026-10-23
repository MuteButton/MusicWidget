param([string]$OutputDirectory='artifacts/final-release/stress', [int]$Cycles=30)
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -FixturePackage 'com.example.mediawidget.testplayer'
$samples=[Collections.Generic.List[object]]::new()
function Sample([string]$Name) {
    $info=Invoke-Adb @('shell','dumpsys','meminfo','com.example.mediawidget')
    $info | Set-Content (Join-Path $OutputDirectory "$Name-meminfo.txt")
    $pss=[regex]::Match($info,'TOTAL PSS:\s+(\d+)')
    if(-not $pss.Success){throw 'Missing memory measurement'}
    $samples.Add([pscustomobject]@{name=$Name;pssKB=[int]$pss.Groups[1].Value;pid=(Invoke-Adb @('shell','pidof','com.example.mediawidget')).Trim()})
    $samples | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'memory-samples.json')
}
try {
    Invoke-Adb @('shell','am','start','-W','-n',"$FixturePackage/com.example.mediawidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    foreach($i in 1..6){Fixture 'next'; Fixture 'pause'; Fixture 'play'}
    Start-Sleep -Seconds 3
    Sample 'warm'
    foreach($i in 1..$Cycles){
        Fixture 'next'; Fixture 'pause'; Fixture 'play'
        if($i % 10 -eq 0){Sample "cycle-$i"; Write-Output "Release stress: $i cycles"}
    }
    Fixture 'pause'
    Start-Sleep -Seconds 3
    Sample 'end'
    Assert-True (@($samples.pid | Select-Object -Unique).Count -eq 1) 'Release process survives stress without restart'
    $growth=$samples[-1].pssKB-$samples[0].pssKB
    Assert-True ($growth -lt 32768) "Release stress PSS growth stays below 32 MiB (observed $growth KiB; no forced GC)"
    Assert-Eventually { (Find-Node (Read-Ui) 'btn_play').'content-desc' -eq 'Play' } 'Release remains responsive after stress'
    Invoke-Adb @('shell','dumpsys','cpuinfo') | Set-Content (Join-Path $OutputDirectory 'cpuinfo.txt')
} finally { Fixture 'stop' }
