package com.aetherwave.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.aetherwave.data.source.youtube.YouTubeTrack
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import java.io.File

@UnstableApi
object TrackMediaItem {
    fun from(track: YouTubeTrack): MediaItem {
        val uri = youtubeResolverUri(track.videoUrl)
        return MediaItem.Builder()
            .setMediaId("youtube:${track.id}")
            .setUri(uri)
            // The custom aetherwave:// URI has no file extension; declare progressive audio
            // explicitly so DefaultMediaSourceFactory does not fail to infer a source type.
            .setMimeType("audio/mp4")
            .setCustomCacheKey(youtubeCacheKey(track.id))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnailUrl.takeIf(String::isNotBlank)?.let(Uri::parse))
                    .build(),
            )
            .build()
    }

    fun from(track: MusicTrack): MediaItem {
        val savedFile = track.localFilePath?.let { File(it) }?.takeIf { it.isFile && it.length() > 0L }
        val uri = when {
            savedFile != null -> Uri.fromFile(savedFile)
            track.source == TrackSource.YOUTUBE -> youtubeResolverUri(canonicalYouTubeUrl(track))
            else -> Uri.parse(
                requireNotNull(track.mediaUri) { "Local track is missing its MediaStore content URI" },
            )
        }

        val builder = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .apply {
                if (track.source == TrackSource.YOUTUBE && savedFile == null) {
                    // The resolver swaps this URI for a signed direct audio URL at load time.
                    setMimeType("audio/mp4")
                }
            }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.artworkUri?.let(Uri::parse))
                    .build(),
            )
        if (track.source == TrackSource.YOUTUBE && savedFile == null) {
            builder.setCustomCacheKey(youtubeCacheKey(track.sourceId))
        }
        return builder.build()
    }

    /**
     * Search results from older database rows may not contain videoUrl. Recover it from the
     * stable YouTube video ID instead of throwing while the user taps a search result.
     */
    private fun canonicalYouTubeUrl(track: MusicTrack): String {
        val candidate = track.videoUrl?.takeIf { it.startsWith("https://") }
            ?: "https://www.youtube.com/watch?v=${track.sourceId}"
        val parsed = Uri.parse(candidate)
        val videoId = parsed.getQueryParameter("v")
            ?: candidate.substringAfterLast('/').substringBefore('?')
        require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) {
            "This YouTube result has an invalid video ID"
        }
        return "https://www.youtube.com/watch?v=$videoId"
    }

    fun youtubeCacheKey(sourceId: String): String = "youtube:$sourceId"

    private fun youtubeResolverUri(videoUrl: String): Uri = Uri.Builder()
        .scheme("aetherwave")
        .authority("track")
        .appendQueryParameter("url", videoUrl)
        .build()
}
