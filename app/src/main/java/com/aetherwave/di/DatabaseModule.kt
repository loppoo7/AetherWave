package com.aetherwave.di

import android.content.Context
import androidx.room.Room
import com.aetherwave.data.local.AetherWaveDatabase
import com.aetherwave.data.local.dao.PlaylistDao
import com.aetherwave.data.local.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAetherWaveDatabase(
        @ApplicationContext context: Context,
    ): AetherWaveDatabase = Room.databaseBuilder(
        context,
        AetherWaveDatabase::class.java,
        "aetherwave.db",
    ).build()

    @Provides
    fun provideTrackDao(database: AetherWaveDatabase): TrackDao = database.trackDao()

    @Provides
    fun providePlaylistDao(database: AetherWaveDatabase): PlaylistDao = database.playlistDao()
}
