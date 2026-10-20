# Pulsify

A native, offline Android music player for Android 10 and newer. Built with Kotlin, Compose Material 3, Media3, Room, Paging 3, DataStore, Coil, WorkManager, coroutines, and Navigation Compose. Application ID: `com.pulsify.music`.

The supplied **Pulsify App Icon.png** is the launcher icon and appears on Home. The interface uses a violet visual identity, rounded artwork, short navigation transitions, animated artwork changes, lazy lists, and an animated mini-player progress bar. There are no sample songs, accounts, analytics, or Internet permission.

## Implemented

- MediaStore device library or a user-selected, recursively scanned SAF music folder; persisted read access, batched upserts, unchanged-file metadata reuse, missing-file cleanup, WhatsApp/hidden-folder filters, embedded metadata and sampled/cached artwork.
- MediaStore change observer while the app is alive, startup scan, manual rescan, and a WorkManager scan about every 15 minutes while Android permits background work.
- Songs, albums, artists, genres, folders, favorites, recently added, recently played, most played, and playlists. Indexed database search includes title, artist, album, genre, and folder; playlist names appear as separate search results. Eight sort fields with ascending/descending order.
- Media3 ExoPlayer and `MediaLibraryService`: background audio, foreground media notification, lock-screen controls, audio focus, headphone-disconnect pause, Bluetooth/headset controls, and a paginated library root with local media-search callbacks and play-from-search resolution for compatible media browsers/Android Auto.
- Play/pause, previous/next, seek, ten-second skips, shuffle, repeat, speed, queue append/play-next/remove/reorder/clear. Play a filtered album, artist, folder, playlist, or library using the Play/Shuffle controls.
- Saved queue, current index, playback position, shuffle and repeat. Restores **paused**, with an option to disable restoring. Tap Play to resume. State is checkpointed every five seconds and at player events.
- Room playlists: create, rename, delete, add song/whole filtered selection, remove/reorder tracks, play, shuffle, JSON export/import through Android’s document picker. Duplicate song membership is prevented.
- Persistent favorites and play history. A play counts after 30 seconds or half the duration (whichever comes first, with a one-second minimum); seeking alone does not count.
- Responsive, scrollable Now Playing; persistent mini-player; accessible controls and ellipsized long titles. Adaptive collection grids and scrollable compact layouts.
- Sleep timer: 5/10/15/30/45/60 minutes, current song, 2/3/5/10 songs, or end of queue. Cancellation and remaining status. End-of-queue requires repeat off.
- Per-track local TXT/LRC attachment, retained privately on the device. LRC is displayed as text, without synchronized highlighting.
- Device-provided audio effects panel with active audio session ID and an unsupported-device message.
- Light/dark/system appearance, optional Android 12+ dynamic colors, artwork fallback, temporary image-cache clearing, Android permission settings.
- Keyboard-safe search and naming dialogs: `adjustResize`, safe-area/IME insets, scrollable dialogs, focused-field bring-into-view, and navigation hidden during keyboard use.

## Requirements and build

- Android Studio Meerkat or newer with compatible Android Gradle Plugin support.
- **JDK 17**, Android SDK Platform 35 and Build Tools 35.0.0.
- Gradle 8.11.1 wrapper (included), AGP 8.9.2, Kotlin 2.1.20.
- Internet access for the initial Gradle/dependency download.
- Minimum API 29; compile/target API 35. This is a deliberate pinned, stable toolchain. Check current Play Store target requirements before publishing there.

Open this directory in Android Studio, let Gradle sync, install the requested SDK components, and run the `app` configuration on an API 29+ device.

```bash
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows: use `gradlew.bat`. Android Studio creates `local.properties`; for command-line builds set `ANDROID_HOME` or provide your own `sdk.dir`. No developer-specific configuration belongs in Git.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Reports: `app/build/reports/`. Use `./gradlew connectedDebugAndroidTest` with an attached emulator/device to run the included input-dialog test. See [validation notes](docs/VALIDATION.md) for the exact checks executed in the delivery environment and the remaining hardware checks.

Unsigned release build: `./gradlew assembleRelease bundleRelease`. For a signed local release, configure the four `PULSIFY_*` environment variables described below, including the absolute keystore path, and pass monotonically increasing `-PversionCode=... -PversionName=...`.

## GitHub Actions: exact steps

1. Upload/extract the project into the **root** of a GitHub repository, including `.github/workflows/build-apk.yml`, `gradlew`, and `gradle/wrapper`.
2. Push to a branch, open a pull request, or select **Actions → Build Pulsify → Run workflow**.
3. The `debug` job installs JDK/SDK, runs unit tests and lint, and builds the debug APK with no secrets.
4. Open the successful run → **Artifacts → pulsify-debug**. Unzip that artifact and install the APK. Check reports are uploaded separately.

Debug builds use the runner’s temporary debug key. They are for testing; **different runs are not guaranteed to install as updates over one another**. Use the stable signed release workflow for real distribution and data-preserving upgrades.

### Signed GitHub releases

Create a release signing keystore locally with `keytool` and keep a secure backup. Do not commit it. In **Repository Settings → Secrets and variables → Actions**, add:

| Secret | Value |
| --- | --- |
| `PULSIFY_KEYSTORE_BASE64` | Base64 encoding of your complete JKS/PKCS12 keystore |
| `PULSIFY_STORE_PASSWORD` | Keystore password |
| `PULSIFY_KEY_ALIAS` | Signing alias |
| `PULSIFY_KEY_PASSWORD` | Key password |

For local signing use `PULSIFY_KEYSTORE` as the keystore’s absolute filesystem path, plus the three password/alias variables. CI decodes `PULSIFY_KEYSTORE_BASE64` into a temporary file and sets that path automatically.

```bash
git tag v1.0.0
git push origin v1.0.0
```

On a `v*` tag, after debug checks pass, the release job signs an APK and AAB and publishes them to **GitHub Releases** as `pulsify.apk` and `pulsify.aab`. Missing signing secrets fail the release job with an explicit setup message; the debug artifact still requires no secrets. Allow GitHub Actions to write repository contents if your organization restricts release creation.

`versionName` comes from the tag (without `v`); `versionCode` comes from `github.run_number`. Keep this workflow identity and signing key stable, and increase the version code when building outside CI. On the same device, **the same application ID, same signing certificate, and higher version code** allow Android to install an update without deleting app data. Do not uninstall between updates. Switching from a debug key to a release key requires uninstalling the debug installation once. Recreating the repository/workflow may reset run numbers; adjust versioning before your next release if necessary.

## Permissions and music folders

No permissions are requested immediately on launch. Select **Settings → Music library**:

- **Music folder** opens Android’s folder picker. Grant access to a directory containing your music. Read access is persisted; no broad filesystem permission is needed. Android may disallow selecting storage roots or protected directories.
- **Use device audio library** requests `READ_MEDIA_AUDIO` on Android 13+ or `READ_EXTERNAL_STORAGE` on Android 10–12 (manifest capped at API 32). Grant it to scan shared MediaStore music.
- The scanner reads content URIs, never assumes unrestricted access to `/storage/emulated/0`, and excludes recordings marked as non-music in MediaStore mode. Folder mode includes supported audio extensions/MIME types.
- If access is denied, choose a folder instead. If permanently denied, use **Android permissions** in Settings. Re-select the music folder after access is revoked.
- The app has foreground-service/media-playback, wake-lock, and audio-settings permissions for playback. WorkManager contributes boot-completed and network-state permissions for reliable scheduling; these do not grant Internet access. Media session notifications are exempt from Android 13’s general notification permission requirement; no unrelated notification permission is requested.

Changing library sources replaces the indexed source. Missing/replaced tracks are removed from playlist membership through foreign keys; your original files are never modified. Statistics stay keyed by URI. Moving files or switching providers can change their IDs.

New MediaStore changes are debounced while the process lives. Folder-provider changes have no universal event API: startup, manual, and periodic scans provide the fallback. Android’s background scheduling can delay these scans. SAF rescans enumerate the tree to detect removals but reuse metadata when modification time and size match. Providers must expose reliable modification times; copying a file with identical size/timestamp may need re-selecting its source to invalidate cached metadata. URI keys prevent duplicate rows from repeated scans; byte-identical files under different URIs are intentionally retained, rather than destructively deduplicated.

## Playlist backup

Use a playlist’s **⋯ → Export JSON** and select a document destination. **Playlists → Import playlist** imports that format with a unique name. Tracks resolve by existing URI, then by exact title/artist. The completion message reports imported/missing tracks. Audio files are not embedded; scan them before importing on another device. Missing tracks are skipped. Internal Room files are never exposed. M3U/M3U8 is not supported in this version.

## Architecture

```
ui/             Compose screens, theme, keyboard-safe controls, MusicViewModel
                ↓                  ↓
data/           Room DAOs       MediaController
                Scanner            ↓
                DataStore      playback/MusicService
                WorkManager    MediaLibrarySession + ExoPlayer

domain/         Parameterized search/filter/sort and playback/queue rules
playback/       Media item mapping and durable session codec
```

`PulsifyApplication` owns a small lazy dependency container. Hilt is intentionally unnecessary for this single application module. Database entities are normalized into songs, independent per-song statistics, playlists, and ordered playlist memberships. Album/artist/genre/folder screens are indexed SQL groupings rather than duplicated metadata tables. Version 1 Room schema is exported and checked in. Future schema changes must supply explicit migrations; destructive migration fallback is not enabled.

Library rows use Room Paging (50-item pages). Artwork is sampled to a bounded resolution, stored separately from the database, and displayed through Coil. Scans and metadata work run on Dispatchers.IO. Play-all queues are capped at **2,000 songs** to keep MediaSession/Binder traffic and persistence bounded; individual songs beyond that cap still play. Collection screens remain paged. Groups and playlist summaries are small materialized lists. Artwork files remain in private files storage to avoid rescanning metadata after routine temporary-cache cleanup.

## Limits and deliberate fallbacks

- This is a complete source project with a working local-player feature set, not a claim that every item in the extended brief is implemented or certified for production distribution.
- Online playlist retrieval/downloading and a download-manager UI are omitted. Local playback has no network dependency. No YouTube extraction, DRM circumvention, authentication/anti-bot bypass, or unsupported platform downloading is included. A future lawful provider must have its own validated API, explicit download rights, bounded background jobs, safe URL/redirect handling, and SAF destination-write permission.
- No crossfade, ReplayGain, normalization, custom equalizer DSP, artwork picker, grid/list preference, notification customization, arbitrary skip duration, or per-song bookmarks. Gapless behavior depends on codec/device/metadata. Effects use an installed system panel and may be unavailable.
- Playlist art uses a branded collection placeholder. Queue/playlist reordering uses accessible up/down actions, not drag gestures. Playlist exports use JSON only.
- TXT/LRC lyrics are local text, not fetched or synchronized. Playback speed is session-scoped. Sleep timers persist while the playback service lives, not across process termination/reboot; end-of-queue requires repeat off. Android force-stop terminates playback and timers.
- Android Auto exposes a basic paginated music root through MediaLibraryService. Automotive hardware/host certification and richer category browsing have not been tested here.
- Media format support follows Android/Media3. Corrupt, deleted, or inaccessible files display a playback error; automatic skipping of bad tracks is not enabled.
- The app is designed for small/large screens and landscape through scrolling/adaptive grids. Real-device keyboard, talkback, orientation, Bluetooth, audio focus, and OEM background-policy checks remain necessary before release. The documentation does not substitute for those checks.
- No destructive library reset, database export, automatic app updater, or application data cloud backup. Keep playlist JSON backups and signing-key backups separately.

## Screenshots

Add actual device screenshots here after running the app: Home, Library, Now Playing, Search with the keyboard open, and Playlist naming with the keyboard open. No fabricated app screenshots are included.

## Usage

Play audio you own or have permission to use. Android folder grants, platform rules, and licenses still apply. No third-party music, artwork, or copyrighted logos are bundled. The supplied repository icon is retained as project branding.
