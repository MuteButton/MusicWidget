param([string]$OutputDirectory='artifacts/idle-routing-scenarios')
. ([scriptblock]::Create((Get-Content tools/Test-WidgetEmulator.ps1 -Raw))) -DefinitionsOnly -OutputDirectory $OutputDirectory
function Set-Routing([string]$Preferred,[string]$Last) {
    Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.oncue') | Out-Null
    [xml]$prefs=$original
    foreach($name in @('preferred_player','last_player')) {
        foreach($node in @($prefs.SelectNodes("/map/string[@name='$name']"))){[void]$node.ParentNode.RemoveChild($node)}
    }
    foreach($entry in @(@('preferred_player',$Preferred),@('last_player',$Last))) {
        if($entry[1]) { $node=$prefs.CreateElement('string'); $node.SetAttribute('name',$entry[0]); $node.InnerText=$entry[1]; [void]$prefs.map.AppendChild($node) }
    }
    $file=Join-Path $OutputDirectory 'routing-prefs.xml'
    $prefs.Save($file)
    Invoke-Adb @('push',$file,'/data/local/tmp/widget-routing-prefs.xml') | Out-Null
    Invoke-Adb @('shell','run-as','io.github.mutebutton.oncue','cp','/data/local/tmp/widget-routing-prefs.xml','shared_prefs/oncue_widget.xml') | Out-Null
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.oncue/.MainActivity') | Out-Null
    Invoke-Adb @('shell','input','keyevent','KEYCODE_HOME') | Out-Null
    Show-WidgetPage
    Assert-Eventually { $null -ne (Find-Node (Read-Ui) 'btn_open') } 'Idle widget is visible for routing scenario'
}
Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.oncue') | Out-Null
$original=Invoke-Adb @('shell','run-as','io.github.mutebutton.oncue','cat','shared_prefs/oncue_widget.xml')
Invoke-Adb @('shell','run-as','io.github.mutebutton.oncue','cp','shared_prefs/oncue_widget.xml','files/idle-routing-backup.xml') | Out-Null
try {
    foreach($scenario in @(
        @{preferred='com.google.android.youtube';last='';name='Preferred native app'},
        @{preferred='';last='com.google.android.youtube';name='Remembered native app'},
        @{preferred='com.example.missing.player';last='com.google.android.youtube';name='Missing preferred app falls back to remembered app'}
    )) {
        Invoke-Adb @('shell','am','force-stop','com.google.android.youtube') | Out-Null
        Set-Routing $scenario.preferred $scenario.last
        Tap-Node 'btn_open'
        Wait-Activity 'ResumedActivity.*com.google.android.youtube/' | Out-Null
        Pass "$($scenario.name) opens from idle widget"
    }
    Invoke-Adb @('shell','am','force-stop','com.google.android.youtube') | Out-Null
    Set-Routing '' ''
    Tap-Node 'btn_open'
    Assert-Eventually { $null -ne (Read-Ui).SelectSingleNode("//node[@text='Choose default media app']") } 'No preferred or remembered app opens the player chooser'
    Screenshot 'idle-player-chooser'
} finally {
    Invoke-Adb @('shell','am','force-stop','io.github.mutebutton.oncue') | Out-Null
    Invoke-Adb @('shell','run-as','io.github.mutebutton.oncue','cp','files/idle-routing-backup.xml','shared_prefs/oncue_widget.xml') | Out-Null
    Invoke-Adb @('shell','am','start','-W','-n','io.github.mutebutton.oncue/.MainActivity') | Out-Null
}
Write-Output "Completed $($results.Count) idle routing checks."
