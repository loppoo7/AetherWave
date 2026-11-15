package com.aetherwave.data.remote.lrclib

import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface LrcLibApi {
    @Headers("User-Agent: AetherWave/1.0.0 (Android)")
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("duration") durationSeconds: Double? = null,
        @Query("album_name") albumName: String? = null,
    ): LrcLibTrackResponse

    @Headers("User-Agent: AetherWave/1.0.0 (Android)")
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null,
    ): List<LrcLibTrackResponse>
}
