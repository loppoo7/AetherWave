package com.aetherwave.data.source.scrobble

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Submits listens to ListenBrainz using the user's token. */
@Singleton
class ListenBrainzClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val jsonType = "application/json".toMediaType()

    suspend fun submitListen(token: String, artist: String, track: String, album: String?, listenedAtSec: Long) {
        val payload = JsonArray().apply { add(listenItem(artist, track, album, listenedAtSec)) }
        send(token, "single", payload)
    }

    suspend fun playingNow(token: String, artist: String, track: String, album: String?) {
        val payload = JsonArray().apply { add(listenItem(artist, track, album, null)) }
        send(token, "playing_now", payload)
    }

    private fun listenItem(artist: String, track: String, album: String?, listenedAtSec: Long?): JsonObject =
        JsonObject().apply {
            listenedAtSec?.let { addProperty("listened_at", it) }
            add("track_metadata", JsonObject().apply {
                addProperty("artist_name", artist)
                addProperty("track_name", track)
                album?.takeIf { it.isNotBlank() }?.let { addProperty("release_name", it) }
                add("additional_info", JsonObject().apply {
                    addProperty("media_player", "AetherWave")
                    addProperty("submission_client", "AetherWave")
                })
            })
        }

    private suspend fun send(token: String, type: String, payload: JsonArray) = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            addProperty("listen_type", type)
            add("payload", payload)
        }
        val request = Request.Builder()
            .url("https://api.listenbrainz.org/1/submit-listens")
            .header("Authorization", "Token $token")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ListenBrainz HTTP ${response.code}")
        }
    }
}
