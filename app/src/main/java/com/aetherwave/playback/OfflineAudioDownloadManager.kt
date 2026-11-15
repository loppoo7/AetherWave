package com.aetherwave.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.aetherwave.data.local.dao.TrackDao
import com.aetherwave.di.AETHERWAVE_AUDIO_DATA_SOURCE
import com.aetherwave.data.local.entity.toEntity
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Downloads a complete YouTube audio stream through the same CacheDataSource pipeline used by
 * playback, while also writing a durable app-private file for explicit offline playback.
 */
@UnstableApi
@Singleton
class OfflineAudioDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named(AETHERWAVE_AUDIO_DATA_SOURCE) private val dataSourceFactory: DataSource.Factory,
    private val trackDao: TrackDao,
) {
    suspend fun download(track: MusicTrack): File = withContext(Dispatchers.IO) {
        require(track.source == TrackSource.YOUTUBE) {
            "Only online YouTube tracks can be downloaded by this manager"
        }
        val videoUrl = requireNotNull(track.videoUrl) { "Track does not contain a YouTube source URL" }
        val sourceId = track.sourceId.ifBlank { track.id.substringAfter(':') }
        val cacheKey = TrackMediaItem.youtubeCacheKey(sourceId)
        val resolverUri = Uri.Builder()
            .scheme("aetherwave")
            .authority("track")
            .appendQueryParameter("url", videoUrl)
            .build()

        val directory = File(context.filesDir, "offline-audio").apply { mkdirs() }
        val safeId = sourceId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(directory, "$safeId.audio")
        val temporary = File(directory, "$safeId.audio.part")
        val backup = File(directory, "$safeId.audio.bak")
        val dataSource = dataSourceFactory.createDataSource()
        val dataSpec = DataSpec.Builder()
            .setUri(resolverUri)
            .setKey(cacheKey)
            .build()

        try {
            // Recover the previous completed download if the app was interrupted mid-replacement.
            if (!destination.exists() && backup.exists() && !backup.renameTo(destination)) {
                throw IOException("Unable to restore the previous offline audio file")
            }
            dataSource.open(dataSpec)
            FileOutputStream(temporary, false).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val bytesRead = dataSource.read(buffer, 0, buffer.size)
                    if (bytesRead == C.RESULT_END_OF_INPUT) break
                    if (bytesRead > 0) output.write(buffer, 0, bytesRead)
                }
                output.fd.sync()
            }

            if (!temporary.isFile || temporary.length() == 0L) {
                throw IOException("The server returned an empty audio stream")
            }
            // Keep the current offline file intact until the replacement is complete and saved.
            if (backup.exists() && !backup.delete()) {
                throw IOException("Unable to clear the previous offline backup")
            }
            val hadPreviousDownload = destination.isFile
            if (hadPreviousDownload && !destination.renameTo(backup)) {
                throw IOException("Unable to protect the previous offline audio file")
            }

            try {
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = false)
                    temporary.delete()
                }
                if (!destination.isFile || destination.length() == 0L) {
                    throw IOException("The downloaded audio file is empty")
                }

                trackDao.insertTrackIfMissing(track.toEntity())
                trackDao.updateDownloadState(track.id, true, destination.absolutePath)
            } catch (installError: Exception) {
                destination.delete()
                if (hadPreviousDownload && backup.exists() && !backup.renameTo(destination)) {
                    installError.addSuppressed(IOException("Unable to restore the previous offline audio file"))
                }
                throw installError
            }

            // A failed cleanup must not invalidate the newly saved download.
            if (backup.exists()) backup.delete()
            destination
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (error: Exception) {
            temporary.delete()
            throw IOException("Unable to download '${track.title}' for offline playback", error)
        } finally {
            runCatching { dataSource.close() }
        }
    }

    suspend fun removeDownload(track: MusicTrack) = withContext(Dispatchers.IO) {
        val file = track.localFilePath?.let { File(it) }
        if (file != null && file.exists() && !file.delete()) {
            throw IOException("Unable to delete offline file: ${file.absolutePath}")
        }
        trackDao.updateDownloadState(track.id, false, null)
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 128 * 1024
    }
}
