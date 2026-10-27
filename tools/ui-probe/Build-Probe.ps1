param([string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }), [string]$Jdk = 'C:/Program Files/Android/Android Studio/jbr')
$ErrorActionPreference = 'Stop'
$probeRoot = Join-Path (Get-Location) 'tools/ui-probe'
$output = Join-Path (Get-Location) 'artifacts/ui-probe'
New-Item -ItemType Directory -Force -Path "$output/classes", "$output/dex" | Out-Null
$androidJar = "$Sdk/platforms/android-34/android.jar"
$buildTools = "$Sdk/build-tools/34.0.0"
$env:JAVA_HOME = $Jdk
& "$Jdk/bin/javac.exe" -source 8 -target 8 -bootclasspath $androidJar -d "$output/classes" "$probeRoot/UiProbe.java"
if ($LASTEXITCODE -ne 0) { throw 'Probe Java compilation failed' }
& "$buildTools/d8.bat" --lib $androidJar --output "$output/dex" "$output/classes/io/github/mutebutton/oncue/uiprobe/UiProbe.class"
if ($LASTEXITCODE -ne 0) { throw 'Probe dex compilation failed' }
& "$buildTools/aapt.exe" package -f -M "$probeRoot/AndroidManifest.xml" -I $androidJar -F "$output/probe-unsigned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
Push-Location "$output/dex"
try { & "$buildTools/aapt.exe" add "$output/probe-unsigned.apk" classes.dex } finally { Pop-Location }
& "$buildTools/zipalign.exe" -f 4 "$output/probe-unsigned.apk" "$output/probe-aligned.apk"
& "$buildTools/apksigner.bat" sign --ks "$env:USERPROFILE/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$output/ui-probe.apk" "$output/probe-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Probe signing failed' }
& "$Sdk/platform-tools/adb.exe" install -r "$output/ui-probe.apk"
