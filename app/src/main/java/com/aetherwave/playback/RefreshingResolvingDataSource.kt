package com.aetherwave.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves aetherwave://track items at load time and retries a single HTTP 403 with a fresh URL.
 * The retry happens at DataSource.open(), because Media3's Resolver itself is not re-invoked
 * after an upstream open has already failed.
 */
@UnstableApi
class RefreshingResolvingDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val streamUrlProvider: (String, Boolean) -> String,
) : DataSource {
    private var delegate: ResolvingDataSource? = null
    private var currentSpec: DataSpec? = null
    private val transferListeners = mutableListOf<androidx.media3.datasource.TransferListener>()

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        transferListeners += transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        currentSpec = dataSpec
        delegate = newResolvingDataSource(forceRefresh = false)
        return try {
            delegate!!.open(dataSpec)
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            if (error.responseCode != 403) throw error
            runCatching { delegate?.close() }
            delegate = newResolvingDataSource(forceRefresh = true)
            delegate!!.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: throw IOException("Data source is not open")

    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
        currentSpec = null
    }

    private fun newResolvingDataSource(forceRefresh: Boolean): ResolvingDataSource {
        val resolver = ResolvingDataSource.Resolver { spec ->
            val original = spec.uri
            if (original.scheme != "aetherwave") return@Resolver spec
            val videoUrl = original.getQueryParameter("url")
                ?: throw IOException("Missing YouTube URL in media item")
            val freshUrl = try {
                streamUrlProvider(videoUrl, forceRefresh)
            } catch (error: Exception) {
                throw IOException("Unable to resolve a fresh YouTube stream", error)
            }
            spec.buildUpon().setUri(Uri.parse(freshUrl)).build()
        }
        val resolving = ResolvingDataSource(upstreamFactory.createDataSource(), resolver)
        transferListeners.forEach(resolving::addTransferListener)
        return resolving
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val streamUrlProvider: (String, Boolean) -> String,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RefreshingResolvingDataSource(upstreamFactory, streamUrlProvider)
    }
}
