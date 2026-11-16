package com.aetherwave.di

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aetherwave.data.source.youtube.InnertubeClient
import com.aetherwave.data.source.youtube.PlayerStream
import com.aetherwave.playback.LoudnessRegistry
import com.aetherwave.data.source.youtube.YouTubeMusicSource
import com.aetherwave.playback.RefreshingResolvingDataSource
import com.aetherwave.playback.ResolvedStream
import com.aetherwave.playback.RoutedAudioDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import javax.inject.Named
import javax.inject.Singleton

const val AETHERWAVE_AUDIO_DATA_SOURCE = "aetherwaveAudioDataSource"
private const val AUDIO_CACHE_MAX_BYTES = 1L shl 30 // 1 GiB
private const val PLAYBACK_TAG = "AetherWavePlayback"
private val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")

@Module
@InstallIn(SingletonComponent::class)
@UnstableApi
object PlaybackModule {
    @Provides
    @Singleton
    fun provideAudioCache(
        @ApplicationContext context: Context,
    ): Cache = SimpleCache(
        File(context.cacheDir, "aetherwave-audio-cache"),
        LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES),
        StandaloneDatabaseProvider(context),
    )

    /**
     * CacheDataSource is intentionally OUTSIDE the resolving source. This makes the original
     * stable AetherWave URI/cache key available before URL resolution, so a complete cached track
     * can play offline without calling NewPipe to obtain an expired stream URL.
     * DefaultDataSource also supports content:// and file:// for local phone audio.
     *
     * Stream resolution order: Innertube WEB_REMIX player (fast, direct URL) first; on any failure
     * or a cipher-only response, NewPipeExtractor resolves the stream instead. A failed resolution
     * surfaces as IOException and never as an uncaught exception on ExoPlayer's loading thread.
     */
    @Provides
    @Singleton
    @Named(AETHERWAVE_AUDIO_DATA_SOURCE)
    fun provideAudioDataSourceFactory(
        @ApplicationContext context: Context,
        youtubeMusicSource: YouTubeMusicSource,
        innertubeClient: InnertubeClient,
        cache: Cache,
    ): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("AetherWave/1.0.0 (Android)")
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/",
                ),
            )
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)

        val defaultUpstream = DefaultDataSource.Factory(context, httpFactory)
        val resolvingFactory = RefreshingResolvingDataSource.Factory(defaultUpstream) { videoUrl, forceRefresh ->
            // Runs on Media3's loading thread. When both sources fail, the error names each one.
            runBlocking(Dispatchers.IO) {
                val videoId = extractVideoId(videoUrl)
                val innertube = if (forceRefresh) {
                    Result.failure<PlayerStream>(IOException("refresh requested"))
                } else {
                    runCatching { innertubeClient.playerAudioUrl(videoId) }
                }
                val direct = innertube.getOrNull()
                if (direct != null) {
                    direct.loudnessDb?.let { LoudnessRegistry.record(videoId, it) }
                    ResolvedStream(
                        url = direct.url,
                        headers = direct.userAgent?.let { mapOf("User-Agent" to it) }.orEmpty(),
                    )
                } else {
                    val fallback = runCatching { youtubeMusicSource.getBestAudioStreamUrl(videoUrl).url }
                    ResolvedStream(
                        url = fallback.getOrElse { error ->
                            throw IOException(
                                "Innertube: ${innertube.exceptionOrNull()?.message ?: "no stream"} | " +
                                    "NewPipe: ${error.javaClass.simpleName} ${error.message.orEmpty()}",
                                error,
                            )
                        },
                    )
                }
            }
        }

        val cachedNetworkFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return RoutedAudioDataSource.Factory(
            cachedNetworkFactory = cachedNetworkFactory,
            localFactory = defaultUpstream,
        )
    }

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        @Named(AETHERWAVE_AUDIO_DATA_SOURCE) dataSourceFactory: DataSource.Factory,
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                .build()
        }.setEnableAudioFloatOutput(true)
            .setEnableDecoderFallback(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply { setHandleAudioBecomingNoisy(true) }
    }
}

private fun extractVideoId(videoUrl: String): String {
    val id = Uri.parse(videoUrl).getQueryParameter("v")
        ?: videoUrl.substringAfterLast('/').substringBefore('?')
    require(VIDEO_ID_PATTERN.matches(id)) { "Invalid YouTube video id" }
    return id
}

/**
 * Returns null instead of throwing so the caller can fall back to NewPipe. Cancellation is
 * rethrown so that a cancelled load does not get mistaken for an Innertube failure.
 */
private suspend fun innertubeAudioUrlOrNull(client: InnertubeClient, videoId: String): PlayerStream? =
    try {
        client.playerAudioUrl(videoId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(PLAYBACK_TAG, "Innertube player failed for $videoId; falling back to NewPipe", error)
        null
    }
