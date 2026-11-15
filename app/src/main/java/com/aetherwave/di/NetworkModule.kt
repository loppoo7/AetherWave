package com.aetherwave.di

import com.aetherwave.data.remote.lrclib.LrcLibApi
import com.aetherwave.data.source.youtube.NewPipeOkHttpDownloader
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NewPipeOkHttpDownloader.createClient()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
        .build()

    @Provides
    @Singleton
    fun provideLrcLibApi(retrofit: Retrofit): LrcLibApi = retrofit.create(LrcLibApi::class.java)
}
