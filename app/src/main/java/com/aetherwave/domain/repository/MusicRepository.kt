package com.aetherwave.domain.repository

import com.aetherwave.domain.model.LyricsResult
import com.aetherwave.domain.model.MusicPlaylist
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.MusicSection
import com.aetherwave.domain.model.MusicHomeFeed
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun getHomeFeed(): MusicHomeFeed
    suspend fun searchYouTubeMusic(query: String, limit: Int = 25): List<MusicTrack>
    suspend fun getUpNext(track: MusicTrack, limit: Int = 25): List<MusicTrack>
    suspend fun getExploreSections(): List<MusicSection>
    fun observeMostPlayed(limit: Int = 20): Flow<List<MusicTrack>>
    suspend fun scanLocalAudio(): List<MusicTrack>

    fun observeLikedTracks(): Flow<List<MusicTrack>>
    fun observeRecentlyPlayed(limit: Int = 100): Flow<List<MusicTrack>>
    fun observeDownloadedTracks(): Flow<List<MusicTrack>>
    fun observePlaylists(): Flow<List<MusicPlaylist>>

    suspend fun setLiked(track: MusicTrack, liked: Boolean)
    suspend fun recordPlayback(track: MusicTrack, playedAt: Long = System.currentTimeMillis())
    suspend fun createPlaylist(name: String, description: String? = null): String
    suspend fun renamePlaylist(playlistId: String, name: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTrackToPlaylist(playlistId: String, track: MusicTrack)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    suspend fun getLyrics(track: MusicTrack): LyricsResult?
    suspend fun searchLyrics(query: String): List<LyricsResult>
}
