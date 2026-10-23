# Public-identity release acceptance (initial signing)

Music Widget 1.1, version code 2, `io.github.mutebutton.musicwidget`. Tested October 23, 2026. Minimum API 24; target/compile API 36. The shipping source was committed in `89f3170`; later commits refine test infrastructure and documentation without changing this APK.

> **Historical signing evidence:** These results describe the initial certificate. The final public APK uses a new neutral MuteButton key; see [neutral signing acceptance](NEUTRAL_RELEASE_ACCEPTANCE.md).

## Exact deliverables

| File under local artifacts/public-release/ | SHA-256 |
| --- | --- |
| MusicWidget-1.1.apk | `b7d6e25192c0b513d775f7bfd362ea4a9c7ff3acae8a2b6441e7a58f588f5255` |
| MusicWidget-1.1.aab | `45bebe6c3c4310f9072d65e79e822c824d25208e86070110db275ecbe87765ee` |

The installable universal APK was generated from this signed AAB with bundletool 1.18.3. Bundle validation and APK signature verification passed. Its signing certificate matches the previous signed prototype; the permanent package ID is intentionally different, so the prototype is not updated in place. No debug components, debuggable flag, Internet permission or native `.so` libraries were found in the shipping APK. Java/Kotlin-only packaging has no native ELF/page-alignment migration requirement; a 16 KB runtime test is not claimed.

## Signed APK integration results

The local S25 Edge approximation emulator runs API 36.1. Tests use a separately installed media-session fixture and actual Chrome, not a debuggable replacement for the shipping APK.

| Group | Passed assertions | Local evidence subdirectory |
| --- | ---: | --- |
| Artifact identity, media/progress/transport, session selection, colors and recovery | 36 | acceptance |
| Marquee motion, one pass, restoration and navigation | 11 | marquee |
| Real Chrome controls and playing-tab restoration | 11 | chrome |
| Browser-only URL setting, validation and idle routing | 12 | browser |
| Callback response time across ten taps | 2 | responsiveness |
| 60-cycle playback/artwork stress | 3 | stress |
| Split/universal replacement installs, ordinary reboot and actual backup/restore | 11 | upgrade-backup-final |

The first 75 checks passed against the byte-identical universal APK. Subsequent update tests installed bundle-generated split APKs and replaced them in place with the same signed universal APK, preserving settings, notification access and widget placement. These are same-version replacement-install checks, not a claim that every future schema migration is tested.

Callback observation including ADB/polling overhead: median **133.9 ms**, maximum **310.2 ms**, threshold 2,000 ms. During 60 stress cycles, the process did not restart; sampled PSS was **29,981 KiB** after warmup, peaked at **41,965 KiB**, and ended at **40,003 KiB** (+10,022 KiB, below the 32 MiB growth threshold). No forced GC was used. This is bounded stress evidence, not proof that every memory leak is absent.

Backup testing used Android's local disk transport only. A first attempt lacked package-manager metadata and Android rejected restore with -1000. Initializing the transport metadata resolved it; the final restore returned 0 and restored the earlier color setting. App-only restore does not restore launcher host state: the test re-adds the widget afterward. Ordinary reboot was tested independently and preserved the existing widget. Failed/partial attempts remain in local evidence.

## Build, privacy and CI

- Release APK/AAB build passed; 11 JVM tests passed.
- Three Android instrumentation tests passed locally: permanent identity/API/permission configuration, backup allowlists, and privacy/setup behavior.
- Release lint: **0 errors, 71 warnings**. See [triage](../docs/LINT_TRIAGE.md).
- OSV query: **63 resolved production libraries, no published advisories returned** at scan time.
- Repository hygiene checks exclude signing material, personal paths and private evidence. Production signing secrets are not uploaded to CI.
- [GitHub run 39988085302](https://github.com/MuteButton/MusicWidget/actions/runs/39988085302) completed successfully: build and API 24/31/36 compatibility jobs passed. API 24 required a retry after an emulator download failure before tests started. These are three smoke tests per API, not the full media integration suite.
- Two additional small-screen checks passed on the signed APK at 720x1280, density 320: privacy access after a swipe and correct policy dialog. The emulator was returned to its normal dimensions.

The full older debug regression remains historical in [FEATURE_ACCEPTANCE.md](FEATURE_ACCEPTANCE.md). It is not relabeled as testing this new namespace/target binary. Physical Samsung/One UI, every third-party player, overnight operation, full accessibility certification and Play's own pre-launch review remain outside this local report.

## Publication boundary

The [release guide](../docs/PUBLIC_RELEASE.md) contains the store listing draft, privacy behavior and packaging commands. The owner confirmed redistribution rights to the supplied visual assets. Store account verification, an independently recoverable off-device signing-key backup, Play App Signing configuration, Console questionnaires/testing requirements and actual publication still require the owner's account/storage setup. No production store release has been performed.
