param([string]$OutputDirectory='artifacts/nfr-profile',[int]$Cycles=30,[int]$SampleSeconds=20)
. ([scriptblock]::Create((Get-Content tools/Preview-ArtworkColors.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
$OutputDirectory=(Resolve-Path $OutputDirectory).Path
$memory=[Collections.Generic.List[object]]::new()
$cpu=[Collections.Generic.List[object]]::new()
function Process-Sample {
    $appPid=(Invoke-Adb @('shell','pidof','io.github.mutebutton.musicwidget')).Trim()
    if($appPid -notmatch '^\d+$'){throw 'Widget process missing'}
    $stat=Invoke-Adb @('shell','run-as','io.github.mutebutton.musicwidget','cat',"/proc/$appPid/stat")
    $fields=($stat.Substring($stat.LastIndexOf(')')+2)).Split(' ')
    $up=[double]((Invoke-Adb @('shell','cat','/proc/uptime')).Split(' ')[0])
    [pscustomobject]@{pid=$appPid;ticks=([long]$fields[11]+[long]$fields[12]);uptime=$up;threads=[int]$fields[17]}
}
function Cpu-Sample([string]$Name,[double]$Limit) {
    Start-Sleep -Seconds 3
    $a=Process-Sample
    Start-Sleep -Seconds $SampleSeconds
    $b=Process-Sample
    if($a.pid -ne $b.pid){throw 'Widget process restarted during CPU sample'}
    $percent=100*($b.ticks-$a.ticks)/($script:hz*($b.uptime-$a.uptime))
    $cpu.Add([pscustomobject]@{state=$Name;seconds=$b.uptime-$a.uptime;oneCoreCpuPercent=$percent;limit=$Limit;pid=$a.pid})
    $cpu | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'cpu-samples.json')
    Assert-True ($percent -le $Limit) "$Name CPU is at most $Limit percent of one guest core (measured $([Math]::Round($percent,3)))"
}
function Heap-Snapshot([string]$Name) {
    $sample=Process-Sample
    $remote="/data/local/tmp/widget-nfr-$Name.hprof"
    Invoke-Adb @('shell','am','dumpheap','-g','io.github.mutebutton.musicwidget',$remote) | Out-Null
    Invoke-Adb @('pull',$remote,(Join-Path $OutputDirectory "$Name.hprof")) | Out-Null
    Invoke-Adb @('shell','rm',$remote) | Out-Null
    $info=Invoke-Adb @('shell','dumpsys','meminfo','io.github.mutebutton.musicwidget')
    $info | Set-Content (Join-Path $OutputDirectory "$Name-meminfo.txt")
    $native=[regex]::Match($info,'(?m)^\s*Native Heap\s+((?:\d+\s+){7}\d+)')
    $managed=[regex]::Match($info,'(?m)^\s*Dalvik Heap\s+((?:\d+\s+){7}\d+)')
    if(-not $native.Success -or -not $managed.Success){throw 'Unrecognized meminfo heap format'}
    $fds=@((Invoke-Adb @('shell','run-as','io.github.mutebutton.musicwidget','ls',"/proc/$($sample.pid)/fd")) -split '\s+' | Where-Object {$_ -match '^\d+$'}).Count
    $entry=[pscustomobject]@{name=$Name;pid=$sample.pid;nativeAllocatedKB=[int](($native.Groups[1].Value -split '\s+')[6]);javaAllocatedKB=[int](($managed.Groups[1].Value -split '\s+')[6]);pssKB=[int]([regex]::Match($info,'TOTAL PSS:\s+(\d+)').Groups[1].Value);threads=$sample.threads;fileDescriptors=$fds}
    $memory.Add($entry)
    $memory | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'memory-samples.json')
    Write-Output "SNAPSHOT: $Name Java=$($entry.javaAllocatedKB)KB Native=$($entry.nativeAllocatedKB)KB PSS=$($entry.pssKB)KB threads=$($entry.threads) FD=$fds"
}
Invoke-Adb @('shell','run-as','io.github.mutebutton.musicwidget','cp','shared_prefs/media_widget.xml','files/nfr-original-prefs.xml') | Out-Null
try {
    $script:hz=[int](Invoke-Adb @('shell','getconf','CLK_TCK'))
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.debug.DebugMediaActivity','--es','command','reset') | Out-Null
    Set-GlassMode 'Artwork glass'
    # Warm all three artwork palettes and play/pause surfaces before measuring retention.
    foreach($i in 1..6){Fixture 'next';Fixture 'pause';Fixture 'play'}
    Fixture 'pause'
    Heap-Snapshot 'warm-baseline'
    Fixture 'play'
    Cpu-Sample 'playing' 5
    Fixture 'pause'
    Cpu-Sample 'paused' 1
    Fixture 'play'
    Invoke-Adb @('shell','input','keyevent','KEYCODE_SLEEP') | Out-Null
    Cpu-Sample 'screen-off-playing' 1
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    foreach($i in 1..$Cycles) {
        Fixture 'next';Fixture 'pause';Fixture 'no-art';Fixture 'art'
        Fixture 'second-play';Fixture 'second-stop';Fixture 'play';Fixture 'pause'
        if($i % 10 -eq 0){Heap-Snapshot "cycle-$i"}
    }
    $first=$memory[0];$last=$memory[$memory.Count-1]
    Assert-True ($last.pid -eq $first.pid) 'Widget process survives repeated artwork/session churn without restart'
    Assert-True (($last.javaAllocatedKB+$last.nativeAllocatedKB)-($first.javaAllocatedKB+$first.nativeAllocatedKB) -le 8192) 'Post-GC allocated heap grows by at most 8 MiB after warm-up'
    Assert-True ($last.pssKB-$first.pssKB -le 24576) 'Post-GC PSS grows by at most 24 MiB after warm-up'
    Assert-True ($last.threads -le $first.threads+3) 'Repeated session churn does not accumulate threads'
    Assert-True ($last.fileDescriptors -le $first.fileDescriptors+4) 'Repeated session churn does not accumulate file descriptors'
    Fixture 'stop'
    Cpu-Sample 'idle' 1
    Heap-Snapshot 'idle-final'
    Show-WidgetPage
    Assert-Eventually { $null -ne (Find-Node (Read-Ui) 'idle_state') } 'Widget returns to functional idle after stress'
} finally {
    Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
    Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.musicwidget') | Out-Null
    Invoke-Adb @('shell','run-as','io.github.mutebutton.musicwidget','cp','files/nfr-original-prefs.xml','shared_prefs/media_widget.xml') | Out-Null
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.musicwidget/.MainActivity') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
}
