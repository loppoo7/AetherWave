package com.aetherwave.di

import com.aetherwave.data.repository.MusicRepositoryImpl
import com.aetherwave.domain.repository.MusicRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusicRepository(implementation: MusicRepositoryImpl): MusicRepository
}
