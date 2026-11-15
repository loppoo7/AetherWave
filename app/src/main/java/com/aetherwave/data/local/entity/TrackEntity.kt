package com.aetherwave.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["isLiked"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["source", "sourceId"], unique = true),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkUri: String?,
    val mediaUri: String?,
    val videoUrl: String?,
    val isLiked: Boolean = false,
    val lastPlayedAt: Long? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    @ColumnInfo(defaultValue = "0") val playCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

fun MusicTrack.toEntity(): TrackEntity = TrackEntity(
    id = id,
    source = source.name,
    sourceId = sourceId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    artworkUri = artworkUri,
    mediaUri = mediaUri,
    videoUrl = videoUrl,
    isLiked = isLiked,
    lastPlayedAt = lastPlayedAt,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
)

fun TrackEntity.toDomain(): MusicTrack = MusicTrack(
    id = id,
    source = runCatching { TrackSource.valueOf(source) }.getOrDefault(TrackSource.LOCAL),
    sourceId = sourceId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    artworkUri = artworkUri,
    mediaUri = mediaUri,
    videoUrl = videoUrl,
    isLiked = isLiked,
    lastPlayedAt = lastPlayedAt,
    isDownloaded = isDownloaded,
    localFilePath = localFilePath,
    playCount = playCount,
)
