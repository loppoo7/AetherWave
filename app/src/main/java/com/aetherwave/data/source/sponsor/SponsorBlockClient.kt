package com.aetherwave.data.source.sponsor

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches community-submitted skip segments from SponsorBlock for a YouTube video. Every failure
 * returns an empty list, so a SponsorBlock outage never affects playback.
 */
@Singleton
class SponsorBlockClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    suspend fun segmentsFor(videoId: String): List<LongRange> = withContext(Dispatchers.IO) {
        runCatching {
            val categories = URLEncoder.encode(CATEGORIES_JSON, "UTF-8")
            val request = Request.Builder()
                .url("https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$categories")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList<LongRange>()
                val array = JsonParser.parseString(response.body?.string().orEmpty())
                    .takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?: return@use emptyList<LongRange>()
                array.mapNotNull { element ->
                    val segment = element.takeIf { it.isJsonObject }?.asJsonObject?.get("segment")
                        ?.takeIf { it.isJsonArray }?.asJsonArray
                        ?: return@mapNotNull null
                    if (segment.size() < 2) return@mapNotNull null
                    val start = (segment[0].asDouble * 1000).toLong()
                    val end = (segment[1].asDouble * 1000).toLong()
                    if (end > start) LongRange(start, end) else null
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val CATEGORIES_JSON = "[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"music_offtopic\"]"
    }
}
