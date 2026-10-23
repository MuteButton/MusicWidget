# Neutral signing release acceptance

Music Widget 1.1 (version code 2), `io.github.mutebutton.musicwidget`, October 23, 2026. Production code is unchanged from the public-identity build; this build replaces its prototype signing certificate with the owner-approved neutral MuteButton identity.

## Deliverables

Files are local and ignored under `artifacts/public-release-neutral/`.

| File | SHA-256 |
| --- | --- |
| MusicWidget-1.1.apk | `2a2451fac063fc86e0202b7502619c582147f81cb42b57079830d6ca3502dad4` |
| MusicWidget-1.1.aab | `d01eba2dadb6fd6fd0db02e4c194c9b9aab85f22488f394e96e6ad788f33e51f` |

The universal APK was generated from this signed AAB with bundletool 1.18.3. Bundle validation and APK signature verification passed. The certificate subject is `CN=MuteButton, OU=Music Widget, O=MuteButton`; a check confirmed it contains no local username. An earlier test build with the same package ID but the previous certificate must be uninstalled once. Retain this new signing key for all future public updates.

A ZIP-entry comparison with the initial release found identical payloads outside signing metadata (634 entries in each APK). No application code or resources changed during signing rotation.

## Recovery and repository privacy

The owner requested a Documents backup. The encrypted PKCS12 keystore and a plaintext recovery-password file are in `Music Widget signing backup` under the owner's Windows Documents folder. Inherited folder access is disabled; access is restricted to the owner account and SYSTEM. The copied keystore hash matches the build key, and the saved recovery password successfully unlocked the backup. No password is included in this report or the repository.

This is a verified same-PC copy, **not an off-device backup**. An encrypted, independently recoverable copy remains a publication prerequisite. Signing files and local evidence remain ignored by Git. Repository hygiene checked 130 tracked/candidate files with no prohibited generated files, recognized credentials, signing-password copies or personal home paths. APK textual metadata was also checked for local username/home-path leakage; none was found. This scan does not claim to rewrite Git history or author metadata.

## Final artifact tests

All **88 assertions passed** on the API 36.1 S25 approximation emulator using the actual signed APK and an external media-session fixture or real Chrome.

| Group | Passed | Evidence subdirectory |
| --- | ---: | --- |
| Exact installed artifact, metadata, progress, controls, session selection, navigation, colors, recovery | 36 | acceptance |
| Automatic marquee, restoration, navigation and track changes | 11 | marquee |
| Actual Chrome callbacks and playing-tab restoration | 11 | chrome |
| Browser-only URL settings, validation, persistence and idle routing | 12 | browser |
| Ten-tap callback latency | 2 | responsiveness |
| 60-cycle playback/artwork memory stress | 3 | stress |
| Split/universal replacement, settings/permissions, reboot and Android backup/restore | 11 | upgrade-backup |
| Small-screen privacy access and dialog | 2 | small-screen |

The final installed universal APK hash was checked again after all tests and matched the deliverable above. The small-screen privacy screenshot was visually reviewed at 720x1280 / density 320; normal emulator dimensions were restored afterward.

Callback latency including ADB/polling overhead was **153.7 ms median**, **274.9 ms maximum**, against a 2,000 ms limit. Stress retained the same process ID: PSS started at **29,475 KiB**, peaked at **44,250 KiB**, and ended at **40,716 KiB** (+11,241 KiB, below the 32 MiB growth limit). No forced GC was used.

Replacement tests used the same version code and new certificate; they do not claim an update across the deliberately incompatible old certificate or every future settings-schema migration. Android's local backup transport restored the previous settings after they were changed. Normal reboot independently retained the widget; app-only restore required re-adding the launcher widget. The prior backup-manager configuration was restored (disabled), and the app was left in Artwork glass mode.

## Additional background validation

The [forced-state background suite](BACKGROUND_ACCEPTANCE.md) subsequently passed 55 assertions in 151.4 seconds against this same APK. It covers repeated Doze/wake, injected process-loss recovery, explicit reopen after force-stop and bounded idle resource checks. Android kept the bound widget active during the standby request, so restrictive standby is explicitly not claimed.

## Unchanged source checks

The [initial public-identity report](PUBLIC_RELEASE_ACCEPTANCE.md) records 11 JVM tests, release lint with 0 errors / 71 triaged warnings, the resolved-dependency vulnerability scan, and the build/API 24/31/36 CI smoke tests. These checks apply to the unchanged application source; CI does not receive the production signing key. Full launcher/media behavior is tested locally on API 36.1, not across every CI API.

Physical Samsung/One UI, every third-party media app, overnight behavior and Play pre-launch review remain outside this report. Bounded memory stress cannot establish the absence of every possible leak. No store publication has been performed.
