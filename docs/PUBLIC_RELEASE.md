# Public release preparation

Current readiness decision and open usability/device gates: [Current limitations](KNOWN_LIMITATIONS.md).

## Stable identity and phone installation

The replacement release is OnCue 2.0, version code 17, with registered application
ID `io.github.mutebutton.oncue`, minimum API 24 and target API 36. This build uses
a newly generated OnCue certificate. It cannot update an installation signed by
the prior certificate: uninstall the old app, install the new APK, grant notification
access and add the widget again. Settings start fresh. Add the new certificate's
SHA-256 fingerprint to the existing Android Developer Console package registration.
Keep this new signing identity stable for subsequent releases.

The signed universal APK is generated from the signed bundle by bundletool. Download the APK from [GitHub releases](https://github.com/MuteButton/OnCue/releases) for direct phone installation. The AAB is for store submission.
## Build and package

Keep `keystore.properties` and the release keystore local and ignored. From the repository root:

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintRelease :test-player:assembleDebug :app:writeDependencyInventory --max-workers=1
```

From the repository root, with bundletool 1.18.3 at `artifacts/tooling/bundletool-1.18.3.jar`:

```powershell
.\tools\Package-Release.ps1 -OutputDirectory artifacts/oncue-2.0-final -DeviceApks
python tools/audit_dependencies.py app/build/reports/dependency-inventory.json artifacts/oncue-2.0-final/dependency-audit.json
```

Omit `-DeviceApks` if no emulator is connected. Outputs in ignored `artifacts/oncue-2.0-final/` are `OnCue-2.0.apk`, `OnCue-2.0.aab`, APK sets and SHA256SUMS.txt. The packaging helper reads signing secrets locally and removes its temporary password files in a finally block. The production key is not uploaded to CI.

Before an update, verify the certificate matches the previous release, install with `adb install -r`, and verify settings, permissions and widget placement survive. A new signing certificate requires a planned migration; do not casually regenerate the release key.

## Signing recovery

The public release uses the neutral certificate subject `CN=MuteButton, OU=OnCue, O=MuteButton`. Older builds with different application IDs can coexist. Future updates must retain the public signing key.

The owner-authorized setup saved a matching encrypted PKCS12 keystore and its recovery password in the Windows Documents folder, under `OnCue signing backup`. That folder restricts access to the current Windows account and SYSTEM. The recovery password file is plaintext: move a copy of the complete folder to an encrypted off-device vault and verify recovery before publication. Same-PC storage is not an independent backup. Never commit these files. `tools/Initialize-PublicSigning.ps1` is a one-time initializer and refuses to overwrite an existing identity.

## Privacy, security and artwork

- [Privacy policy](../PRIVACY.md) is also available offline in setup. The notification-access explanation is displayed before the permission action.
- The manifest has no Internet permission. Notification destinations and media state are used locally, and only the settings file is allowed in Android backup/device transfer. Backup can include saved browser URLs; the policy explicitly discloses this. An app-only settings restore does not restore launcher host data, so the widget may need to be added again.
- The widget action receiver is not exported. The notification listener is protected by Android's notification-listener binding permission. Control PendingIntents are explicit and immutable. The exported setup activity does not accept arbitrary destination URLs from callers.
- The owner confirmed on October 23, 2026 that the supplied v5 design and README/demo artwork may be redistributed. The code's MIT license alone does not establish image rights. Keep that confirmation with release records.
- The resolved production dependency scan uses OSV and sends Maven coordinates only. No advisory results are a database snapshot, not a guarantee of no vulnerabilities.

Current release: [OnCue 2.0](RELEASE_2.0.md).

## Automated verification

GitHub Actions builds without production signing secrets, runs repository hygiene, JVM tests, lint and a dependency scan. Android instrumentation jobs target API 24, 31 and 36 and check package/API configuration, backup allowlists, privacy access and setup insets. These small CI checks do not replace the full widget integration suite on a prepared launcher.

`Test-ReleaseUpgrade.ps1` additionally tests split/universal replacement installs, permission/settings retention, ordinary reboot recovery, and actual local-transport backup/restore on a dedicated emulator. It restores the previous backup-manager configuration; do not run it on a personal phone.

The release integration scripts test the actual signed APK with the separate acceptance player, Chrome, launcher UI, callbacks, progress, marquee and stress. Keep reports scoped to the tested artifact hash. Do not describe a configured CI job as passed before its run completes.

## Store listing draft

- Name: **OnCue**
- Developer: **MuteButton**
- Short description: **Artwork-inspired glass, playback controls and live music progress.**
- Description: OnCue brings your current track, album artwork and playback controls to your Android home screen. Choose colors inspired by your artwork or a consistent Classic glass finish. Long titles scroll once in the full layout. Tap the track to return to your player, or choose a default app and optional browser URL for when nothing is playing. Notification access is required to discover media sessions. Available controls, progress and browser-tab navigation depend on the media app. Full glass styling requires Android 12 or newer; earlier versions use the native layout.
- Support: https://github.com/MuteButton/OnCue/issues
- Privacy URL after publishing this file: use the public repository URL for PRIVACY.md on the release/default branch.
- Data safety draft: the app does not transmit data to the developer and has no analytics/ads SDK. It accesses media information locally and stores settings; Android backup and explicit launches of other apps are disclosed in the privacy policy. Confirm the current Console questionnaire against this behavior before submission.

## Owner/account steps before public publication

1. Secure an off-device backup of the existing signing keystore and passwords in an owner-controlled encrypted vault. A second file on this PC is not an independent backup. Confirm recovery before publishing.
2. Configure the Play developer account, verification, app signing and the permanent package identity. If direct APKs must update Play-installed copies, use the same app-signing certificate for both distribution channels; the upload key alone is not sufficient.
3. Upload the AAB to an internal test track, review the Play pre-launch report, and complete required testing/account gates, Data safety, content rating, audience and store graphics.
4. Verify the privacy URL is publicly reachable, then approve the actual store listing and production release. No Play Console upload or production publication is performed by these repository scripts.

Google references: [target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878), [user data/privacy](https://support.google.com/googleplay/android-developer/answer/10144311), [bundletool](https://developer.android.com/tools/bundletool), [Android backup](https://developer.android.com/identity/data/autobackup).
