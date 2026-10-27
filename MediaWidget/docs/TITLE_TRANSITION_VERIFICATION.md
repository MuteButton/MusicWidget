# Title transition verification

The current battery-verification checkpoint was pushed before this change
(commit 64192c0). The title fix is a separate, unpushed working-tree change.

Only blank titles and exact YouTube hostnames (youtube.com, www.youtube.com,
m.youtube.com and music.youtube.com, optionally with a scheme/trailing slash)
receive the grace period. Ordinary titles are never delayed or substring-filtered.
A previous title may remain for up to one second within the same session. Repeated
callbacks do not extend that deadline. A real title replaces it immediately.
Persistent YouTube hosts become YouTube; missing metadata retains Unknown title.
Session changes, idle state and listener disconnection clear the cached title.

The resolver holds only one title/source/deadline and schedules at most one expiry
callback. Playback position, duration, speed, controls and the one-second progress
update path are not delayed or modified. No polling loop or background service
was introduced.

Validation completed:
- All 21 unit tests pass, including four transition-policy tests.
- Signed APK emulator core regression: 41 checks passed.
- Focused title/timing emulator regression: 9 checks passed.
- APK and private emulator evidence are stored under ignored artifacts/title-transition/.

The emulator recording of a 200ms hostname update followed by a real title was
inspected at 5 frames/second: Before transitioned directly to After, while elapsed
time advanced from 1:10 to 1:22. This sampled visual evidence supplements the
precise one-second deadline unit tests; it is not an every-frame assertion.

The same APK was installed on the connected Samsung. Brave's neutral fallback
and later real title were observed, and settled playback advanced from 0:50 to
1:14. The attempted physical transition video had no usable duration and was
excluded. Brave initially reported unknown position (-1); timing appeared after
opening its player and returning home. No synthetic timestamps were invented.

Candidate APK SHA-256:
919663f91c9aca21208a156eeb417dabdb056d7030a4111584ad247a5a233099.
The app version remains 1.2.4-preview8; distinguish this test APK by its hash.
This is not a newly published release. The title fix remains unpushed for review.
The temporary phone stay-awake override was restored to its original value (0)
after testing.
