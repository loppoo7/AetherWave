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
            track.source == TrackSource.YOUTUBE -> youtubeResolverUri(
                requireNotNull(track.videoUrl) { "YouTube track is missing its canonical video URL" },
            )
            else -> Uri.parse(
                requireNotNull(track.mediaUri) { "Local track is missing its MediaStore content URI" },
            )
        }

        val builder = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
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

    fun youtubeCacheKey(sourceId: String): String = "youtube:$sourceId"

    private fun youtubeResolverUri(videoUrl: String): Uri = Uri.Builder()
        .scheme("aetherwave")
        .authority("track")
        .appendQueryParameter("url", videoUrl)
        .build()
}
