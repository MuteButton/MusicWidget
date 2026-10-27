# Timer scheduling optimization — October 26, 2026

Retained change: schedule periodic progress updates only when extrapolation can
actually advance a usable media position. Unknown positions, missing update time,
zero/non-finite speed and unknown duration do not start the timer. Forward playback
stops at duration; reverse playback stops at zero and can start from track end.
Metadata/playback callbacks still refresh immediately and restart scheduling when
valid timing returns. The final scheduled update still renders the clamped endpoint.

Existing artwork caching, change-only last-player preference writes, screen/lock
suppression and progress-only periodic rendering were retained. No extra cache,
metadata debounce, broad render skipping or home-screen polling was added: these
would add invalidation or latency risk without a demonstrated benefit in this run.
No battery setting change or foreground notification was introduced.

Validation: release build and 23 unit tests passed. Emulator recovery results are
recorded under ignored artifacts/ticker-optimization/acceptance/. This optimization
removes the previous one-per-second scheduled work in unusable/stationary timing
states; it does not reduce ordinary valid playback's refresh rate. No battery-drain
percentage or CPU saving is claimed. Samsung Optimized-mode freezing remains an
independent limitation; Unrestricted remains the selected phone setting.

Follow-up verification on October 26: all 13 recovery checks passed in
`artifacts/reliability-final/recovery/acceptance-results.txt`, including raw unknown
position, seek recovery, unknown duration, pause/resume, endpoint and next track.
The earlier unknown-position failure was traced to Android's controller getter
extrapolating a raw -1 position. The listener now retains playback-state callbacks
per session and uses that raw state for rendering and scheduling; entries are
removed when sessions disappear or the listener disconnects.

Samsung Unrestricted verification: real Brave playback advanced from 0:46 to
0:57. After an app-to-home transition, it advanced from 2:05 to 2:16 with matching
progress. This is a short device check, not a long-term battery or sleep/wake
signoff. An earlier stationary 0:01 sample had source playback speed zero and
was not counted as a successful advancing-timer test.