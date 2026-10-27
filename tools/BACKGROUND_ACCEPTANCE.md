# Forced-state background regression

Tested October 23, 2026 on the API 36.1 emulator using the unchanged final signed Music Widget 1.1 APK. SHA-256: `2a2451fac063fc86e0202b7502619c582147f81cb42b57079830d6ca3502dad4`.

**55 assertions passed in 151.4 seconds**, including setup assertions and repeated checks. This is additional evidence alongside the [88-check final release regression](NEUTRAL_RELEASE_ACCEPTANCE.md), not 55 distinct new features.

Run from the repository root on the prepared, dedicated emulator:

```powershell
./tools/Test-ReleaseBackground.ps1
```

Requires the pinned widget, notification access, separate test player and UI probe used by the existing emulator suite. The script refuses physical devices and an already simulated battery/idle state. It restores battery simulation, forced idle, standby configuration and crash-dialog settings in `finally`. It intentionally crashes and force-stops only the widget app, then stops the test player and leaves the launcher awake.

## Results

| Check | Result |
| --- | --- |
| Three forced deep-Doze cycles | Android confirmed IDLE each time; title, playing state, timestamps, pause/resume and single pause dispatch recovered after every wake |
| App standby request | Android retained ACTIVE (bucket 10, Idle=false) while the widget was bound; recovery after the request passed, but restrictive standby was **not exercised** |
| Injected process crash | Android restarted the notification listener without opening setup; current media and controls recovered |
| Force-stop followed by explicit reopen | Original process terminated; reopening restored metadata, advancing/frozen timestamps and controls |
| Screen-off Doze and awake/no-media CPU | Each sampled approximately 15 seconds; both recorded 0 additional process CPU ticks, below the 5%-of-one-core regression limit |
| Wake locks and scheduled work | No app-owned wake lock observed in either snapshot; reviewed alarm/job dumps contained no scheduled widget alarms/jobs |
| Memory | PSS 18,375 KiB initially, 26,304 KiB after third Doze cycle, 33,530 KiB finally; final growth 15,155 KiB, below 32 MiB |
| Exit history | The final run contained its one injected crash and one intentional force-stop; no additional crash/ANR was recorded during that run |
| Artifact and cleanup | Installed APK still matched the final hash; deep idle ACTIVE, standby Idle=false/bucket 10, charging simulation reset and original crash-dialog setting restored |

The Doze snapshot did include an AudioMix wake lock attributed to the separate playing-media fixture and audio service, not the widget. This is not a claim that the entire device held no wake locks.

Memory values span deliberately replaced processes; this lifecycle check is not a same-process leak test. The earlier 60-cycle stress run supplies the same-process evidence. Zero observed CPU ticks over 15 seconds means no measurable CPU work at the available counter resolution during that sample, not zero battery use.

## Platform behavior and test corrections

Android's [Doze testing guidance](https://developer.android.com/training/monitoring-device-state/doze-standby) supplies the forced idle/standby procedure. The [AOSP standby controller](https://android.googlesource.com/platform/frameworks/base/+/0b774f93c32d401094bff02641b226ae237bf74c/apex/jobscheduler/service/java/com/android/server/usage/AppStandbyController.java) keeps apps with bound widgets at least ACTIVE. The emulator remained active even when explicitly simulated as unplugged. This test does not bypass that platform rule or claim Samsung-specific restricted-app behavior.

Two earlier attempts exposed incorrect test assumptions: that a placed widget could be forced into restrictive standby, and that the package stopped flag must remain true after force-stop. The latter was already false by inspection on this system-bound listener; the corrected check verifies that the old process ends, then tests explicit reopen. Neither required an app-code change. A VM crash is an intentional process-loss test, not a claim to reproduce every low-memory-killer condition.

Final local evidence: `artifacts/public-release-neutral/background-verified/`. Earlier attempts remain in `background/` and `background-final/`. Evidence includes results, resource dumps, CPU/memory measurements, standby eligibility, process exits, cleanup verification and final screenshot. The final application APK was not rebuilt or changed.

## Limits

This short suite covers forced transitions and bounded resource observations. It does not measure overnight battery endurance, slow leaks over hours, every player, or Samsung/One UI restrictions. Physical-device testing and the independently recoverable signing backup remain release prerequisites.
