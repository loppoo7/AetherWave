package com.aetherwave.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.aetherwave.domain.model.LyricsResult
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import com.aetherwave.domain.repository.MusicRepository
import com.aetherwave.playback.OfflineAudioDownloadManager
import com.aetherwave.playback.PlaybackController
import com.aetherwave.playback.LoudnessLevel
import com.aetherwave.playback.PlaybackState
import com.aetherwave.playback.SoundStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class MainPlayerUiState(
    val playback: PlaybackState = PlaybackState(),
    val currentTrack: MusicTrack? = null,
    val lyrics: LyricsResult? = null,
    val isLyricsLoading: Boolean = false,
    val showLyrics: Boolean = false,
    val isDownloading: Boolean = false,
    val message: String? = null,
    val queue: List<MusicTrack> = emptyList(),
)

@OptIn(UnstableApi::class)
@HiltViewModel
class MainPlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val musicRepository: MusicRepository,
    private val downloadManager: OfflineAudioDownloadManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MainPlayerUiState())
    val state: StateFlow<MainPlayerUiState> = mutableState.asStateFlow()

    /** Seeds whose radio has already been requested, so each seed is fetched once. */
    private val radioSeedIds = mutableSetOf<String>()

    init {
        playbackController.connect()
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                mutableState.update {
                    it.copy(playback = playback, currentTrack = playback.currentMusicTrack)
                }
                maybeExtendRadio(playback)
            }
        }
        viewModelScope.launch {
            playbackController.queue.collect { queue ->
                mutableState.update { it.copy(queue = queue) }
            }
        }
        viewModelScope.launch {
            playbackController.state
                .map { it.currentMusicTrack }
                .distinctUntilChangedBy { it?.id }
                .collectLatest { track ->
                    mutableState.update { it.copy(lyrics = null, isLyricsLoading = track != null, message = null) }
                    if (track != null) {
                        try {
                            musicRepository.recordPlayback(track)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Lyrics lookup remains useful even if history persistence fails.
                        }
                        try {
                            val lyrics = musicRepository.getLyrics(track)
                            mutableState.update { it.copy(lyrics = lyrics, isLyricsLoading = false, message = null) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            mutableState.update {
                                it.copy(lyrics = null, isLyricsLoading = false, message = error.localizedMessage ?: "Lyrics unavailable")
                            }
                        }
                    } else {
                        mutableState.update { it.copy(isLyricsLoading = false) }
                    }
                }
        }
    }

    /**
     * Auto-radio: when the live queue has fewer than [RADIO_THRESHOLD] items left, fetch Up Next
     * for the current YouTube track and append it. A single song therefore starts an endless
     * related queue, and a finished search or home queue continues with similar music.
     */
    private fun maybeExtendRadio(playback: PlaybackState) {
        val seed = playback.currentMusicTrack?.takeIf { it.source == TrackSource.YOUTUBE } ?: return
        if (playback.queueSize == 0 || playback.queueSize - playback.queueIndex > RADIO_THRESHOLD) return
        if (!radioSeedIds.add(seed.id)) return
        viewModelScope.launch {
            try {
                playbackController.appendTracks(musicRepository.getUpNext(seed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(message = "Radio suggestions are unavailable right now") }
            }
        }
    }

    fun play(track: MusicTrack): Unit = playbackController.play(track)
    fun playQueue(tracks: List<MusicTrack>, startIndex: Int = 0) = playbackController.playMusicQueue(tracks, startIndex)
    fun togglePlayPause(): Unit = playbackController.playPause()
    fun next(): Unit = playbackController.seekToNext()
    fun previous(): Unit = playbackController.seekToPrevious()
    fun seekTo(positionMs: Long): Unit = playbackController.seekTo(positionMs)
    fun toggleShuffle(): Unit = playbackController.setShuffleEnabled(!state.value.playback.shuffleMode)

    fun cycleRepeat() {
        val next = when (state.value.playback.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playbackController.setRepeatMode(next)
    }

    fun setPlaybackSpeed(speed: Float): Unit = playbackController.setPlaybackSpeed(speed)
    fun setPitch(pitch: Float): Unit = playbackController.setPitch(pitch)
    fun setSkipSilence(enabled: Boolean): Unit = playbackController.setSkipSilence(enabled)
    fun setNormalizeVolume(enabled: Boolean): Unit = playbackController.setNormalizeVolume(enabled)
    fun setSoundStyle(style: SoundStyle): Unit = playbackController.setSoundStyle(style)
    fun setLoudnessLevel(level: LoudnessLevel): Unit = playbackController.setLoudnessLevel(level)
    fun setPureMode(enabled: Boolean): Unit = playbackController.setPureMode(enabled)
    fun setAutoSkipOnError(enabled: Boolean): Unit = playbackController.setAutoSkipOnError(enabled)
    fun setHapticsEnabled(enabled: Boolean): Unit = playbackController.setHapticsEnabled(enabled)
    fun setSponsorBlockEnabled(enabled: Boolean): Unit = playbackController.setSponsorBlockEnabled(enabled)
    fun setScrobbleEnabled(enabled: Boolean): Unit = playbackController.setScrobbleEnabled(enabled)
    fun setListenBrainzToken(token: String): Unit = playbackController.setListenBrainzToken(token)
    fun setSleepTimer(minutes: Int): Unit = playbackController.setSleepTimer(minutes)
    fun setSleepAtEndOfTrack(): Unit = playbackController.setSleepAtEndOfTrack()
    fun cancelSleepTimer(): Unit = playbackController.cancelSleepTimer()

    fun toggleLyrics(): Unit = mutableState.update { it.copy(showLyrics = !it.showLyrics) }

    fun moveQueueItem(fromIndex: Int, toIndex: Int): Unit = playbackController.moveQueueItem(fromIndex, toIndex)
    fun removeQueueItem(index: Int): Unit = playbackController.removeQueueItem(index)
    fun jumpToQueueItem(index: Int): Unit = playbackController.jumpToQueueItem(index)

    fun toggleLike() {
        val track = state.value.currentTrack ?: return
        val liked = !track.isLiked
        viewModelScope.launch {
            try {
                musicRepository.setLiked(track, liked)
                val updated = track.copy(isLiked = liked)
                playbackController.updateTrackMetadata(updated)
                mutableState.update { it.copy(currentTrack = updated, message = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(message = error.localizedMessage ?: "Unable to update favorite") }
            }
        }
    }

    fun downloadCurrentTrack() {
        val track = state.value.currentTrack ?: return
        if (track.isDownloaded || state.value.isDownloading) return
        if (track.source == TrackSource.LOCAL) {
            mutableState.update { it.copy(message = "This local file is already available on your device") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isDownloading = true, message = null) }
            try {
                val file = downloadManager.download(track)
                val updated = track.copy(isDownloaded = true, localFilePath = file.absolutePath)
                playbackController.updateTrackMetadata(updated)
                mutableState.update { it.copy(currentTrack = updated, message = "Saved for offline playback") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(message = error.localizedMessage ?: "Download failed") }
            } finally {
                mutableState.update { it.copy(isDownloading = false) }
            }
        }
    }

    private companion object {
        const val RADIO_THRESHOLD = 2
    }
}
