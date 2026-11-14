package com.aetherwave.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aetherwave.data.source.youtube.YouTubeTrack
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private val trackRegistry = ConcurrentHashMap<String, MusicTrack>()

    @Volatile private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pollingJob: kotlinx.coroutines.Job? = null

    @Synchronized
    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, AetherWavePlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { mediaController ->
                controller = mediaController
                mediaController.addListener(playerListener)
                publish(mediaController)
                startPositionUpdates()
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(errorMessage = error.message ?: "Unable to connect playback service")
                controllerFuture = null
            }
        }, java.util.concurrent.Executor { command -> scope.launch { command.run() } })
    }

    fun play(track: YouTubeTrack) {
        val musicTrack = MusicTrack(
            id = "youtube:${track.id}",
            source = TrackSource.YOUTUBE,
            sourceId = track.id,
            title = track.title,
            artist = track.artist,
            durationMs = track.durationSeconds.coerceAtLeast(0L) * 1_000L,
            artworkUri = track.thumbnailUrl.takeIf(String::isNotBlank),
            videoUrl = track.videoUrl,
        )
        play(musicTrack)
    }

    fun play(track: MusicTrack) {
        trackRegistry[track.id] = track
        connect()
        scope.launch {
            try {
                val active = awaitController() ?: throw IllegalStateException("Playback service is unavailable")
                val item = TrackMediaItem.from(track)
                active.setMediaItem(item)
                active.prepare()
                active.play()
                mutableState.value = mutableState.value.copy(errorMessage = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.localizedMessage ?: "Unable to start this track",
                )
            }
        }
    }

    fun playMusicQueue(tracks: List<MusicTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        tracks.forEach { trackRegistry[it.id] = it }
        connect()
        scope.launch {
            try {
                val active = awaitController() ?: throw IllegalStateException("Playback service is unavailable")
                val mediaItems = tracks.map(TrackMediaItem::from)
                require(mediaItems.isNotEmpty()) { "No playable tracks in this queue" }
                active.setMediaItems(mediaItems, startIndex.coerceIn(mediaItems.indices), 0L)
                active.prepare()
                active.play()
                mutableState.value = mutableState.value.copy(errorMessage = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.localizedMessage ?: "Unable to start this queue",
                )
            }
        }
    }

    fun playQueue(tracks: List<YouTubeTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val converted = tracks.map { track ->
            MusicTrack(
                id = "youtube:${track.id}", source = TrackSource.YOUTUBE, sourceId = track.id,
                title = track.title, artist = track.artist,
                durationMs = track.durationSeconds.coerceAtLeast(0L) * 1_000L,
                artworkUri = track.thumbnailUrl.takeIf(String::isNotBlank), videoUrl = track.videoUrl,
            )
        }
        playMusicQueue(converted, startIndex)
    }

    fun updateTrackMetadata(track: MusicTrack) {
        trackRegistry[track.id] = track
        controller?.let(::publish)
    }

    fun playPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun pause() { controller?.pause() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)) }
    fun seekToNext() { controller?.seekToNextMediaItem() }
    fun seekToPrevious() { controller?.seekToPreviousMediaItem() }
    fun setRepeatMode(mode: Int) { controller?.repeatMode = mode }
    fun setShuffleEnabled(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }

    private suspend fun awaitController(): MediaController? {
        repeat(50) {
            controller?.let { return it }
            delay(100)
        }
        return controller
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
        override fun onPlayerError(error: PlaybackException) {
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = error.message ?: "Playback failed",
            )
        }
    }

    private fun startPositionUpdates() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                controller?.let(::publish)
                delay(450)
            }
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val mediaId = item?.mediaId
        val musicTrack = if (mediaId == null) null else trackRegistry[mediaId] ?: item.toMusicTrackOrNull()
        mutableState.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentTrack = if (item != null && mediaId != null) metadata?.toCurrentTrack(mediaId) else null,
            currentMusicTrack = musicTrack,
            position = player.currentPosition.coerceAtLeast(0L),
            duration = player.duration.takeIf { it >= 0L } ?: 0L,
            bufferPosition = player.bufferedPosition.coerceAtLeast(0L),
            repeatMode = player.repeatMode,
            shuffleMode = player.shuffleModeEnabled,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            errorMessage = mutableState.value.errorMessage,
        )
    }

    private fun androidx.media3.common.MediaItem.toMusicTrackOrNull(): MusicTrack? {
        val config = localConfiguration ?: return null
        val uri = config.uri
        val metadata = mediaMetadata
        val isYoutube = mediaId.startsWith("youtube:")
        val videoUrl = if (uri.scheme == "aetherwave") uri.getQueryParameter("url")?.let { Uri.decode(it) } else null
        return MusicTrack(
            id = mediaId,
            source = if (isYoutube) TrackSource.YOUTUBE else TrackSource.LOCAL,
            sourceId = mediaId.substringAfter(':', mediaId),
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

    @Synchronized
    fun disconnect() {
        pollingJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}
