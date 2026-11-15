package com.aetherwave.data.repository

import com.aetherwave.data.local.dao.PlaylistDao
import com.aetherwave.data.local.dao.PlaylistWithTracks
import com.aetherwave.data.local.dao.TrackDao
import com.aetherwave.data.local.entity.PlaylistEntity
import com.aetherwave.data.local.entity.PlaylistTrackCrossRef
import com.aetherwave.data.local.entity.toDomain
import com.aetherwave.data.local.entity.toEntity
import com.aetherwave.data.remote.lrclib.LrcLibApi
import com.aetherwave.data.remote.lrclib.LrcLibTrackResponse
import com.aetherwave.data.remote.lrclib.LyricsParser
import com.aetherwave.data.source.local.MediaStoreAudioSource
import com.aetherwave.data.source.youtube.InnertubeClient
import com.aetherwave.domain.model.MusicHomeFeed
import com.aetherwave.domain.model.LyricsResult
import com.aetherwave.domain.model.MusicPlaylist
import com.aetherwave.domain.model.MusicSection
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import com.aetherwave.domain.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val innertubeClient: InnertubeClient,
    private val mediaStoreAudioSource: MediaStoreAudioSource,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val lyricsApi: LrcLibApi,
) : MusicRepository {

    override suspend fun getHomeFeed(): MusicHomeFeed {
        val feed = innertubeClient.homeFeed()
        suspend fun merge(tracks: List<MusicTrack>): List<MusicTrack> = tracks.map { fresh ->
            trackDao.insertTrackIfMissing(fresh.toEntity())
            val stored = trackDao.getTrack(fresh.id)
            if (stored == null) fresh else fresh.copy(
                isLiked = stored.isLiked,
                lastPlayedAt = stored.lastPlayedAt,
                isDownloaded = stored.isDownloaded,
                localFilePath = stored.localFilePath,
            )
        }
        return MusicHomeFeed(
            quickPicks = merge(feed.quickPicks),
            trending = merge(feed.trending),
            recommended = merge(feed.recommended),
        )
    }

    override suspend fun searchYouTubeMusic(query: String, limit: Int): List<MusicTrack> =
        innertubeClient.searchSongs(query, limit).map { fresh ->
            trackDao.insertTrackIfMissing(fresh.toEntity())
            val stored = trackDao.getTrack(fresh.id)
            if (stored == null) fresh else fresh.copy(
                isLiked = stored.isLiked,
                lastPlayedAt = stored.lastPlayedAt,
                isDownloaded = stored.isDownloaded,
                localFilePath = stored.localFilePath,
            )
        }

    /**
     * Radio / Up Next for a YouTube seed track. Local files have no YouTube seed, so they get an
     * empty list instead of a failed request.
     */
    override suspend fun getUpNext(track: MusicTrack, limit: Int): List<MusicTrack> {
        if (track.source != TrackSource.YOUTUBE || track.sourceId.isBlank()) return emptyList()
        return innertubeClient.upNext(track.sourceId, limit).map { fresh ->
            trackDao.insertTrackIfMissing(fresh.toEntity())
            val stored = trackDao.getTrack(fresh.id)
            if (stored == null) fresh else fresh.copy(
                isLiked = stored.isLiked,
                lastPlayedAt = stored.lastPlayedAt,
                isDownloaded = stored.isDownloaded,
                localFilePath = stored.localFilePath,
            )
        }
    }

    override suspend fun getExploreSections(): List<MusicSection> = innertubeClient.exploreSections()

    override fun observeMostPlayed(limit: Int): Flow<List<MusicTrack>> =
        trackDao.observeMostPlayed(limit.coerceIn(1, 100)).map { rows -> rows.map { it.toDomain() } }

    override suspend fun scanLocalAudio(): List<MusicTrack> = mediaStoreAudioSource.scanAudio()

    override fun observeLikedTracks(): Flow<List<MusicTrack>> =
        trackDao.observeLikedTracks().map { rows -> rows.map { it.toDomain() } }

    override fun observeRecentlyPlayed(limit: Int): Flow<List<MusicTrack>> =
        trackDao.observeRecentlyPlayed(limit.coerceIn(1, 500)).map { rows -> rows.map { it.toDomain() } }

    override fun observeDownloadedTracks(): Flow<List<MusicTrack>> =
        trackDao.observeDownloadedTracks().map { rows -> rows.map { it.toDomain() } }

    override fun observePlaylists(): Flow<List<MusicPlaylist>> =
        playlistDao.observePlaylistsWithTracks().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setLiked(track: MusicTrack, liked: Boolean) {
        trackDao.insertTrackIfMissing(track.toEntity())
        trackDao.setLiked(track.id, liked)
    }

    override suspend fun recordPlayback(track: MusicTrack, playedAt: Long) {
        trackDao.insertTrackIfMissing(track.toEntity())
        trackDao.recordPlay(track.id, playedAt.coerceAtLeast(0L))
    }

    override suspend fun createPlaylist(name: String, description: String?): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Playlist name must not be blank" }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        playlistDao.insertPlaylist(
            PlaylistEntity(
                id = id,
                name = normalizedName,
                description = description?.trim()?.takeIf(String::isNotEmpty),
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    override suspend fun renamePlaylist(playlistId: String, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Playlist name must not be blank" }
        require(playlistDao.getPlaylist(playlistId) != null) { "Playlist does not exist" }
        playlistDao.renamePlaylist(playlistId, normalizedName, System.currentTimeMillis())
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylistById(playlistId)
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: MusicTrack) {
        require(playlistDao.getPlaylist(playlistId) != null) { "Playlist does not exist" }
        trackDao.insertTrackIfMissing(track.toEntity())
        val position = playlistDao.nextTrackPosition(playlistId)
        playlistDao.insertTrackReference(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = track.id,
                position = position,
            ),
        )
        playlistDao.touchPlaylist(playlistId, System.currentTimeMillis())
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeTrack(playlistId, trackId)
        playlistDao.touchPlaylist(playlistId, System.currentTimeMillis())
    }

    override suspend fun getLyrics(track: MusicTrack): LyricsResult? {
        val durationSeconds = track.durationMs
            .takeIf { it in 1_000L..3_600_000L }
            ?.div(1_000.0)
        val exact = try {
            lyricsApi.getLyrics(
                trackName = track.title,
                artistName = track.artist,
                durationSeconds = durationSeconds,
                albumName = track.album,
            )
        } catch (error: HttpException) {
            if (error.code() == 404) null else throw error
        }
        if (exact != null) return exact.toDomainLyrics()

        val candidates = lyricsApi.searchLyrics(
            trackName = track.title,
            artistName = track.artist,
            albumName = track.album,
        )
        val bestMatch = candidates.minByOrNull { candidate ->
            val durationDifference = if (durationSeconds != null && candidate.duration != null) {
                abs(candidate.duration - durationSeconds)
            } else {
                10_000.0
            }
            val titlePenalty = if (candidate.trackName.equals(track.title, ignoreCase = true)) 0.0 else 5.0
            val artistPenalty = if (candidate.artistName.equals(track.artist, ignoreCase = true)) 0.0 else 5.0
            durationDifference + titlePenalty + artistPenalty
        }
        return bestMatch?.toDomainLyrics()
    }

    override suspend fun searchLyrics(query: String): List<LyricsResult> {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotEmpty()) { "Lyrics search query must not be blank" }
        return lyricsApi.searchLyrics(query = normalizedQuery).map { it.toDomainLyrics() }
    }

    private fun PlaylistWithTracks.toDomain(): MusicPlaylist = MusicPlaylist(
        id = playlist.id,
        name = playlist.name,
        description = playlist.description,
        createdAt = playlist.createdAt,
        updatedAt = playlist.updatedAt,
        tracks = tracks.map { it.toDomain() },
    )

    private fun LrcLibTrackResponse.toDomainLyrics(): LyricsResult = LyricsResult(
        id = id,
        trackName = trackName,
        artistName = artistName,
        albumName = albumName,
        durationSeconds = duration,
        plainLyrics = plainLyrics,
        syncedLines = LyricsParser.parse(syncedLyrics),
        instrumental = instrumental,
    )
}
