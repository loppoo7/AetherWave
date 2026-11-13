package com.aetherwave.domain.model

import androidx.compose.runtime.Immutable

@Immutable
enum class TrackSource {
    YOUTUBE,
    LOCAL,
}

@Immutable
data class MusicTrack(
    val id: String,
    val source: TrackSource,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
    val mediaUri: String? = null,
    val videoUrl: String? = null,
    val isLiked: Boolean = false,
    val lastPlayedAt: Long? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
)

@Immutable
data class MusicPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val tracks: List<MusicTrack>,
)
