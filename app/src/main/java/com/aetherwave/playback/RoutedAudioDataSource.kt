package com.aetherwave.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Caches network streams while sending local content/file URIs straight to Android's data source.
 *
 * Media3's Loader treats any non-IOException thrown while loading as fatal: it is wrapped in an
 * UnexpectedLoaderException and rethrown on the main thread, which kills the app. This class is the
 * outermost data source, so every RuntimeException from the resolver, cache or extractor layers is
 * converted to IOException here. ExoPlayer then reports a normal playback error instead of crashing.
 */
@UnstableApi
class RoutedAudioDataSource(
    private val cachedNetworkFactory: DataSource.Factory,
    private val localFactory: DataSource.Factory,
) : DataSource {
    private val listeners = CopyOnWriteArrayList<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        if (listeners.addIfAbsent(transferListener)) {
            delegate?.addTransferListener(transferListener)
        }
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        closeDelegate()
        val scheme = dataSpec.uri.scheme.orEmpty().lowercase()
        val factory = when (scheme) {
            "aetherwave", "http", "https" -> cachedNetworkFactory
            else -> localFactory
        }
        val created = try {
            factory.createDataSource()
        } catch (error: RuntimeException) {
            throw IOException("Unable to create an audio data source", error)
        }
        listeners.forEach(created::addTransferListener)
        delegate = created
        return try {
            created.open(dataSpec)
        } catch (error: IOException) {
            closeDelegate()
            throw error
        } catch (error: RuntimeException) {
            closeDelegate()
            throw IOException("Audio source failed to open: ${error.message}", error)
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val active = delegate ?: throw IOException("Audio data source is not open")
        return try {
            active.read(buffer, offset, length)
        } catch (error: IOException) {
            throw error
        } catch (error: RuntimeException) {
            throw IOException("Audio read failed: ${error.message}", error)
        }
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        closeDelegate()
    }

    private fun closeDelegate() {
        runCatching { delegate?.close() }
        delegate = null
    }

    class Factory(
        private val cachedNetworkFactory: DataSource.Factory,
        private val localFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutedAudioDataSource(
            cachedNetworkFactory = cachedNetworkFactory,
            localFactory = localFactory,
        )
    }
}
