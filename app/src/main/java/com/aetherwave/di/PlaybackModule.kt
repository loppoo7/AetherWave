package com.aetherwave.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.net.Uri
import com.aetherwave.data.source.youtube.InnertubeClient
import com.aetherwave.data.source.youtube.YouTubeMusicSource
import com.aetherwave.playback.RefreshingResolvingDataSource
import com.aetherwave.playback.RoutedAudioDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

const val AETHERWAVE_AUDIO_DATA_SOURCE = "aetherwaveAudioDataSource"
private const val AUDIO_CACHE_MAX_BYTES = 1L shl 30 // 1 GiB

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
            // Extractor access is blocking; Media3 invokes this on its loading thread.
            runBlocking(Dispatchers.IO) {
                val videoId = Uri.parse(videoUrl).getQueryParameter("v")
                    ?: videoUrl.substringAfterLast('/').substringBefore('?')
                val directInnertubeUrl = if (forceRefresh) {
                    null
                } else {
                    // Innertube can reject a request independently of the stream URL.
                    // Fall back to NewPipe for transient API errors and cipher-only formats.
                    runCatching { innertubeClient.playerAudioUrl(videoId) }.getOrNull()
                }
                directInnertubeUrl ?: youtubeMusicSource.getBestAudioStreamUrl(videoUrl).url
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
                enableAudioOutputPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(true)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
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
