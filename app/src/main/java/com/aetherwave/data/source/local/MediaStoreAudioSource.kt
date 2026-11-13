package com.aetherwave.data.source.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreAudioSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun requiredReadPermission(): String = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        requiredReadPermission(),
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun scanAudio(): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) {
            throw SecurityException("Audio library permission is required: ${requiredReadPermission()}")
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = ArrayList<MusicTrack>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idColumn)
                val duration = cursor.getLong(durationColumn).coerceAtLeast(0L)
                if (duration == 0L) continue

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaId,
                )
                val albumId = cursor.getLong(albumIdColumn)
                val artwork = if (albumId > 0L) {
                    Uri.parse("content://media/external/audio/albumart/$albumId").toString()
                } else {
                    null
                }

                result += MusicTrack(
                    id = "local:$mediaId",
                    source = TrackSource.LOCAL,
                    sourceId = mediaId.toString(),
                    title = cursor.getString(titleColumn).orEmpty().ifBlank { "Unknown title" },
                    artist = cursor.getString(artistColumn).orEmpty().ifBlank { "Unknown artist" },
                    album = cursor.getString(albumColumn),
                    durationMs = duration,
                    artworkUri = artwork,
                    mediaUri = contentUri.toString(),
                )
            }
        }
        result
    }
}
