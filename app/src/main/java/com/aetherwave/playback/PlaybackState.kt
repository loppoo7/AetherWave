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
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val playbackSpeed: Float = 1f,
    val pitch: Float = 1f,
    val skipSilence: Boolean = false,
    val sleepTimerEndsAt: Long? = null,
    val sleepAtEndOfTrack: Boolean = false,
    val normalizeVolume: Boolean = true,
    val loudnessLevel: LoudnessLevel = LoudnessLevel.BALANCED,
    /** Null when speed or pitch were set by hand, which means the look is custom. */
    val soundStyle: SoundStyle? = SoundStyle.NORMAL,
    /** Plays local files without speed, pitch, silence skipping, or normalization. */
    val pureMode: Boolean = false,
    /** Skip to the next track when one cannot be played. */
    val autoSkipOnError: Boolean = true,
    /** Light vibration on play, pause, and skip. */
    val hapticsEnabled: Boolean = true,
    /** Skip sponsor, intro, outro, and non-music segments from SponsorBlock. */
    val sponsorBlockEnabled: Boolean = true,
    /** Send listens to ListenBrainz. Needs [listenBrainzToken]. */
    val scrobbleEnabled: Boolean = false,
    val listenBrainzToken: String = "",
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
