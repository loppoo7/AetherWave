package com.aetherwave.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aetherwave.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIfMissing(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrack(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET isLiked = :liked WHERE id = :trackId")
    suspend fun setLiked(trackId: String, liked: Boolean)

    @Query("UPDATE tracks SET lastPlayedAt = :playedAt WHERE id = :trackId")
    suspend fun updateLastPlayedAt(trackId: String, playedAt: Long)

    @Query("UPDATE tracks SET isDownloaded = :downloaded, localFilePath = :filePath WHERE id = :trackId")
    suspend fun updateDownloadState(trackId: String, downloaded: Boolean, filePath: String?)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: String)
}
