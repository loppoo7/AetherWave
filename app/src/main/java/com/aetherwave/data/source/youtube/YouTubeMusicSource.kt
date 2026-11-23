package com.aetherwave.data.source.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicSource @Inject constructor(
    private val downloader: NewPipeOkHttpDownloader,
) {
    @Volatile private var initialized = false

    @Synchronized
    private fun ensureInitialized() {
        if (!initialized) {
            NewPipe.init(downloader)
            initialized = true
        }
    }

    suspend fun searchTracks(query: String, limit: Int = 20): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Search query must not be blank" }
        ensureInitialized()
        try {
            val extractor: SearchExtractor = ServiceList.YouTube.getSearchExtractor(query, listOf("music_songs"), null)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .take(limit.coerceIn(1, 50))
                .map { item ->
                    YouTubeTrack(
                        id = item.url.substringAfter("v=", item.url.substringAfterLast('/')),
                        title = item.name,
                        artist = item.uploaderName,
                        durationSeconds = item.duration,
                        thumbnailUrl = item.thumbnails.maxByOrNull(Image::getHeight)?.url.orEmpty(),
                        videoUrl = item.url,
                    )
                }
        } catch (error: Exception) {
            throw YouTubeExtractionException("Unable to search YouTube Music", error)
        }
    }

    suspend fun getBestAudioStreamUrl(videoUrl: String): ResolvedYouTubeStream = withContext(Dispatchers.IO) {
        require(videoUrl.startsWith("https://")) { "A valid YouTube URL is required" }
        ensureInitialized()
        try {
            // Build the link from the video ID. Passing a URL makes NewPipe check every link type, and that
            // check calls URLDecoder.decode(String, Charset), which Android 10 does not have.
            val videoId = videoUrl.substringAfter("v=", "").substringBefore("&")
            require(videoId.isNotBlank()) { "No video id in the YouTube URL" }
            val handler = YoutubeStreamLinkHandlerFactory.getInstance().fromId(videoId)
            val info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(handler))
            val stream = info.audioStreams
                .filter { it.isUrl && it.content.isNotBlank() }
                .maxWithOrNull(
                    compareBy<AudioStream> { codecRank(it) }
                        .thenBy { it.averageBitrate },
                ) ?: throw YouTubeExtractionException("No playable audio stream was returned")

            ResolvedYouTubeStream(
                url = stream.content,
                title = info.name,
                artist = info.uploaderName,
                durationSeconds = info.duration,
                thumbnailUrl = info.thumbnails.maxByOrNull(Image::getHeight)?.url.orEmpty(),
                mimeType = stream.format?.mimeType.orEmpty(),
                bitrateKbps = stream.averageBitrate,
                videoUrl = videoUrl,
            )
        } catch (error: YouTubeExtractionException) {
            throw error
        } catch (error: IOException) {
            throw YouTubeExtractionException("Network error while resolving YouTube audio", error)
        } catch (error: Exception) {
            throw YouTubeExtractionException(
                "YouTube extraction failed; the site may have changed its player/cipher response",
                error,
            )
        }
    }

    private fun codecRank(stream: AudioStream): Int {
        val mime = stream.format?.mimeType.orEmpty().lowercase()
        val suffix = stream.format?.suffix.orEmpty().lowercase()
        val codec = stream.codec.orEmpty().lowercase()
        return when {
            "opus" in codec || "opus" in mime || suffix == "opus" -> 3
            "mp4" in mime || suffix == "m4a" || "aac" in mime -> 2
            else -> 1
        }
    }
}

data class YouTubeTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val videoUrl: String,
)

data class ResolvedYouTubeStream(
    val url: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val mimeType: String,
    val bitrateKbps: Int,
    val videoUrl: String,
)

class YouTubeExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)
