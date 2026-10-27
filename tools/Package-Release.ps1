param(
    [string]$OutputDirectory='artifacts/oncue-registered-release',
    [string]$Bundletool='artifacts/tooling/bundletool-1.18.3.jar',
    [switch]$DeviceApks
)
$ErrorActionPreference='Stop'
$root=(Get-Location).Path
$app=Join-Path $root 'app'
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'C:/Program Files/Android/Android Studio/jbr/bin/java.exe'}
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}elseif($env:ANDROID_SDK_ROOT){$env:ANDROID_SDK_ROOT}else{Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
$adb=Join-Path $sdk 'platform-tools/adb.exe'
$sign=@{}
foreach($line in [IO.File]::ReadAllLines((Join-Path $root 'keystore.properties'))){
    if($line -match '^\s*([^#=]+?)\s*=\s*(.*?)\s*$'){$sign[$Matches[1]]=$Matches[2]}
}
$key=if([IO.Path]::IsPathRooted($sign.storeFile)){$sign.storeFile}else{Join-Path $app $sign.storeFile}
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$out=(Resolve-Path $OutputDirectory).Path
if(-not $out.StartsWith((Join-Path $root 'artifacts')+[IO.Path]::DirectorySeparatorChar)){throw 'Release output must stay under ignored artifacts/'}
$buildText=Get-Content (Join-Path $app 'build.gradle') -Raw
if($buildText -notmatch 'versionName\s+"([0-9A-Za-z][0-9A-Za-z._-]*)"'){throw 'Cannot determine release version name'}
$artifactBase="OnCue-$($Matches[1])"
$bundle=Join-Path $out "$artifactBase.aab"
Copy-Item (Join-Path $app 'build/outputs/bundle/release/app-release.aab') $bundle -Force
$private=Join-Path $out ('.signing-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $private | Out-Null
try {
    [IO.File]::WriteAllText((Join-Path $private 'store'),$sign.storePassword)
    [IO.File]::WriteAllText((Join-Path $private 'key'),$sign.keyPassword)
    # Native JVM warnings on stderr are not failures in Windows PowerShell.
    $ErrorActionPreference='Continue'
    & $java -jar $Bundletool validate "--bundle=$bundle" > (Join-Path $out 'bundle-validation.txt')
    $ErrorActionPreference='Stop'
    if($LASTEXITCODE -ne 0){throw 'Bundle validation failed'}
    $common=@("--bundle=$bundle","--ks=$key","--ks-key-alias=$($sign.keyAlias)","--ks-pass=file:$(Join-Path $private 'store')","--key-pass=file:$(Join-Path $private 'key')",'--overwrite')
    $universal=Join-Path $out 'universal.apks'
    # Native JVM warnings on stderr are not failures in Windows PowerShell.
    $ErrorActionPreference='Continue'
    & $java -jar $Bundletool build-apks @common "--output=$universal" --mode=universal
    $ErrorActionPreference='Stop'
    if($LASTEXITCODE -ne 0){throw 'Universal APK generation failed'}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead($universal)
    try {[IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry('universal.apk'),(Join-Path $out "$artifactBase.apk"),$true)} finally {$zip.Dispose()}
    if($DeviceApks){
        # Native JVM warnings on stderr are not failures in Windows PowerShell.
        $ErrorActionPreference='Continue'
        & $java -jar $Bundletool build-apks @common "--output=$(Join-Path $out 'device.apks')" --connected-device "--adb=$adb"
        $ErrorActionPreference='Stop'
        if($LASTEXITCODE -ne 0){throw 'Device APK generation failed'}
    }
    Get-ChildItem $out -File | Where-Object {$_.Extension -in @('.apk','.aab','.apks')} | Get-FileHash -Algorithm SHA256 |
        ForEach-Object { "$($_.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($_.Path))" } | Set-Content (Join-Path $out 'SHA256SUMS.txt')
} finally {
    $resolved=(Resolve-Path $private).Path
    if(-not $resolved.StartsWith($out+[IO.Path]::DirectorySeparatorChar)){throw 'Unsafe temporary signing path'}
    Remove-Item -LiteralPath $resolved -Recurse -Force
    $sign.Clear()
}
Write-Output 'Packaged signed AAB and installable universal APK from that same bundle. Temporary signing-password files removed.'
