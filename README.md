# AetherWave v1.0.0 — Liquid Glass Music Player

AetherWave is an Android music-player foundation built with Kotlin, Jetpack Compose, Media3, Room and Hilt.

## Existing v0.3.0 data and playback foundation

### Local library and Room

- `TrackEntity` / `TrackDao` persist track metadata, favorites (`isLiked`), listening history (`lastPlayedAt`), and offline-download state (`isDownloaded`, `localFilePath`).
- `PlaylistEntity`, `PlaylistTrackCrossRef`, and `PlaylistDao` provide local playlist creation, rename, deletion, and track membership.
- `AetherWaveDatabase` is registered through Hilt in `DatabaseModule`.
- `MediaStoreAudioSource` scans on-device music using the Android audio-library permission. It returns `content://` URIs rather than depending on deprecated filesystem paths.

### Streaming cache and offline downloads

- A singleton Media3 `SimpleCache` with `LeastRecentlyUsedCacheEvictor` is capped at 1 GiB.
- `CacheDataSource` wraps `RefreshingResolvingDataSource`, so the stable `aetherwave://` URI is checked in cache before NewPipe resolves a potentially expired CDN URL. Fully cached streams can therefore be replayed without URL extraction. `RoutedAudioDataSource` bypasses this network cache for local `content://` and `file://` items so local library playback does not duplicate the user's own files into cache.
- Local `content://` and `file://` media are supported by `DefaultDataSource`, including common FLAC, WAV and MP3 files supported by Media3's extractors/decoders.
- `OfflineAudioDownloadManager` reads through the same caching pipeline and also writes an app-private audio file under `files/offline-audio/`. The absolute file path is persisted to Room and is used by `TrackMediaItem` for explicit offline playback.
- The 1 GiB LRU cache is temporary/evictable; user-requested offline files are stored separately in app-private files and remain until removed or app data is cleared.

### LRCLIB synced lyrics

- `LrcLibApi` implements the stable `/api/get` and `/api/search` endpoints.
- `LyricsParser` handles multiple timestamps per line, fractional seconds, signed `[offset:]` headers, and returns sorted `LyricLine(timestampMs, text)` values annotated `@Immutable`.
- The repository tries the exact track signature first and falls back to LRCLIB search when the exact endpoint returns 404.

### Repository

`MusicRepository` / `MusicRepositoryImpl` combine YouTube Music Innertube `WEB_REMIX` Home and song-only search, MediaStore scan for the Library Local Files tab, Room favorites/history/playlists/downloads, and LRCLIB lyrics lookup. NewPipeExtractor is reserved for stream URL/cipher fallback during playback; it is not used for Home or Search discovery. UI code can depend on the domain interface rather than data-layer classes.

## Runtime permission for local audio

Request `MediaStoreAudioSource.requiredReadPermission()` at runtime before calling `scanLocalAudio()`. This resolves to `READ_MEDIA_AUDIO` on Android 13+ and `READ_EXTERNAL_STORAGE` on older supported Android versions. The scanner throws a clear `SecurityException` if the permission has not been granted.

## GitHub Actions build

`.github/workflows/build.yml` runs on push, pull request and manual dispatch. It installs JDK 17 and Gradle 9.5.0, generates `gradle-wrapper.jar` on the runner with `gradle wrapper`, runs `./gradlew testDebugUnitTest assembleDebug`, and uploads `app/build/outputs/apk/debug/app-debug.apk` as the `AetherWave-v1.0.0-debug-apk` artifact.

The repository intentionally does not bundle `gradle-wrapper.jar`; running the workflow generates it on the GitHub runner. For a local command-line build, generate the wrapper once with an installed Gradle 9.5.0, or open the project in Android Studio and use its Gradle tooling.

## Build versions

- JDK 17
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0
- Kotlin Gradle Plugin 2.4.21 / KSP 2.3.12 (pinned explicitly for AGP 9 built-in Kotlin)
- Media3 1.11.1
- Room 2.8.5
- NewPipeExtractor 0.27.1
- Retrofit 3.0.0

## Engineering notes and constraints

- Float PCM is an output preference, not a guarantee of bit-perfect playback or a hardware-native sample rate. The actual path depends on the Android audio policy, device audio HAL, DAC, route and source.
- YouTube extraction remains dependent on upstream YouTube player/cipher behavior and NewPipeExtractor updates. It is not an official YouTube Music API.
- Only media fully downloaded through `OfflineAudioDownloadManager` is guaranteed available offline as a standalone file. Automatically cached stream segments are LRU-managed and may be evicted.
- Downloaded audio files are stored inside the app's private files directory, not the public Downloads folder. They are removed when app data is cleared or the app is uninstalled.
- NewPipeExtractor is GPL-3.0; review licensing obligations before distributing the application.

## Verification status

The LRC parser was compiled independently with a minimal Compose annotation stub and exercised with timestamp, repeated timestamp, fractional-second and offset samples. ZIP structure and YAML/Gradle source configuration are inspected. **A full Android Gradle build has not been run in this environment** because it has no installed Gradle distribution/Android SDK and outbound dependency downloads are unavailable. The GitHub Actions workflow is included to run the authoritative `assembleDebug` build; build success is not claimed until that workflow passes.

## v1.0.0 — UI and navigation layer

The v1.0.0 UI layer builds on the v0.3.0 playback, local database, cache, MediaStore, and LRCLIB modules. The app version remains `1.0.0`; these are updates to the existing release line, not a 2.0.0 version bump.

- **Home:** YouTube Music `FEmusic_home` feed with Quick Picks, trending and recommended tracks; local MediaStore files are not shown on Home.
- **Search:** YouTube Music results with a 400 ms debounce and playlist insertion.
- **Library:** local playlists, a dedicated Local Files tab (tracks shorter than 30 seconds are filtered out), completed downloads, and liked songs.
- **Now Playing:** artwork-derived palette background, format badge, seek control, shuffle/repeat, like, offline download, and synchronized-lyrics view with tap-to-seek and active-line auto-scroll.
- **Persistent mini-player and floating navigation:** glass surfaces remain above scrollable screen content.
- **Permissions:** Android 13+ requests `READ_MEDIA_AUDIO` and `POST_NOTIFICATIONS`; Android 8–12 requests `READ_EXTERNAL_STORAGE`. The Home scan action can request audio access again after a denial.

### Build

The GitHub Actions workflow installs the pinned Gradle 9.5.0 distribution, generates the wrapper because `gradle-wrapper.jar` is not committed in this project, runs unit tests and `assembleDebug`, and uploads `app-debug.apk` as the `AetherWave-v1.0.0-debug-apk` artifact.

For this archive, the source and project configuration were statically inspected. A full Android Gradle build was not run in the packaging environment because it has no Android SDK and no installed Gradle distribution. The first authoritative compile/test result will be the GitHub Actions run after the archive is pushed.
