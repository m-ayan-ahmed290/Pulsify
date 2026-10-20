# Validation

## Pulsify 1.1.1 lyrics and queue fixes — 2026-10-20

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passes with **25 unit tests**, zero failures/errors, and no lint errors.
- Five new lyrics tests cover filename validation independent of provider MIME type, fractional and repeated timestamps, ordering, LRC file offsets, hour timestamps, simultaneous translations, instrumental gaps, malformed/plain text, and matching highlight/seek timing.
- Two new device-test cases compile: active-line selection/tap-to-seek, and the per-song delay control. These new device tests have **not** been run on an emulator or physical device in this change.
- Picker uses `*/*` so Android providers cannot exclude LRC based on MIME classification; selected filenames and bounded UTF-8 contents are validated before replacing the stored attachment. File picking on individual OEM document providers remains a device regression check.
- Queue delete background is absent at rest, including on the translucent current-song row. The foreground X is absent during swiping. A rejected dismiss state permits a removed track to return cleanly through Undo. Visual swipe/Undo regression on a physical device remains required.
- Only Library/Settings rescan buttons call `scan(manual = true)`. Startup/resume, source/setting changes, observers, and queued automatic scans do not post the song-count snackbar. A manual request queued behind an automatic scan retains its manual reporting flag.
- Lyrics polling increases to 100 ms only while the lyrics sheet is visible in the foreground; status commands remain limited to once per second. Parser results are remembered, active-line lookup uses binary search, and displayed rows are lazy. No frame-time benchmark is claimed.
- Per-song timing changes are saved separately from the lyrics text. Replacing/removing an attachment resets its adjustment. Original source files are never edited.


## Pulsify 1.1.0 redesign — 2026-10-20

Toolchain: JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, SDK Platform 35 / Build Tools 35.0.0.

Executed successfully after the navigation, screen, Sound Studio, and keyboard changes:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-daemon --max-workers=2
```

Result: **BUILD SUCCESSFUL**, 81 tasks, 22 executed and 59 up-to-date.

- **20 unit tests passed**, zero failures/errors: LibraryDatabaseTest (6), PlaybackRulesTest (3), StoredSessionTest (3), AudioEffectsTest (5), ListeningToolsTest (3).
- Effects tests verify configuration persistence without a session, safe recovery/clamping, logarithmic frequency mapping, headroom bypass, and no attenuation when effects are unavailable. They do not measure physical audio output.
- Listening-tool tests cover A–B boundaries, short/reversed points, duration clamps, and search-history limits/deduplication.
- Debug lint: **zero errors**, 29 warnings, one informational item. No baseline or disabled error checks. Warnings include dependency update suggestions, exported media-service/backup advice, and optional style cleanup.
- Debug APK and instrumentation APK compile. Instrumentation covers a scrollable naming dialog, primary navigation, internal Sound Studio/preset selection, actual IME visibility with the search field visible, and different intermediate/settled dock animation pixels.
- GitHub Actions compiles the instrumentation APK alongside unit tests, lint, and the debug APK. Instrumentation compilation is distinct from executing device tests.
- Repository icon is retained. Room entities/schema version remain unchanged; playlist artwork is a joined projection.

Optimized unsigned release APK and AAB also built successfully with `assembleRelease bundleRelease` (55 tasks). Distribution signing still requires the documented release key.

API 29 software-emulator instrumentation: **2 of 4 passed** (playlist dialog and intermediate/settled navigation-animation frames). The search test could not open the IME within its timeout; the Sound Studio test reached the selected Bass preset but its text selector matched both the chip and the updated chart label. The test now explicitly requests the IME and selects the clickable preset node; these corrections are compiled by CI but have not yet been rerun on the emulator. Do not count those two checks as passed. The emulator also displayed System UI/app responsiveness dialogs without hardware acceleration, so it cannot establish real-device performance. Synthetic local audio fixtures were used only on the emulator, never bundled in the project.

## Device regression checklist

- API 29 and API 33+: deny/allow audio access, permanently deny, choose folder, revoke folder access, then recover.
- Import MP3/FLAC/AAC/OGG, nested folders, corrupt metadata, missing artwork, long titles, and a 10,000-song library. Add/delete a file and verify manual/periodic scan behavior.
- Play music, lock device, switch apps, use notification/lock-screen/Bluetooth/headset controls, receive an interruption, unplug headphones, and force-stop/reopen.
- Pause mid-song and reopen; verify restored queue/position/shuffle/repeat/speed/volume and restore-off behavior.
- Create, rename, reorder, delete, export, and reimport playlists. Select several library songs and create a playlist from that selection. Original audio files stay intact.
- Favorites/history survive rescanning; short previews do not increment play counts.
- Queue play-next/append/move/swipe-remove/Undo/clear-played/clear, seek, lyrics, and every sleep-timer mode.
- Sound Studio: presets/custom bands/master bypass/bass/virtualization/headroom, output/session changes, background playback, persistence, and disabled controls on unsupported devices. Compare actual output with bypass on physical headphones/speakers. The app never launches the system equalizer panel.
- A–B repeat within a track, minimum interval, clear, pause/resume, and clearing on track change.
- Small phone dimensions, landscape, large fonts: search/name inputs remain visible with keyboard open; dialog actions remain reachable by scrolling. Run `connectedDebugAndroidTest` on a hardware-accelerated emulator/device.
- Light/dark/dynamic themes, TalkBack labels and traversal, visible focus, touch targets, reduced/system-disabled animations, and Android Auto browsing.
- Screen transitions, spring capsule, player expansion/artwork/title transitions, queue insertion/removal/reordering: inspect frame timing on representative physical devices. Build success cannot establish smoothness or battery efficiency.

## Previous baseline

The initial 1.0.0 source archive was built from a fresh extraction with debug/unit/lint/instrumentation-APK and minified unsigned release APK/AAB tasks passing. Its 12 unit tests passed. The Android SDK setup fix subsequently passed GitHub Actions. The current redesign supersedes the baseline UI and external equalizer-panel behavior.
