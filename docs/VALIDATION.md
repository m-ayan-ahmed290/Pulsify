# Validation

Build and test results are recorded below after execution. An APK build does not prove hardware media routing or soft-keyboard behavior.

## Device regression checklist

- API 29 and API 33+: deny/allow audio access, permanently deny, choose folder, revoke folder access, then recover.
- Import MP3/FLAC/AAC/OGG, nested folders, corrupt metadata, missing artwork, long titles, and a 10,000-song library. Add/delete a file and verify manual/periodic scan behavior.
- Play music, lock device, switch apps, use notification/lock-screen/Bluetooth/headset controls, receive an interruption, unplug headphones, and force-stop/reopen.
- Pause mid-song, restart the app, and verify restored queue/position/shuffle/repeat. Verify restore-off setting.
- Create, rename, reorder, delete, export, and reimport playlists. Verify song files stay intact.
- Favorites and history survive a rescan; short previews do not increment play counts.
- Queue play-next/append/move/remove/clear, speed, seek, lyrics, and every sleep-timer mode.
- At small phone dimensions, landscape, and large font scaling: search/name inputs stay visible with the keyboard open; submit/cancel remain reachable by scrolling. Run `connectedDebugAndroidTest` for the included dialog test.
- Light/dark/dynamic themes, TalkBack labels, visible focus, 48dp actions, unsupported audio-effects panel, Android Auto media browser.

## Execution results

Verified on 2026-10-20 in a fresh extraction of the project ZIP. No build output, local.properties, SDK installation, signing key, or IDE state was bundled in the source archive. SDK/dependencies came from the external build toolchain and standard repositories.

Toolchain: JDK 17, Gradle 8.11.1 wrapper, AGP 8.9.2, Kotlin 2.1.20, SDK Platform 35 / Build Tools 35.0.0.

Executed successfully:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease bundleRelease --continue --no-daemon --max-workers=4
```

Result: **BUILD SUCCESSFUL**, 135 tasks executed, from a clean source extraction.

- 12 tests passed, zero failures/errors: LibraryDatabaseTest (6), PlaybackRulesTest (3), StoredSessionTest (3).
- Debug lint: zero errors, 26 warnings. Warnings concern pinned dependency versions (20), intentionally exported media browsing service (1), pre-Android-12 backup configuration advice despite allowBackup=false (1), and optional Kotlin style changes (4). No lint baseline or disabled error checks.
- Debug APK and UI-test APK built successfully. The UI-test APK was compiled, not run on a device.
- Minified unsigned release APK and unsigned release AAB built successfully. A release signing key was not supplied; configure the documented secrets for distribution.
- Debug APK verified using apksigner (APK Signature Scheme v2); zipalign alignment check passed, including the 16 KB native-library page alignment option.
- aapt2 verified application ID com.pulsify.music, version 1.0.0 / code 1, minSdk 29, targetSdk 35, and Pulsify label.
- GitHub workflow passed actionlint 1.7.7 semantic validation (optional shellcheck/pyflakes integrations disabled); XML and archive integrity checks also passed. The workflow was not executed remotely on GitHub in this environment.
- Source code and exported Room schema in the fresh build match the delivered project. Documentation was updated afterward with these results.

Debug APK SHA-256: `d403ecce19259a4acfb1ce0fbfd608e4c33e778dac4c24048df26c7ab779eec0`

Not executed: attached-device/emulator instrumentation, real IME visibility, actual audio output/focus/Bluetooth/lock-screen behavior, Android Auto host integration, accessibility traversal, or large-library performance measurement. These require the device checks above. Compilation and Robolectric tests do not replace those checks.
