# Release lint triage

API 36 migration run, October 23, 2026: **0 errors, 71 warnings**, reduced from 135. The unused legacy animation resources and unused colors were removed; Android test dependencies were updated after the old Espresso input injector failed on Android 16. No lint categories were globally suppressed.

| Category | Disposition |
| --- | --- |
| Newer SDK/dependency/AGP available | Target 36 meets the current Play submission requirement. Keep newer-version notices visible; resolved production dependencies are scanned against OSV. A newer version notice alone is not a demonstrated security defect. |
| Kotlin/compat convenience APIs | Refactoring opportunities; current equivalent calls remain tested. |
| Small widget timestamp text | Retained for the approved layout; a full accessibility review remains outstanding. Do not claim accessibility certification. |
| Placeholder timestamp strings | Replaced at runtime by live metadata; localization/formatting improvements remain possible. |
| Unused resources/attributes | Remaining compatibility layouts and resources should be evaluated alongside older-Android testing, rather than removed indiscriminately. |
| Layout/overdraw and launcher monochrome icon | Improvement backlog; no associated crash or functional failure demonstrated by the release checks. |

The detailed lint output remains in the local build reports and CI artifacts. CI fails on lint errors. Warnings remain visible rather than establishing a baseline that would hide new findings.
