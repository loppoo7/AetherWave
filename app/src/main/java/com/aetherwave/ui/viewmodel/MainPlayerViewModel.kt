package com.aetherwave.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.aetherwave.domain.model.LyricsResult
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.repository.MusicRepository
import com.aetherwave.playback.OfflineAudioDownloadManager
import com.aetherwave.playback.PlaybackController
import com.aetherwave.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
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

    init {
        playbackController.connect()
        viewModelScope.launch {
            playbackController.state.collect { playback ->
                mutableState.update {
                    it.copy(playback = playback, currentTrack = playback.currentMusicTrack)
                }
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
                            mutableState.update { it.copy(lyrics = null, isLyricsLoading = false, message = error.localizedMessage ?: "Lyrics unavailable") }
                        }
                    } else {
                        mutableState.update { it.copy(isLyricsLoading = false) }
                    }
                }
        }
    }

    fun play(track: MusicTrack) = playbackController.play(track)
    fun playQueue(tracks: List<MusicTrack>, startIndex: Int = 0) = playbackController.playMusicQueue(tracks, startIndex)
    fun togglePlayPause() = playbackController.playPause()
    fun next() = playbackController.seekToNext()
    fun previous() = playbackController.seekToPrevious()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun toggleShuffle() = playbackController.setShuffleEnabled(!state.value.playback.shuffleMode)
    fun cycleRepeat() {
        val current = state.value.playback.repeatMode
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        playbackController.setRepeatMode(next)
    }

    fun toggleLyrics() = mutableState.update { it.copy(showLyrics = !it.showLyrics) }

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
        if (track.source == com.aetherwave.domain.model.TrackSource.LOCAL) {
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
}
