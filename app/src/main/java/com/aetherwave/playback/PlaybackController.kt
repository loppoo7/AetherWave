package com.aetherwave.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aetherwave.data.source.youtube.YouTubeTrack
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI-facing playback facade. All MediaController and ExoPlayer access happens on the main thread;
 * stream resolution and networking run on background threads inside Media3 and the extractors.
 *
 * Recovery layers:
 *  1. A dropped service connection is detected through MediaController.Listener and rebuilt on the
 *     next command instead of reusing a dead controller.
 *  2. A failed playback is re-prepared once, which re-runs stream resolution (Innertube, then NewPipe).
 *  3. Anything still failing is shown as an error message; it never escapes as an exception.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackPrefs.load(context))
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private val mutableQueue = MutableStateFlow<List<MusicTrack>>(emptyList())
    /** Live snapshot of the player's timeline, in playback order. */
    val queue: StateFlow<List<MusicTrack>> = mutableQueue.asStateFlow()
    private val trackRegistry = ConcurrentHashMap<String, MusicTrack>()
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(context) }

    @Volatile private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pollingJob: Job? = null
    private var sleepJob: Job? = null
    private var retryMediaId: String? = null
    private var retryCount = 0
    private var appliedVolume = -1f

    @Synchronized
    fun connect() {
        if (controller != null || controllerFuture != null) return
        try {
            val token = SessionToken(context, ComponentName(context, AetherWavePlaybackService::class.java))
            val future = MediaController.Builder(context, token)
                .setListener(controllerListener)
                .buildAsync()
            controllerFuture = future
            future.addListener({ onControllerReady(future) }, mainExecutor)
        } catch (error: Exception) {
            controllerFuture = null
            publishError(error, "Unable to connect playback service")
        }
    }

    fun play(track: MusicTrack): Unit = startQueue(listOf(track), 0, "Unable to start this track")

    fun play(track: YouTubeTrack): Unit = play(track.toMusicTrack())

    fun playMusicQueue(tracks: List<MusicTrack>, startIndex: Int = 0) =
        startQueue(tracks, startIndex, "Unable to start this queue")

    fun playQueue(tracks: List<YouTubeTrack>, startIndex: Int = 0) =
        playMusicQueue(tracks.map { it.toMusicTrack() }, startIndex)

    /**
     * Appends radio / Up Next tracks to the end of the live queue, skipping any track already queued.
     * Items that cannot be turned into a media item are dropped rather than failing the whole batch.
     */
    fun appendTracks(tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) return
        tracks.forEach { trackRegistry[it.id] = it }
        scope.launch {
            val active = controller?.takeIf { it.isConnected } ?: return@launch
            val queued = (0 until active.mediaItemCount).mapTo(HashSet()) { active.getMediaItemAt(it).mediaId }
            val fresh = tracks
                .filter { it.id !in queued }
                .mapNotNull { track -> runCatching { TrackMediaItem.from(track) }.getOrNull() }
            if (fresh.isNotEmpty()) active.addMediaItems(fresh)
        }
    }

    fun updateTrackMetadata(track: MusicTrack) {
        trackRegistry[track.id] = track
        controller?.takeIf { it.isConnected }?.let(::publish)
    }

    fun playPause(): Unit = withController { if (it.isPlaying) it.pause() else it.play() }
    fun pause(): Unit = withController { it.pause() }
    fun seekTo(positionMs: Long): Unit = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }
    fun seekToNext(): Unit = withController { it.seekToNextMediaItem() }
    fun seekToPrevious(): Unit = withController { it.seekToPreviousMediaItem() }
    fun setRepeatMode(mode: Int): Unit = withController { it.repeatMode = mode }
    fun setShuffleEnabled(enabled: Boolean): Unit = withController { it.shuffleModeEnabled = enabled }

    fun setPlaybackSpeed(speed: Float): Unit = applyPlaybackParameters(speed, state.value.pitch, null)

    fun setPitch(pitch: Float): Unit = applyPlaybackParameters(state.value.playbackSpeed, pitch, null)

    fun setSoundStyle(style: SoundStyle) {
        applyPlaybackParameters(style.speed, style.pitch, style)
    }

    fun setLoudnessLevel(level: LoudnessLevel) {
        scope.launch {
            mutableState.update { it.copy(loudnessLevel = level) }
            appliedVolume = -1f
            PlaybackPrefs.save(context, mutableState.value)
        }
    }

    fun setNormalizeVolume(enabled: Boolean) {
        scope.launch {
            mutableState.update { it.copy(normalizeVolume = enabled) }
            appliedVolume = -1f
            PlaybackPrefs.save(context, mutableState.value)
        }
    }

    /**
     * Applies the loudness correction for the current track. Local files and tracks without a
     * reported loudness keep full volume, so their output stays unchanged.
     */
    private fun applyNormalization(mediaId: String?, track: MusicTrack?) {
        val target = when {
            !mutableState.value.normalizeVolume || mediaId == null -> 1f
            track?.source == TrackSource.LOCAL -> 1f
            else -> LoudnessRegistry.loudnessFor(mediaId.removePrefix("youtube:"))
                ?.let { LoudnessRegistry.volumeFor(it, mutableState.value.loudnessLevel) }
                ?: 1f
        }
        if (target != appliedVolume) {
            appliedVolume = target
            exoPlayer.volume = target
        }
    }

    fun setPureMode(enabled: Boolean) {
        scope.launch {
            mutableState.update { it.copy(pureMode = enabled) }
            PlaybackPrefs.save(context, mutableState.value)
        }
    }

    /**
     * Makes ExoPlayer match the user's settings, except for local files in Pure mode, which play
     * with neutral speed and pitch and without silence skipping. The user's values stay in state,
     * so they return as soon as a YouTube track plays again.
     */
    private fun syncAudioEffects(track: MusicTrack?) {
        val settings = mutableState.value
        val bypass = settings.pureMode && track?.source == TrackSource.LOCAL
        val wantSpeed = if (bypass) 1f else settings.playbackSpeed
        val wantPitch = if (bypass) 1f else settings.pitch
        val wantSkipSilence = !bypass && settings.skipSilence
        val current = exoPlayer.playbackParameters
        if (current.speed != wantSpeed || current.pitch != wantPitch) {
            exoPlayer.setPlaybackParameters(PlaybackParameters(wantSpeed, wantPitch))
        }
        if (exoPlayer.skipSilenceEnabled != wantSkipSilence) {
            exoPlayer.setSkipSilenceEnabled(wantSkipSilence)
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        scope.launch {
            exoPlayer.setSkipSilenceEnabled(enabled)
            mutableState.update { it.copy(skipSilence = enabled) }
            PlaybackPrefs.save(context, mutableState.value)
        }
    }

    /** Pauses playback after [minutes]. Replaces any timer that is already running. */
    fun setSleepTimer(minutes: Int) {
        cancelSleepJob()
        val durationMs = minutes.coerceIn(1, 240) * 60_000L
        mutableState.update {
            it.copy(sleepTimerEndsAt = System.currentTimeMillis() + durationMs, sleepAtEndOfTrack = false)
        }
        sleepJob = scope.launch {
            val fadeMs = SLEEP_FADE_MS.coerceAtMost(durationMs)
            delay(durationMs - fadeMs)
            fadeOutVolume(fadeMs)
            controller?.takeIf { it.isConnected }?.pause()
            // Forget the faded level, so the next publish restores the normal volume on resume.
            appliedVolume = -1f
            mutableState.update { it.copy(sleepTimerEndsAt = null, sleepAtEndOfTrack = false) }
        }
    }

    /** Pauses when the current track finishes naturally. */
    fun setSleepAtEndOfTrack() {
        cancelSleepJob()
        mutableState.update { it.copy(sleepTimerEndsAt = null, sleepAtEndOfTrack = true) }
    }

    fun cancelSleepTimer() {
        cancelSleepJob()
        mutableState.update { it.copy(sleepTimerEndsAt = null, sleepAtEndOfTrack = false) }
    }

    /** Ramps the output volume down over [durationMs], one step per second. */
    private suspend fun fadeOutVolume(durationMs: Long) {
        val start = if (appliedVolume > 0f) appliedVolume else 1f
        val steps = (durationMs / FADE_STEP_MS).toInt().coerceAtLeast(1)
        repeat(steps) { step ->
            exoPlayer.volume = start * (1f - (step + 1f) / steps)
            delay(FADE_STEP_MS)
        }
    }

    private fun cancelSleepJob() {
        sleepJob?.cancel()
        sleepJob = null
    }

    private fun applyPlaybackParameters(speed: Float, pitch: Float, style: SoundStyle?) {
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        scope.launch {
            exoPlayer.setPlaybackParameters(PlaybackParameters(safeSpeed, safePitch))
            mutableState.update {
                it.copy(playbackSpeed = safeSpeed, pitch = safePitch, soundStyle = style)
            }
            PlaybackPrefs.save(context, mutableState.value)
        }
    }

    private fun startQueue(tracks: List<MusicTrack>, startIndex: Int, failureMessage: String) {
        if (tracks.isEmpty()) return
        tracks.forEach { trackRegistry[it.id] = it }
        scope.launch {
            try {
                val active = awaitController()
                val built = tracks.mapIndexedNotNull { index, track ->
                    runCatching { TrackMediaItem.from(track) }.getOrNull()?.let { index to it }
                }
                if (built.isEmpty()) throw IllegalStateException("No playable tracks in this queue")
                val start = built.indexOfFirst { it.first >= startIndex }.let { if (it < 0) 0 else it }
                retryMediaId = null
                retryCount = 0
                active.setMediaItems(built.map { it.second }, start, 0L)
                active.prepare()
                active.play()
                mutableState.update { it.copy(errorMessage = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publishError(error, failureMessage)
            }
        }
    }

    /** Waits briefly for a fresh connection. Throws if the service cannot be reached. */
    private suspend fun awaitController(): MediaController {
        connect()
        val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            controller?.takeIf { it.isConnected }?.let { return it }
            if (controllerFuture == null) break
            delay(50)
        }
        throw IllegalStateException(mutableState.value.errorMessage ?: "Playback service is unavailable")
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int): Unit = withController {
        if (fromIndex in 0 until it.mediaItemCount && toIndex in 0 until it.mediaItemCount) {
            it.moveMediaItem(fromIndex, toIndex)
        }
    }

    fun removeQueueItem(index: Int): Unit = withController {
        if (index in 0 until it.mediaItemCount) it.removeMediaItem(index)
    }

    fun jumpToQueueItem(index: Int): Unit = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekTo(index, 0L)
            it.play()
        }
    }

    private fun refreshQueue(player: Player) {
        mutableQueue.value = (0 until player.mediaItemCount).mapNotNull { index ->
            val item = player.getMediaItemAt(index)
            trackRegistry[item.mediaId] ?: item.toMusicTrackOrNull()
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.takeIf { it.isConnected }?.let(action)
    }

    private fun onControllerReady(future: ListenableFuture<MediaController>) {
        runCatching { future.get() }
            .onSuccess { ready ->
                controller = ready
                ready.addListener(playerListener)
                publish(ready)
                refreshQueue(ready)
                startPositionUpdates()
            }
            .onFailure { error ->
                controllerFuture = null
                publishError(error, "Unable to connect playback service")
            }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(disconnected: MediaController) {
            scope.launch { handleDisconnected(disconnected) }
        }
    }

    private fun handleDisconnected(disconnected: MediaController) {
        if (controller !== disconnected) return
        disconnected.removeListener(playerListener)
        pollingJob?.cancel()
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mutableState.update { it.copy(isPlaying = false, isBuffering = false) }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) refreshQueue(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                retryMediaId = null
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retryMediaId = null
            retryCount = 0
            // "End of track": the natural advance to the next item is blocked by pausing here.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && state.value.sleepAtEndOfTrack) {
                controller?.takeIf { it.isConnected }?.pause()
                cancelSleepTimer()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val active = controller
            val mediaId = active?.currentMediaItem?.mediaId
            if (active == null || mediaId == null) {
                publishError(error, "Playback failed")
                return
            }
            if (mediaId != retryMediaId) {
                retryMediaId = mediaId
                retryCount = 0
            }
            if (retryCount < MAX_RETRIES_PER_TRACK) {
                retryCount++
                // Re-preparing re-runs stream resolution, so an expired URL or a bad Innertube
                // response gets a fresh NewPipe lookup instead of failing the track outright.
                active.prepare()
                return
            }
            publishError(error, "This track could not be played. Try another result.")
        }
    }

    private fun startPositionUpdates() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                controller?.takeIf { it.isConnected }?.let(::publish)
                delay(450)
            }
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val mediaId = item?.mediaId
        val musicTrack = if (item == null || mediaId == null) {
            null
        } else {
            trackRegistry[mediaId] ?: item.toMusicTrackOrNull()
        }
        applyNormalization(mediaId, musicTrack)
        syncAudioEffects(musicTrack)
        mutableState.update { previous ->
            previous.copy(
                isPlaying = player.isPlaying,
                currentTrack = if (item != null && mediaId != null) item.mediaMetadata.toCurrentTrack(mediaId) else null,
                currentMusicTrack = musicTrack,
                position = player.currentPosition.coerceAtLeast(0L),
                duration = player.duration.takeIf { it >= 0L } ?: 0L,
                bufferPosition = player.bufferedPosition.coerceAtLeast(0L),
                repeatMode = player.repeatMode,
                shuffleMode = player.shuffleModeEnabled,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                queueIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                queueSize = player.mediaItemCount,
            )
        }
    }

    private fun publishError(error: Throwable, fallback: String) {
        mutableState.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = error.localizedMessage?.takeIf(String::isNotBlank) ?: fallback,
            )
        }
    }

    private fun MediaItem.toMusicTrackOrNull(): MusicTrack? {
        val config = localConfiguration ?: return null
        val uri = config.uri
        val metadata = mediaMetadata
        val id = mediaId
        val isYoutube = id.startsWith("youtube:")
        val videoUrl = if (uri.scheme == "aetherwave") uri.getQueryParameter("url")?.let { Uri.decode(it) } else null
        return MusicTrack(
            id = id,
            source = if (isYoutube) TrackSource.YOUTUBE else TrackSource.LOCAL,
            sourceId = id.substringAfter(':', id),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString(),
            artworkUri = metadata.artworkUri?.toString(),
            mediaUri = if (isYoutube || uri.scheme == "file") null else uri.toString(),
            videoUrl = videoUrl,
            isDownloaded = isYoutube && uri.scheme == "file",
            localFilePath = uri.takeIf { it.scheme == "file" }?.path,
        )
    }

    private fun YouTubeTrack.toMusicTrack(): MusicTrack = MusicTrack(
        id = "youtube:$id",
        source = TrackSource.YOUTUBE,
        sourceId = id,
        title = title,
        artist = artist,
        durationMs = durationSeconds.coerceAtLeast(0L) * 1_000L,
        artworkUri = thumbnailUrl.takeIf(String::isNotBlank),
        videoUrl = videoUrl,
    )

    @Synchronized
    fun disconnect() {
        pollingJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val SLEEP_FADE_MS = 60_000L
        const val FADE_STEP_MS = 1_000L
        const val MAX_RETRIES_PER_TRACK = 1
    }
}
