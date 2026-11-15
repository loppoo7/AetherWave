package com.aetherwave.data.remote.lrclib

import com.google.gson.annotations.SerializedName

/** JSON response model shared by the stable LRCLIB v1 get and search endpoints. */
data class LrcLibTrackResponse(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("trackName") val trackName: String = "",
    @SerializedName("artistName") val artistName: String = "",
    @SerializedName("albumName") val albumName: String? = null,
    @SerializedName("duration") val duration: Double? = null,
    @SerializedName("instrumental") val instrumental: Boolean = false,
    @SerializedName("plainLyrics") val plainLyrics: String? = null,
    @SerializedName("syncedLyrics") val syncedLyrics: String? = null,
)
