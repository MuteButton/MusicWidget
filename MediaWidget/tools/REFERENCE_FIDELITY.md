# Reference fidelity status

## Current target: v5

The latest user-supplied target is `../../artifacts/music-widget-PIXEL-VERIFIED-v5.zip`, extracted to `../../artifacts/v5-source/music-widget-v5`. Its canonical crops are `REFERENCE-idle.png` and `REFERENCE-playing.png`, both **1030 x 225**. They supersede the earlier whole-screen PNG for the current Android 12+ full layout.

**Full live-widget pixel-for-pixel acceptance is not met.** Idle is verified RGB-identical at native reference resolution in an Android diagnostic host; the resized launcher render and live playing state still differ. Functional tests and source-layer verification are reported separately.

`Verify-V5Assets.ps1` independently reconstructs the SVG's embedded raster layers and measures zero RGB differences against both supplied PNGs. This is a source-asset audit only. Results: `../../artifacts/media-widget-v5-source-audit/independent-verification.json`.

`V5Artwork.kt` uses the supplied exact idle layers and transport sprites. All coordinates scale from 1030 x 225; both states have identical outer dimensions. Control visuals are composed with the glass before scaling, avoiding independent image rounding seams. Independent accessible hit targets still dispatch actual session commands, and disabled actions are dimmed in the composition. Live playback uses native text, accurate progress, and an aspect-preserving center crop across the full artwork/fade frame. The debug fixture uses the full supplied artwork sprite, including its precomposed glass continuation; production covers still come from the selected MediaSession.

The supplied playing underpaint contains polygon-shaped repair artifacts. The runtime skin instead preserves the source rim and repairs the regions occupied by sample metadata and artwork before placing live views. These repairs, dynamic artwork blending, font rasterization and scaling introduce differences. The supplied progress fill is approximately 38.5%, while the displayed `1:24` and `-3:18` imply 29.8%; the live widget preserves the mathematically correct value.

The launcher currently allocates a 979 x 214 card, so the comparison normalizes its real ADB screenshot to 1030 x 225. `Compare-WidgetV5.ps1` measures **every RGB pixel**, without excluding the rounded exterior. It records bounds, differing pixel count, maximum difference and mean absolute error, and requires native resolution as well as zero differences for `pixelPerfect=true`. Current measurements and comparison panels are in `../../artifacts/media-widget-v5-art-comparison/`.

The pre-refinement baseline was idle **1.31652/255** and playing **6.54437/255**. The current numerical results are recorded in `idle-comparison.json` and `playing-comparison.json` in the art-comparison directory. A low mean error is not a percentage of identical pixels.

Final measured launcher results: idle **1.31652/255** (104,654 differing RGB pixels) and playing **3.90636/255** (154,753 differing RGB pixels), each out of 231,750 normalized pixels. The prior renderer/fixture measured 5.18450/255. The final artwork revision also changes the fixture from a cropped square to the full supplied sprite, so its lower error is not a renderer-only comparison. Full pixel identity remains unachieved for the live playing state.

`Test-WidgetNativeReference.ps1` captures the production idle RemoteViews at exactly **1030 x 225** inside a debug diagnostic host on the emulator. All **231,750 RGB pixels match**, with mean and maximum channel error zero. Evidence is in `../../artifacts/media-widget-v5-native-idle/`. This isolates native rendering from launcher scaling; it does not assert that a 979 x 214 home-screen card has identical physical pixels.

## Refinement and verification, 2026-10-19

### Native playing-state isolation

`Test-WidgetNativePlaying.ps1` now captures the same `WidgetRenderer.createViews` used by the launcher, bound to the real fixture MediaSession at exactly 1030 x 225 in the diagnostic host. This is not a substituted reference image. The measured error is **3.3411089536/255**, with **56,558 differing pixels** and **175,192 identical pixels**. Evidence: `../../artifacts/media-widget-v5-native-playing/playing-comparison.png` and its JSON. The remaining differences are concentrated in dynamic captions/timestamps, progress, and the artwork fade. The shared renderer preserves the previous launcher result of 3.90636/255; launcher placement is still a separate comparison.

The supplied ZIP's README explicitly states that its exact sprites are raster crops, that the clean underlying artwork/background and original font do not exist, and that dynamic replacements only approximate the fixed sample. Its implementation prompt also explicitly forbids claiming 1:1 fidelity after replacing raster elements with live content. Exact fixed-sample presentation versus accurate live media rendering therefore requires an acceptance decision; neither a screenshot substitution nor a false progress value is being silently introduced to pass the comparison.

Independent native capture region checks confirm zero differing pixels across all three complete control rectangles: previous 10,608 pixels, pause 13,794 pixels, next 9,682 pixels (`control-regions.json`). After extracting the shared renderer, all 26 core and 10 extended functional checks and six launcher/hash checks pass again; results are in `media-widget-v5-shared-renderer-tests/` and `media-widget-v5-shared-renderer-comparison/` under artifacts. Build, six unit tests, and lint also pass, with lint warnings remaining.

The debug-only `VisualCalibrationActivity` compares actual Android RemoteViews/TextViews against the reference at the measured launcher size. Searches covered 5,544 title settings, 2,916 artist settings, 4,620 timestamp settings, and 55 artwork fade/extension settings. Calibration output is preserved in `../../artifacts/media-widget-v5-calibration-full.json` and `media-widget-v5-calibration-boundaries.json`. The finite search establishes the best tested settings in those ranges, not a mathematical global optimum.

An initial Paint-only font candidate was rejected after the real home-screen mean error increased to 6.83675/255. Actual TextView calibration improved it to 6.20780/255; corrected artwork fade, timestamps and progress colors improved it to 5.58337/255 before the final shared-surface control refinement. Each candidate has separately retained ADB captures. No reference-specific title or artist substitution is used in production.

The shared-surface control revision measured 5.18450/255. It keeps independent live click targets while drawing control skins once with the glass, eliminating separate-resampling seams. All normal, paused, disabled, missing-artwork, permission and recovery checks were rerun after this change. `../../artifacts/media-widget-v5-best-tests/state-review.png` is a labeled contact sheet of unmodified crops from real emulator screenshots for visual review.

The final artwork treatment uniformly center-crops to a 307 x 225 frame and fades its actual image continuation from x=215 to x=307. This removes synthetic horizontal edge streaks without stretching covers. The supplied reference fixture includes precomposed glass; ordinary covers come from MediaSession and are independently tested. The older 55-candidate artwork calibration records the previous square/edge treatment, not the new production method. A weak source reference and bounded output cache avoid retaining obsolete large source covers, and the renderer skips the unused legacy crop when all widgets use v5. Progress ticks still send only progress/time updates. Missing artwork now uses the same pill-shaped fade instead of a boxed placeholder. The permission state restores the glass/CTA before drawing its live recovery captions, removing the supplied underpaint's retouch scars.

Validation: 6 unit tests pass; build and lint pass (warnings remain, zero errors); 26 core and 10 extended emulator checks pass. Six additional checks cover installed APK hash equality, full control hit bounds, bottom-edge Pause, Play, idle recovery and Open App. Two native-resolution checks verify bounds and zero idle pixel differences: **44 emulator checks total**. Results are in `../../artifacts/media-widget-v5-art-tests/`, its `extended/` subdirectory, `media-widget-v5-art-comparison/`, and `media-widget-v5-native-idle/`. Final comparison captures use a real debug MediaSession held in buffering at 84 seconds with supported actions, keeping the Pause glyph and timestamps deterministic. Real advancing playback is independently tested in the core suite. These are fixture captures, not a third-party player's playback.

Android 7–11 and narrow allocations retain the previous layouts; the v5 full renderer uses Android 12 RemoteViews sizing APIs. The v5 full typography follows reference coordinates rather than system font scaling, with full accessible metadata retained. Launcher placement, wallpaper and system chrome are outside the cropped v5 target.

## Historical target and implementation notes

Reference: `../../artifacts/reference.png` (941 x 1672).

**The current rendering does not meet the requested pixel-for-pixel acceptance criterion.** Functional test success is separate from visual acceptance.

Measured playback card bounds: x=38, y=605, width=865, height=257. Artwork: x=83, y=642, approximately 183 square. Progress: x=298..858, y=776..787. The full layout uses approximately the same proportions at a 374dp card width: 111dp height, 19.45dp artwork inset, 79dp artwork, 12.5dp artwork-to-content gap. The card is centered in a two-row launcher allocation instead of stretching vertically. Existing one-row placements must be resized or re-added.

Remaining differences include native font rasterization, glass lighting, edge glow, transparency against a different wallpaper, and small geometry differences. The PNG is a flattened composition, not separate transparent glass/lighting layers. The production widget does not embed the reference screenshot as its surface.

The reference progress fill is approximately 199/560 = 35.5%, but its displayed times imply 84/(84+198) = 29.8%. Production progress remains derived from actual playback position and duration.

`Compare-WidgetReference.ps1` produces a labeled reference/emulator/difference panel and a mean absolute RGB error. It rescales the real emulator card crop to the reference size, excludes the rounded exterior from the metric, and never substitutes its output for an emulator screenshot. A nonzero error is a failed pixel-identity comparison. This normalized diagnostic is not proof of native-resolution identity.

The reference album crop is debug-only fixture data. Production artwork still comes from the selected MediaSession.

Validation: build, unit tests and lint passed; 24 core and 10 extended emulator checks passed after the geometry changes. The last button-translation-only adjustment is validated by a fresh build, screenshot, and transport smoke check.

## Editable reconstruction supplied later

The reference-only ZIP is extracted under ../../artifacts/gradient-glass-source. Its README explicitly identifies the SVGs as an approximation. The PNG remains authoritative. GlassArtwork.kt renders cached native Canvas surfaces, blurred rims, directional highlights, control skins, the idle medallion, and CTA. It uses no screenshot as its surface and keeps all labels and actions live. The source SVG geometry and suggested Inter font were not adopted as authoritative.
