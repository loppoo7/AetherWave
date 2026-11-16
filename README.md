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

## Playback stability and Up Next (v1.0.0 patch)

- **Crash containment.** `RoutedAudioDataSource` is the outermost audio data source. It converts every non-IO runtime exception from the resolver, cache or extractor into `IOException`. Media3's Loader would otherwise wrap such an exception as `UnexpectedLoaderException` and rethrow it on the main thread, which crashes the app.
- **Service lifecycle.** `AetherWavePlaybackService` releases only its `MediaSession` on destroy. The app-wide `ExoPlayer` singleton is no longer released, so the next service start gets a working player.
- **Reconnect.** `PlaybackController` listens for `MediaController` disconnection, clears the stale controller and rebuilds the connection on the next command.
- **Retry layer.** A failed track is re-prepared once. Re-preparing re-runs stream resolution: Innertube first, then NewPipeExtractor.
- **Innertube parsing.** All JSON access is type-checked. `/next` (radio queue) and section titles read from `header` are supported.
- **Up Next / auto-radio.** Playing a YouTube track loads its related queue through `/next`. When fewer than 3 items remain in the live queue, the next radio batch is appended. Duplicates are skipped.
- **Playback options.** Sleep timer (15, 30, 45, 60 minutes or end of track), playback speed (0.75x to 2x), pitch, and skip silence. The options sheet opens from the `⋯` button on Now Playing.

- **Up Next sheet.** The UP NEXT button on Now Playing lists the live queue. Each row can be tapped to jump, moved up or down, or removed.
- **Lyric share.** The SHARE button sends the currently highlighted synced lyric line.

- **Explore.** The EXPLORE tab loads `FEmusic_explore` sections (charts, moods, new releases) and shows your most-played tracks at the top.
- **Most played.** Each track start increments `playCount` in Room. Database version 2 adds the column through `MIGRATION_1_2`, so existing libraries and playlists are kept.

- **Loudness normalization.** `/player` returns `playerConfig.audioConfig.loudnessDb` for each track. `LoudnessRegistry` converts it into a volume that is applied after the track resolves. The Normalize volume switch is in the playback options sheet. Local files are never changed.
- **Visual system.** Neutral graphite and off-white palettes replace the indigo tints. Glass edges use the 0.75 dp micro-specular border. The ambient mesh is calmer. The Now Playing artwork has a shadow tinted by the cover palette. Swipe the artwork to change tracks and swipe the top bar down to close.

Not yet implemented: backup import/export (needs a file picker), drag-to-reorder (up/down buttons are used instead), and a dynamic accent that follows each album cover across the whole app.

## Ported from Auralys (partial)

- **Sound style.** Normal, Sped up, Nightcore, and Slowed, set through speed and pitch presets. These are approximations and do not include reverb.
- **Loudness target.** Quiet, Balanced, Loud, and Aggressive set the headroom used by normalization. Volume never exceeds 1, so the Loud and Aggressive presets only change the result for quiet tracks.
- **Sleep timer fade-out.** The volume ramps down over the last minute before playback pauses. Resume restores the normal volume.
- **Glass tiers.** `GlassTier` G1, G2, and G3 control the rim width and tint strength of liquid glass surfaces.
- **Palette.** Dark and light backgrounds, surfaces, and text now use the Horizon values from the reference app. Error colors are included.

Refraction and blur were already provided by haze-glass, so they were not ported again.

- **Pure / Lossless.** When enabled, local files play with neutral speed and pitch, no silence skipping, and no normalization. The user's settings are kept and return when a YouTube track plays. YouTube streams are lossy and are not affected.

## Crash report

If the app crashes, the stack trace is saved to the app's private storage. The next time the app opens, a dialog shows the report with a Copy button. Send that text to the developer.

## 1.3 — preview design applied

- **Vector icons.** Toolbar, bottom bar, Now Playing controls, and mini player use vector icons (`AwGlyph`) instead of text characters. Glyphs without an icon fall back to text.
- **Pill bottom bar.** The bottom navigation uses a full pill shape.
- The palette, glass tiers, and crash report from 1.2 are included.

## 1.4

- **Crash fix (Android 10).** NewPipeExtractor v0.27.1 calls `URLDecoder.decode(String, Charset)`, which Android 10 lacks. Core library desugaring now provides it. The audio data sources also convert `Error`s into playback errors, so a failure like this no longer closes the app.
- **Black launch screen.** The window background is black, so the white flash on start is gone.
- **Lighter navigation.** The ambient background is static. The continuous animation was redrawing the whole screen every frame.
- **Icons.** Icon strokes are slightly heavier.

## 1.5

- **Playback.** Stream resolution tries the Auralys client set first: ANDROID_VR, IOS, and ANDROID_MUSIC, each with its own User-Agent. WEB_REMIX and NewPipeExtractor follow as fallbacks.
- **Status messages** appear at the top of Now Playing, not in the middle.
- **Language.** Settings > Language switches between System, English, and Bahasa Indonesia. Some labels (Settings rows, options sheet) are still English-only.

## 1.5

- **Playback.** Stream resolution tries the Auralys client set first: ANDROID_VR, IOS, and ANDROID_MUSIC, each with its own User-Agent. WEB_REMIX and NewPipeExtractor follow as fallbacks.
- **Status messages** appear at the top of Now Playing, not in the middle.
- **Language.** Settings > Language switches between System, English, and Bahasa Indonesia. Some labels (Settings rows, options sheet) are still English-only.

## 1.6

- **Persistent playback notification** uses a high-importance channel, so it appears at the top of the notification shade. Android may still place media controls in its own media panel.
- **In-app messages** return to their original place at the bottom of Now Playing.

## 1.7

- **Build fixes.** The playback notification channel is created in code with high importance. The duplicate import in Settings is removed.
- **Performance.** Release builds use R8 code shrinking and resource shrinking. Test performance on the release APK, since the debug APK is always slower.

## 1.8

- **Icons.** Every glyph in the app is drawn with Material Rounded icons (`AwGlyph`), using the same stroke weight and corner style everywhere. The Compose BOM manages the icon library version.

## 1.9

- **Settings hub.** Settings is split into Appearance, Sound & playback, Language, and About. Each option is connected to a feature that works.
- **Saved playback options.** Normalization, loudness target, Pure / Lossless, skip silence, speed, pitch, and sound style are saved and restored when the app starts.

## 2.0

- **Auto-skip on error.** If a song cannot be played, the queue moves to the next one. Toggle: Settings > Sound & playback.
- **Haptic feedback.** A light vibration on play, pause, and skip. Toggle: Settings > Sound & playback.
- **Instrumental breaks in lyrics.** A music note fills from the bottom up during gaps of 6 seconds or more between lyric lines, and at the start of a track.

## 2.1

- **SponsorBlock.** Skips sponsor, intro, outro, and non-music segments in YouTube videos. Toggle: Settings > Integrations.
- **ListenBrainz scrobbling.** Sends listens after half the track (or four minutes) has played, and a now-playing update when a track starts. Needs a user token in Settings > Integrations. Last.fm is not included yet, since it needs an API key.
- **Accent color.** Default, Ocean, Mint, Amber, and Rose. Set in Settings > Appearance.
- **Language fix.** Indonesian is stored as "id", so the app switches language correctly.
- **Error details.** Playback errors show the error code and its cause on screen.

## 2.2

- **Build fix.** A stray `@Composable` annotation on the lyric row interface caused a compile error. It is removed.
- **Faster song start.** The mobile player clients run at the same time instead of one after another, each with an 8-second timeout.
- **Faster app start.** The player and audio cache are created on first playback. Explore loads only when its screen opens.
- **Less idle work.** Playback position updates every 1.5 seconds while paused, instead of every 450 ms.
