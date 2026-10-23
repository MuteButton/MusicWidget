# Emulator recovery investigation (2026-10-22)

The API 36.1 AVD repeatedly became unresponsive after boot. All QEMU threads were suspended; resuming them temporarily restored ADB, then the process exited. Windows reported crashes in `crashpad_handler.exe`. Both host graphics and software rendering reproduced the problem with emulator 36.3.10. An isolated emulator 37.2.12 reproduced it too.

Disabling crash reporting with the supported `-crash-report-mode disabled` flag exposed the underlying Windows application error:

- Process: `qemu-system-x86_64-headless.exe`
- Module: `C:\Windows\System32\libGLESv2.dll`
- Version: ANGLE 2.1.17734, git hash `1bd1a3db1c60`
- Exception: `0xc0000005`
- Fault offset: `0x38efff`

The isolated emulator archive came from Google's SDK repository, stable channel:

- `https://dl.google.com/android/repository/emulator-windows_x64-16428233.zip`
- Version 37.2.12
- Verified archive SHA-1: `488ed747e82de7e9bb5247becd1ac043c7e5e85d`
- Extracted under `artifacts/emulator-37.2.12`; installed SDK unchanged.

For the isolated copy only, `libEGL.dll`, `libGLESv2.dll`, and `libGLES_CM.dll` from its `lib64/gles_swiftshader` directory were copied beside `qemu/windows-x86_64/qemu-system-x86_64-headless.exe`. Reading the running process modules confirmed all three resolve to these bundled copies instead of System32. No system DLLs were modified.

Launch from the repository root:

```powershell
$env:ANDROID_HOME = 'C:/Users/David/AppData/Local/Android/Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
& './artifacts/emulator-37.2.12/emulator/emulator.exe' `
  -avd S25_Edge_Approx_API_36_1 -no-window -no-audio -no-boot-anim `
  -no-snapshot-load -no-snapshot-save -memory 2048 -cores 2 `
  -gpu swiftshader -crash-report-mode disabled -no-metrics
```

This emulator raises the requested RAM to its 2560 MB minimum for this image. A successful boot or corrected DLL resolution is not itself a passing widget regression. See `FEATURE_ACCEPTANCE.md` and the corresponding run artifacts for actual results.
