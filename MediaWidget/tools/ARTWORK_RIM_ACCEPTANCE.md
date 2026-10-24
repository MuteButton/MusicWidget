# Artwork reaches the rim

Verified October 24, 2026 on the API 36.1 emulator. The artwork mask previously used a 102px corner radius inside a raster rim with tighter corners, exposing crescent-shaped areas of glass above and below the cover. The live artwork now uses an 80px radius with the existing thin rim inset. Image cropping, right-side fade, palette selection, controls and layout are unchanged. The same correction applies to the missing-cover placeholder.

## Visual evidence

`Capture-ArtworkShowcase.ps1` captures the actual signed app using the separate test player, fixed playback position and Artwork glass mode. It crops the widget from each screenshot and stacks the three unretouched crops. No artwork or rim is painted into the screenshot. The resulting image replaces `docs/images/artwork-glass-showcase.png`, already referenced by the README.

The blue, green and pink captures were visually reviewed before and after. Artwork now fills both left corners up to the thin rim; the inset crescent is gone. At 979x214 per card, approximately 2,180 pixels change in the left corner regions. Outside those regions the blue card is identical; the green/pink cards have respectively 15/12 changed pixels with at most one RGB level of difference from emulator rendering.

Evidence remains local under `artifacts/rim-fix/`: `before/`, `after/`, `pixel-comparison.json`, `acceptance/`, and `release/`. Emulator startup initially failed and then encountered a System UI ANR; those attempts were rejected by the capture checks. Accepted captures were taken after the emulator recovered. No application-code change was made for those infrastructure issues.

## Build and regression

- Signed APK and AAB built successfully; bundle validation and APK signature verification passed.
- 11 JVM tests passed.
- All 36 signed-release integration assertions passed against the exact corrected APK, covering playback/progress, transport actions, session changes, navigation, both color modes, missing artwork and permission/screen recovery.
- All three track captures verified their current media title in the live widget before capture.
- Repository hygiene passed; signing secrets and raw emulator evidence remain untracked.

| Corrected artifact under artifacts/rim-fix/release/ | SHA-256 |
| --- | --- |
| MusicWidget-1.1.apk | `607def22209b6762abbf206eb3b03ef5e3057305e770314156d23c39b56a50d6` |
| MusicWidget-1.1.aab | `7138870cd164a74de16f4a5f18ec7ec707f02793e52fe5db4ff7328b66461aaf` |

The existing neutral signing key is retained; this APK installs over the preceding neutral-signed 1.1 build. The older [release](NEUTRAL_RELEASE_ACCEPTANCE.md) and [background](BACKGROUND_ACCEPTANCE.md) reports remain evidence for their stated prior APK hash. Their entire suites are not relabeled as runs of this corrected binary; the 36-check integration rerun above is the fresh verification for this rendering-only change.
