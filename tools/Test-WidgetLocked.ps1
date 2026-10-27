param([string]$OutputDirectory='artifacts/locked-widget-acceptance', [string]$AppPackage='io.github.mutebutton.oncue')
. ([scriptblock]::Create((Get-Content tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -AppPackage $AppPackage -FixturePackage io.github.mutebutton.musicwidget.testplayer -OutputDirectory $OutputDirectory
if((Invoke-Adb @('shell','getprop','ro.kernel.qemu')).Trim() -ne '1'){throw 'Emulator only'}
$disabled=(Invoke-Adb @('shell','locksettings','get-disabled')).Trim()
$stayAwake=(Invoke-Adb @('shell','settings','get','global','stay_on_while_plugged_in')).Trim()
try {
 Invoke-Adb @('shell','settings','put','global','stay_on_while_plugged_in','7') | Out-Null
 Invoke-Adb @('shell','am','start','-W','-n',"$FixturePackage/io.github.mutebutton.musicwidget.debug.DebugMediaActivity",'--es','command','reset') | Out-Null
 Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
 Show-WidgetPage
 $before=(Find-Node (Read-Ui) 'elapsed_time').text
 Invoke-Adb @('shell','locksettings','set-disabled','false') | Out-Null
 Invoke-Adb @('shell','input','keyevent','KEYCODE_SLEEP') | Out-Null
 Start-Sleep -Seconds 2
 Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
 Start-Sleep -Seconds 2
 $policy=Invoke-Adb @('shell','dumpsys','window','policy')
 $policy | Set-Content (Join-Path $OutputDirectory 'locked-policy.txt')
 Assert-True ($policy -match 'showing=true') 'Awake keyguard is actually showing'
 Screenshot 'locked'
 function Cpu {
  $appPid=(Invoke-Adb @('shell','pidof',$AppPackage)).Trim()
  $stat=Invoke-Adb @('shell','cat',"/proc/$appPid/stat")
  $fields=($stat -replace '^.*\) ','') -split '\s+'
  [pscustomobject]@{pid=$appPid;ticks=([long]$fields[11]+[long]$fields[12]);time=[DateTime]::UtcNow}
 }
 $a=Cpu
 Start-Sleep -Seconds 15
 $b=Cpu
 Assert-True ((Invoke-Adb @('shell','dumpsys','power')) -match 'mWakefulness=Awake') 'Screen stayed awake during locked CPU sample'
 $hz=[double](Invoke-Adb @('shell','getconf','CLK_TCK')).Trim()
 $cpu=100*($b.ticks-$a.ticks)/$hz/($b.time-$a.time).TotalSeconds
 @{cpuPercent=$cpu;seconds=($b.time-$a.time).TotalSeconds} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'locked-cpu.json')
 Assert-True ($a.pid -eq $b.pid -and $cpu -ge 0 -and $cpu -lt 1) 'Awake locked app stays below 1 percent of one CPU core'
 Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
 Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
 Show-WidgetPage
 $after=(Find-Node (Read-Ui) 'elapsed_time').text
 Assert-True ($after -ne $before) 'Unlock immediately refreshes current media time'
 Start-Sleep -Seconds 3
 Assert-True ((Find-Node (Read-Ui) 'elapsed_time').text -ne $after) 'Live timer resumes after unlock'
} finally {
 Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
 Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
 Invoke-Adb @('shell','locksettings','set-disabled',$disabled) | Out-Null
 Invoke-Adb @('shell','settings','put','global','stay_on_while_plugged_in',$stayAwake) | Out-Null
}
