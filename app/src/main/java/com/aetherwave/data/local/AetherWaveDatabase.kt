package com.aetherwave.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aetherwave.data.local.dao.PlaylistDao
import com.aetherwave.data.local.dao.TrackDao
import com.aetherwave.data.local.entity.PlaylistEntity
import com.aetherwave.data.local.entity.PlaylistTrackCrossRef
import com.aetherwave.data.local.entity.TrackEntity

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackCrossRef::class],
    version = 2,
    exportSchema = true,
)
abstract class AetherWaveDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
}
