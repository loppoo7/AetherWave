package com.aetherwave.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

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
    private val transferListeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        closeDelegate()
        val first = newResolvingDataSource(forceRefresh = false)
        delegate = first
        return try {
            first.open(dataSpec)
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            closeDelegate()
            if (error.responseCode != 403) throw error
            val refreshed = newResolvingDataSource(forceRefresh = true)
            delegate = refreshed
            try {
                refreshed.open(dataSpec)
            } catch (retryError: IOException) {
                closeDelegate()
                throw retryError
            }
        } catch (error: IOException) {
            closeDelegate()
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: throw IOException("Data source is not open")

    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        closeDelegate()
    }

    private fun closeDelegate() {
        runCatching { delegate?.close() }
        delegate = null
    }

    private fun newResolvingDataSource(forceRefresh: Boolean): ResolvingDataSource {
        val resolver = ResolvingDataSource.Resolver { spec ->
            val original = spec.uri
            if (original.scheme != "aetherwave") return@Resolver spec
            val videoUrl = original.getQueryParameter("url")
                ?: throw IOException("Missing YouTube URL in media item")
            val freshUrl = try {
                streamUrlProvider(videoUrl, forceRefresh)
            } catch (error: Throwable) {
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
