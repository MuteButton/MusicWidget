# Run once from the repository root. Never prints signing secrets.
$ErrorActionPreference='Stop'
$root=(Get-Location).Path
$app=Join-Path $root 'app'
$key=Join-Path $app 'oncue-public-release.keystore'
$properties=Join-Path $root 'keystore.properties'
$documents=[Environment]::GetFolderPath('MyDocuments')
if(-not $documents){throw 'Documents folder is unavailable'}
$backup=Join-Path $documents 'OnCue signing backup'
if((Test-Path $key) -or (Test-Path $backup)){throw 'Signing identity or backup already exists; refusing to overwrite it'}
$keytool=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/keytool.exe'}else{Join-Path $env:ProgramFiles 'Android/Android Studio/jbr/bin/keytool.exe'}
if(-not (Test-Path $keytool)){throw 'JDK keytool is unavailable'}
New-Item -ItemType Directory -Path $backup | Out-Null
$acl=New-Object Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true,$false)
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
$acl.SetOwner($sid)
foreach($identity in @($sid,(New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))){
    $rule=New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $backup -AclObject $acl
$bytes=New-Object byte[] 32
$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$password=[Convert]::ToBase64String($bytes)
try {
    $env:ONCUE_STORE_PASSWORD=$password
    $env:ONCUE_KEY_PASSWORD=$password
    $ErrorActionPreference='Continue'
    & $keytool -genkeypair -keystore $key -storetype PKCS12 -alias oncue-public -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=MuteButton, OU=OnCue, O=MuteButton' -storepass:env ONCUE_STORE_PASSWORD -keypass:env ONCUE_KEY_PASSWORD 2>&1 | Out-Null
    $keyExit=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($keyExit -ne 0){throw 'Neutral signing key generation failed'}
    Copy-Item -LiteralPath $key -Destination (Join-Path $backup 'oncue-public-release.keystore')
    [IO.File]::WriteAllText((Join-Path $backup 'RECOVERY-SECRET.txt'),"Store type: PKCS12`r`nAlias: oncue-public`r`nStore and key password: $password`r`n")
    [IO.File]::WriteAllText((Join-Path $backup 'README.txt'),'Private signing recovery material. The keystore is encrypted; RECOVERY-SECRET.txt contains its password in plaintext. Folder permissions restrict access to your Windows account and SYSTEM. Copy this entire folder to an encrypted off-device vault or encrypted removable storage. This same-PC copy alone cannot recover from loss of this PC. Never commit or share these files.')
    if((Get-FileHash $key).Hash -ne (Get-FileHash (Join-Path $backup 'oncue-public-release.keystore')).Hash){throw 'Backup verification failed'}
    if(Test-Path $properties){Copy-Item -LiteralPath $properties -Destination (Join-Path $backup 'previous-prototype-keystore.properties')}
    [IO.File]::WriteAllText($properties,"storeFile=oncue-public-release.keystore`nstorePassword=$password`nkeyAlias=oncue-public`nkeyPassword=$password`n")
    Write-Output 'Neutral signing identity created; matching keystore and recovery secret saved in the restricted Documents backup folder.'
} finally {
    Remove-Item Env:ONCUE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:ONCUE_KEY_PASSWORD -ErrorAction SilentlyContinue
    $password=$null
    [Array]::Clear($bytes,0,$bytes.Length)
}
