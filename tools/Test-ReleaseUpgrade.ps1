param(
    [string]$Apk='artifacts/oncue-release/OnCue-1.2.apk',
    [string]$DeviceApks='artifacts/oncue-release/device.apks',
    [string]$Bundletool='artifacts/tooling/bundletool-1.18.3.jar',
    [string]$OutputDirectory='artifacts/oncue-release/upgrade-backup'
)
. ([scriptblock]::Create((Get-Content tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory -FixturePackage 'io.github.mutebutton.oncue.testplayer'
$package='io.github.mutebutton.oncue'
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'}
function Setup {
    Invoke-Adb @('shell','am','start','-W','-a','android.intent.action.MAIN','-f','0x10008000','-n',"$package/.MainActivity") | Out-Null
}
function Color([string]$Name) {
    Setup
    Tap-Node 'color_mode_button'
    $n=(Read-Ui).SelectSingleNode("//node[@text='$Name']")
    if(-not $n){throw "Missing color: $Name"}
    $b=[regex]::Matches($n.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    Invoke-Adb @('shell','input','tap',"$([int](($b[0]+$b[2])/2))","$([int](($b[1]+$b[3])/2))") | Out-Null
}
function ExpectClassic([string]$Reason) {
    Setup
    Assert-Eventually { (Find-Node (Read-Ui) 'color_mode_button').text -eq 'Colors: Classic glass' } $Reason
}
Invoke-Adb @('shell','am','force-stop',$FixturePackage) | Out-Null
Color 'Classic glass'
& $java -jar $Bundletool install-apks "--apks=$DeviceApks" "--adb=$Adb"
if($LASTEXITCODE -ne 0){throw 'Split APK upgrade failed'}
Assert-True (@((Invoke-Adb @('shell','pm','path',$package)) -split "`n").Count -gt 1) 'Bundle-generated split APKs installed successfully'
ExpectClassic 'Split APK install preserves settings'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Assert-True ($null -ne (Find-Node (Read-Ui) 'widget_container')) 'Split APK install preserves launcher widget'
Invoke-Adb @('install','-r',$Apk) | Out-Null
ExpectClassic 'Direct universal APK update preserves settings'
Assert-True ((Invoke-Adb @('shell','settings','get','secure','enabled_notification_listeners')) -like "*$package/*") 'APK updates preserve notification permission'
Invoke-Adb @('reboot') | Out-Null
$deadline=[DateTime]::UtcNow.AddSeconds(180)
do {
    Start-Sleep -Seconds 3
    $ErrorActionPreference='Continue'
    $boot=(& $Adb shell getprop sys.boot_completed 2>$null) -join ''
    $ErrorActionPreference='Stop'
    if($boot.Trim() -eq '1'){break}
} while([DateTime]::UtcNow -lt $deadline)
if($boot.Trim() -ne '1'){throw 'Emulator did not reboot within 180 seconds'}
Invoke-Adb @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
Invoke-Adb @('shell','wm','dismiss-keyguard') | Out-Null
ExpectClassic 'Settings survive device reboot'
Assert-Eventually { (Find-Node (Read-Ui) 'grant_button').text -eq 'Manage notification access' } 'Notification permission survives reboot'
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Assert-True ($null -ne (Find-Node (Read-Ui) 'idle_state')) 'Launcher widget survives reboot and returns to idle'
$transportList=Invoke-Adb @('shell','bmgr','list','transports')
$originalTransport=[regex]::Match($transportList,'(?m)^\s*\*\s+(\S+)').Groups[1].Value
$wasEnabled=(Invoke-Adb @('shell','bmgr','enabled')) -match 'currently enabled'
$local='com.android.localtransport/.LocalTransport'
if(-not $originalTransport -or $transportList -notmatch [regex]::Escape($local)){throw 'Local backup transport unavailable'}
try {
    Invoke-Adb @('shell','bmgr','enable','true') | Out-Null
    Invoke-Adb @('shell','bmgr','transport',$local) | Out-Null
    # A freshly enabled local transport also needs Android's package metadata.
    $metadata=Invoke-Adb @('shell','bmgr','backupnow','--non-incremental','@pm@')
    $metadata | Set-Content (Join-Path $OutputDirectory 'package-metadata-backup.txt')
    if($metadata -notmatch 'Backup finished with result: Success'){throw 'Package metadata backup failed'}
    $backup=Invoke-Adb @('shell','bmgr','backupnow','--non-incremental',$package)
    $backup | Set-Content (Join-Path $OutputDirectory 'backup.txt')
    Assert-True ($backup -match 'Backup finished with result: Success') 'Android local transport backs up release settings'
    Color 'Artwork glass'
    Assert-Eventually { (Find-Node (Read-Ui) 'color_mode_button').text -eq 'Colors: Artwork glass' } 'Settings changed before restore'
    Invoke-Adb @('shell','am','force-stop',$package) | Out-Null
    $sets=Invoke-Adb @('shell','bmgr','list','sets')
    $token=[regex]::Match($sets,'(?m)^\s*([a-fA-F0-9]+)\s*:').Groups[1].Value
    if(-not $token){throw 'No local restore set'}
    $restore=Invoke-Adb @('shell','bmgr','restore',$token,$package)
    $restore | Set-Content (Join-Path $OutputDirectory 'restore.txt')
    if($restore -notmatch 'restoreFinished: 0'){throw 'Android restore transport failed; see restore.txt'}
    ExpectClassic 'Actual Android backup restore recovers prior settings'
} finally {
    Invoke-Adb @('shell','bmgr','wipe',$local,$package) | Out-Null
    Invoke-Adb @('shell','bmgr','transport',$originalTransport) | Out-Null
    Invoke-Adb @('shell','bmgr','enable',$(if($wasEnabled){'true'}else{'false'})) | Out-Null
}
Color 'Artwork glass'
# App-only restore does not restore the launcher's host data. Re-add its widget.
Setup
Tap-Node 'add_widget_button'
$pin=(Read-Ui).SelectSingleNode("//node[@text='Add to home screen' and @class='android.widget.Button']")
if(-not $pin){throw 'Missing launcher pin confirmation'}
$b=[regex]::Matches($pin.bounds,'\d+') | ForEach-Object {[int]$_.Value}
Invoke-Adb @('shell','input','tap',"$([int](($b[0]+$b[2])/2))","$([int](($b[1]+$b[3])/2))") | Out-Null
Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
Show-WidgetPage
Screenshot 'restored-widget'
