# OnCue 1.2 release readiness review

Reviewed October 24, 2026. **Recommendation: continue controlled testing; hold unrestricted public release.** Playback is well covered on the prepared emulator. Accessibility defects, physical-device coverage, signing recovery, and publication gates remain open.

## Findings by priority

### UI checkpoint update — October 26, 2026

The findings below describe the October 24 candidate, not the current preview8
layout. The current glass layout uses precise launcher dimensions, larger circular
controls, live idle captions, and an inset remaining-time label. At a 374dp
canvas width, Previous/Next are about 39.8dp square and Play is 47.1dp square
before launcher scaling. This improves the layout but **does not close the 48dp
touch-target finding**. Text still uses density-based sizing; system font scaling
and TalkBack verification remain open.

Preview7 passed 41 signed-APK core emulator checks and 13 marquee checks, plus
17 JVM tests and release lint. Preview8 adds only timestamp-row right padding;
its signed build and actual Samsung rendering/hash were verified. The complete
emulator suite was not rerun for that padding-only revision. Physical Samsung
canvas, clipping, artwork, control separation, and timestamp clearance were
inspected. This is not a new unrestricted public-release sign-off.

Private captures, test-player data, APKs, and signing material remain outside the
commit. Battery architecture is unchanged at this UI checkpoint: progress still
uses app-side one-second updates while playing with the screen unlocked.

### Original review findings

1. **High: full glass touch targets are too small.** `V5Artwork.kt:173-175` scales the live controls with the 1030-unit artwork. At 374dp widget width, Previous is 37.0 x 37.8dp, Play is 43.9 x 41.4dp, and Next is 34.1 x 37.4dp. Android recommends at least 48 x 48dp. Expanding these adjacent rectangles without repositioning them would overlap targets. Adjust the layout or provide an accessible alternative, and verify actual launcher hit bounds and TalkBack navigation. Compact playback buttons already use 48dp, but that layout cannot be independently selected at full width.
2. **High: full glass text ignores system font scaling.** `V5Artwork.kt:180-186` and `WidgetRenderer.kt`'s `bindTitle` set pixel text sizes using display density only. At 374dp width, title/artist/timestamps are approximately 11.6/8.9/7.1dp. Connected idle captions are rasterized, with transparent live text. A large-font test checking only overlap does not establish readable enlarged text. Add responsive text/layout behavior or an accessible layout choice, then verify 100%, 150%, and 200% font scale, contrast, and screen-reader order. Compact/legacy timestamp lint warnings also remain relevant.
3. **High release gate: supported-device coverage is incomplete.** The current signed-APK integration suite uses API 36 and a separate deterministic media player. Historical API 24/31/36 CI jobs cover three setup/configuration smoke tests, not full playback integration. Physical Samsung/One UI battery restrictions, overnight behavior, and broad real-player interoperability remain unverified. Test the final candidate on representative physical hardware and the minimum supported API, recording APK hash, launcher, OS, media app, screen-off/reboot recovery, session changes, and resize results.
4. **Medium, fixed: CI omitted optimized release compilation and lint.** CI previously checked debug builds only. The workflow now assembles release APK/AAB and runs release lint, exposing R8, resource shrinking, and release packaging failures. Gradle attaches release signing only when local signing properties exist. An isolated source copy with no signing files successfully built and linted unsigned release artifacts. Unsigned CI outputs are not distributable releases. The remote workflow has not been run as part of this review.
5. **Publication gates remain unevidenced.** The release guide still requires verified encrypted off-device signing-key recovery, Play account/app-signing setup, pre-launch review, store questionnaires, and a publicly reachable privacy URL. Direct APK distribution also needs recoverable key custody and a tested update path; Play-specific steps apply when distributing through Play. Local test success does not complete these steps.

The reviewed emulator screenshot has coherent artwork, metadata, and playback grouping. Small text and controls are the principal usability concern. Application runtime/design was not changed in this review; these accessibility findings remain open and the existing signed candidate was preserved.

## Requirements assessment

| Area | Assessment |
| --- | --- |
| Metadata/artwork | Current signed-APK checks pass, including missing metadata/artwork |
| Play/pause/skip | Pass; unsupported actions disabled, commands isolated to selected session |
| Progress | Pass for advancing/paused playback, seeks, track end, unknown duration; display-only by design |
| Multiple sessions | Deterministic switching and disappearance cases pass |
| Navigation | Current title/artist/artwork taps pass; real Chrome/browser URL evidence is historical, not rerun here |
| Permission/lifecycle | Revocation/restoration, screen wake, and explicit process reopen pass; reboot/backup/Doze evidence remains scoped to earlier APKs |
| Compatibility | Partial; older-API playback and physical launchers remain open |
| Privacy/security | No Internet permission; local processing; private action receiver; immutable explicit control intents; platform-protected listener; settings-only backup. Source review is not penetration-test certification. |
| Responsiveness | Ten taps: median 128.3ms, maximum 247.5ms to observed callback, including ADB/polling overhead |
| Memory/stability | 30 stress cycles passed; no process restart; PSS growth 9,163 KiB, below the 32 MiB regression limit; no forced GC |
| Battery | Progress ticking is gated on advancing playback, screen-on, known duration, and widget presence. Historical emulator CPU results are positive; physical battery/overnight behavior remains open. |
| Accessibility | High-priority findings above remain open |
| Maintainability | Release CI gap fixed; deprecated Gradle options and other lint warnings remain visible backlog |
| Recovery/distribution | Stable identity and signed artifacts exist; key recovery and store acceptance remain gates |

## Fresh verification

- Release APK/AAB build succeeded; all 11 JVM tests passed.
- Release lint: 0 errors, 71 warnings. See [lint triage](LINT_TRIAGE.md).
- All 36 signed-release integration assertions passed again on the byte-identical candidate.
- Responsiveness and 30-cycle stress checks passed as measured above. These are bounded emulator observations, not physical-device guarantees or proof of no memory leaks.
- OSV checked 63 resolved production dependencies and returned no advisories at scan time.
- Repository hygiene passed; scope is current tracked/candidate files, not Git history.
- A clean, unsigned source-copy build completed all 57 release build/lint tasks successfully without signing configuration or keystore access.

Candidate APK SHA-256: `966cad695e0502631fb75f8d25a335a187351718660ae5f828c57e46652350a1`.

Candidate AAB SHA-256: `bedaa68afb965f7e3dd74589c4367fae0d569d94f6512826e0812c2c5df23a81`.

Fresh private evidence is under ignored `artifacts/release-review-2026-10-24/`, separate from prior reports. No production publication, signing identity change, or upload of signing secrets occurred.

Related records: [current release acceptance](../tools/ONCUE_RELEASE_ACCEPTANCE.md), [historical NFR measurements](../tools/NFR_VERIFICATION.md), [publication steps](PUBLIC_RELEASE.md).

Android references: [accessible Views and 48dp touch targets](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views), [Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878). The project targets API 36; API targeting alone does not establish store approval.
