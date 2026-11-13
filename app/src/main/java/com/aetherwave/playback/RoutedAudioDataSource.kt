package com.aetherwave.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Caches network streams while sending local content/file URIs straight to Android's data source. */
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
        close()
        val scheme = dataSpec.uri.scheme.orEmpty().lowercase()
        val factory = when (scheme) {
            "aetherwave", "http", "https" -> cachedNetworkFactory
            else -> localFactory
        }
        val created = factory.createDataSource()
        listeners.forEach(created::addTransferListener)
        delegate = created
        return try {
            created.open(dataSpec)
        } catch (error: IOException) {
            runCatching { created.close() }
            delegate = null
            throw error
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: throw IOException("Audio data source is not open")

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
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
