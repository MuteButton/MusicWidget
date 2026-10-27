# OnCue 1.2 rename and upgrade acceptance

Verified October 24, 2026 on the API 36.1 emulator. OnCue replaces the visible Music Widget name in the launcher/widget picker, setup, notification-access label, permission explanation, privacy policy, README and current release documentation. Version code increases from 2 to 3; version name is 1.2. Release filenames are `OnCue-1.2.apk` and `OnCue-1.2.aab`.

The application ID remains `io.github.mutebutton.oncue`. The existing neutral MuteButton signing certificate is retained and was compared with the preceding signed release. The GitHub repository URL, internal source/resource identifiers, existing signing-backup folder and historical test reports retain their established names. This rename does not generate a new key or require uninstalling the neutral-signed 1.1 app.

## Verification

- Signed APK/AAB build, bundle validation and signature verification passed; 11 JVM tests passed.
- Ten rename/upgrade assertions passed: the emulator started on version 1.1/code 2 with a placed widget; version 1.2/code 3 installed in place; notification access, player choice, color setting and the original widget survived; setup, widget confirmation, notification settings and offline policy displayed OnCue.
- The first eight rename assertions are in local `rename-upgrade/` evidence. The helper initially remained on the Android Settings activity when attempting to open privacy. After explicitly returning to setup, the final two assertions passed in `rename-followup/`. This was a test-navigation correction, not an application failure or reinstall.
- APK metadata reports `application-label: OnCue`; certificate digest matches the prior release. The setup screenshot was visually reviewed.
- All 36 signed-release integration assertions passed on the exact OnCue APK: live metadata/progress, controls, action availability, session changes, navigation, both color modes, missing artwork and permission/screen recovery.
- Repository hygiene found no signing-password copies, personal home paths or prohibited generated files in tracked/candidate files.

## Deliverables

Local output and evidence: `artifacts/oncue-release/` (ignored by Git).

| File | SHA-256 |
| --- | --- |
| OnCue-1.2.apk | `966cad695e0502631fb75f8d25a335a187351718660ae5f828c57e46652350a1` |
| OnCue-1.2.aab | `bedaa68afb965f7e3dd74589c4367fae0d569d94f6512826e0812c2c5df23a81` |

The APK includes the previously verified [artwork rim fix](ARTWORK_RIM_ACCEPTANCE.md). Earlier full release/background reports keep their original hashes and are not relabeled as tests of this new binary. Physical-device checks, encrypted off-device signing recovery and any store publication steps remain separate from this rename verification.
