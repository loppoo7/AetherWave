package com.aetherwave.playback

import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.aetherwave.domain.model.MusicTrack

@Immutable
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: CurrentTrack? = null,
    val currentMusicTrack: MusicTrack? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val bufferPosition: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleMode: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
)

@Immutable
data class CurrentTrack(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String?,
)

internal fun MediaMetadata.toCurrentTrack(mediaId: String): CurrentTrack = CurrentTrack(
    mediaId = mediaId,
    title = title?.toString().orEmpty(),
    artist = artist?.toString().orEmpty(),
    artworkUri = artworkUri?.toString(),
)
