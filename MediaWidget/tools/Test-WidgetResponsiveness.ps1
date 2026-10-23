param([string]$OutputDirectory='artifacts/nfr-interaction', [string]$FixturePackage='com.example.mediawidget')
. ([scriptblock]::Create((Get-Content MediaWidget/tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -FixturePackage $FixturePackage
$latencies=[Collections.Generic.List[double]]::new()
try {
    Invoke-Adb @('shell','am','start','-W','-n',"$FixturePackage/com.example.mediawidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Assert-Eventually { $null -ne (Find-Node (Read-Ui) 'btn_next') } 'Playback control available for responsiveness sample'
    $node=Find-Node (Read-Ui) 'btn_next'
    $b=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    $x=[string][int](($b[0]+$b[2])/2);$y=[string][int](($b[1]+$b[3])/2)
    foreach($i in 1..10) {
        $before=(State).nextCount
        $timer=[Diagnostics.Stopwatch]::StartNew()
        Invoke-Adb @('shell','input','tap',$x,$y) | Out-Null
        do {
            if((State).nextCount -gt $before){break}
            Start-Sleep -Milliseconds 50
        } while($timer.Elapsed.TotalSeconds -lt 5)
        $timer.Stop()
        if((State).nextCount -le $before){throw 'Playback callback not received within five seconds'}
        $latencies.Add($timer.Elapsed.TotalMilliseconds)
        Start-Sleep -Milliseconds 300
    }
    $ordered=@($latencies | Sort-Object)
    [pscustomobject]@{metric='Host wall time from ADB tap dispatch to observed player callback; includes ADB/polling overhead, excludes initial UI lookup';samplesMs=@($latencies);medianMs=($ordered[4]+$ordered[5])/2;maxMs=$ordered[-1];limitMs=2000} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'callback-latency.json')
    Assert-True ($ordered[-1] -le 2000) 'All ten widget taps reach the media callback within two seconds including ADB overhead'
    Screenshot 'after-interactions'
    Invoke-Adb @('shell','dumpsys','activity','exit-info','com.example.mediawidget') | Set-Content (Join-Path $OutputDirectory 'process-exit-history.txt')
} finally {
    Fixture 'stop'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
}
