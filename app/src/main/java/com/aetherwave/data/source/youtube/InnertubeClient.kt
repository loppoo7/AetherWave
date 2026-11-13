package com.aetherwave.data.source.youtube

import com.aetherwave.domain.model.MusicHomeFeed
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defensive WEB_REMIX client for YouTube Music discovery and player metadata.
 * Innertube is undocumented and can change without notice; unknown response nodes are ignored.
 */
@Singleton
class InnertubeClient @Inject constructor(
    private val httpClient: OkHttpClient,
) {
    private val endpoint = "https://music.youtube.com/youtubei/v1/"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun requestContext() = JsonObject().apply {
        add("client", JsonObject().apply {
            addProperty("clientName", "WEB_REMIX")
            addProperty("clientVersion", CLIENT_VERSION)
            addProperty("hl", "en")
            addProperty("gl", "US")
            addProperty("platform", "DESKTOP")
        })
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint + path + "?prettyPrint=false")
            .header("Origin", ORIGIN)
            .header("Referer", REFERER)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("YouTube Music " + path + " failed with HTTP " + response.code)
            if (payload.isBlank()) throw IOException("YouTube Music returned an empty " + path + " response")
            JsonParser.parseString(payload).asJsonObject
        }
    }

    suspend fun homeFeed(): MusicHomeFeed {
        val response = post("browse", JsonObject().apply {
            add("context", requestContext())
            addProperty("browseId", "FEmusic_home")
        })
        val sections = findSections(response).mapNotNull { section ->
            val title = firstText(section).orEmpty()
            val tracks = findTrackRenderers(section).mapNotNull(::toTrack).distinctBy { it.id }
            if (tracks.isEmpty()) null else title to tracks
        }
        val all = sections.flatMap { it.second }.distinctBy { it.id }
        val quick = sections.firstOrNull { it.first.contains("quick", true) }?.second
            ?: sections.firstOrNull()?.second.orEmpty()
        val trending = sections.firstOrNull {
            it.first.contains("trending", true) || it.first.contains("chart", true)
        }?.second.orEmpty()
        val quickIndex = sections.indexOfFirst { it.first.contains("quick", true) }.let { index ->
            if (index >= 0) index else 0
        }
        val recommended = sections.filterIndexed { index, section ->
            index != quickIndex &&
                !section.first.contains("trending", true) &&
                !section.first.contains("chart", true)
        }.flatMap { it.second }.distinctBy { it.id }
        return MusicHomeFeed(
            quickPicks = (quick.ifEmpty { all }).take(24),
            trending = trending.take(20),
            recommended = (recommended.ifEmpty { all.filterNot { item -> item.id in quick.map(MusicTrack::id).toSet() } }).take(24),
        )
    }

    suspend fun searchSongs(query: String, limit: Int = 30): List<MusicTrack> {
        val normalized = query.trim()
        require(normalized.isNotEmpty()) { "Search query must not be blank" }
        val params = URLDecoder.decode(SONG_FILTER_PARAMS, StandardCharsets.UTF_8.name())
        val response = post("search", JsonObject().apply {
            add("context", requestContext())
            addProperty("query", normalized)
            addProperty("params", params)
        })
        return collect(response, setOf("musicResponsiveListItemRenderer"))
            .mapNotNull(::toTrack)
            .distinctBy { it.id }
            .take(limit.coerceIn(1, 50))
    }

    /**
     * Returns the highest-bitrate direct audio URL. Cipher-only formats return null and are
     * delegated to NewPipeExtractor by PlaybackModule for signature deciphering.
     */
    suspend fun playerAudioUrl(videoId: String): String? {
        require(videoId.isNotBlank()) { "videoId must not be blank" }
        val response = post("player", JsonObject().apply {
            add("context", requestContext())
            addProperty("videoId", videoId)
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
        })
        val streaming = response.getAsJsonObject("streamingData") ?: return null
        val candidates = listOf("adaptiveFormats", "formats").flatMap { key ->
            streaming.getAsJsonArray(key)?.mapNotNull { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject
            }.orEmpty()
        }
        return candidates.asSequence()
            .filter { it.get("mimeType")?.asString.orEmpty().startsWith("audio/") }
            .filter { it.get("url")?.asString?.startsWith("https://") == true }
            .maxByOrNull { it.get("bitrate")?.asLong ?: 0L }
            ?.get("url")?.asString
    }

    private fun findSections(root: JsonElement): List<JsonObject> =
        collect(root, setOf("musicCarouselShelfRenderer", "musicShelfRenderer", "gridRenderer"))

    private fun findTrackRenderers(root: JsonElement): List<JsonObject> =
        collect(root, setOf("musicResponsiveListItemRenderer", "musicTwoRowItemRenderer"))

    private fun collect(root: JsonElement?, keys: Set<String>): List<JsonObject> {
        val output = mutableListOf<JsonObject>()
        fun walk(node: JsonElement?) {
            if (node == null || node.isJsonNull) return
            if (node.isJsonObject) {
                val obj = node.asJsonObject
                keys.forEach { key -> obj.get(key)?.takeIf { it.isJsonObject }?.asJsonObject?.let(output::add) }
                obj.entrySet().forEach { (_, child) -> walk(child) }
            } else if (node.isJsonArray) node.asJsonArray.forEach(::walk)
        }
        walk(root)
        return output
    }

    private fun toTrack(renderer: JsonObject): MusicTrack? {
        val columns = renderer.getAsJsonArray("flexColumns")
        val title = columns?.takeIf { it.size() > 0 }?.get(0)?.let(::firstText)
            ?: firstText(renderer.get("title")) ?: return null
        val subtitle = columns?.takeIf { it.size() > 1 }?.get(1)?.let(::firstText)
            ?: firstText(renderer.get("subtitle")).orEmpty()
        val artist = subtitle.split(" • ", " · ", " | ").firstOrNull { it.isNotBlank() }
            ?.takeIf { !it.matches(Regex("\\d{1,2}:\\d{2}")) } ?: "Unknown artist"
        val videoId = findString(renderer, "videoId") ?: return null
        val duration = findAllText(renderer).asSequence().mapNotNull { text ->
            Regex("(?:\\d{1,2}:)?\\d{1,2}:\\d{2}").find(text)?.value?.let(::parseDuration)
        }.firstOrNull() ?: 0L
        return MusicTrack(
            id = "youtube:$videoId",
            source = TrackSource.YOUTUBE,
            sourceId = videoId,
            title = title.trim(),
            artist = artist.trim(),
            durationMs = duration,
            artworkUri = findThumbnail(renderer),
            videoUrl = "https://www.youtube.com/watch?v=$videoId",
        )
    }

    private fun parseDuration(value: String): Long {
        val parts = value.split(":").mapNotNull(String::toLongOrNull)
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1_000L
            3 -> (parts[0] * 3_600L + parts[1] * 60L + parts[2]) * 1_000L
            else -> 0L
        }
    }

    private fun findString(root: JsonElement?, key: String): String? {
        if (root == null || root.isJsonNull) return null
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?.takeIf(String::isNotBlank)?.let { return it }
            obj.entrySet().forEach { (_, child) -> findString(child, key)?.let { return it } }
        } else if (root.isJsonArray) root.asJsonArray.forEach { child -> findString(child, key)?.let { return it } }
        return null
    }

    private fun findThumbnail(root: JsonElement): String? {
        val candidates = mutableListOf<Pair<Int, String>>()
        fun walk(node: JsonElement?) {
            if (node == null || node.isJsonNull) return
            if (node.isJsonObject) {
                val obj = node.asJsonObject
                obj.getAsJsonArray("thumbnails")?.forEach { item ->
                    if (item.isJsonObject) {
                        val thumb = item.asJsonObject
                        val url = thumb.get("url")?.asString.orEmpty()
                        if (url.startsWith("http")) candidates += (thumb.get("width")?.asInt ?: 0) to url
                    }
                }
                obj.entrySet().forEach { (_, child) -> walk(child) }
            } else if (node.isJsonArray) node.asJsonArray.forEach(::walk)
        }
        walk(root)
        return candidates.maxByOrNull { it.first }?.second?.replace(Regex("w\\d+-h\\d+"), "w544-h544")
    }

    private fun firstText(node: JsonElement?): String? {
        if (node == null || node.isJsonNull) return null
        if (node.isJsonObject) {
            val obj = node.asJsonObject
            obj.get("simpleText")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)?.let { return it }
            obj.getAsJsonArray("runs")?.let { runs ->
                val joined = runs.mapNotNull { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString
                }.joinToString("").trim()
                if (joined.isNotBlank()) return joined
            }
            obj.entrySet().forEach { (_, child) -> firstText(child)?.let { return it } }
        } else if (node.isJsonArray) node.asJsonArray.forEach { child -> firstText(child)?.let { return it } }
        return null
    }

    private fun findAllText(root: JsonElement): List<String> {
        val result = mutableListOf<String>()
        fun walk(node: JsonElement?) {
            if (node == null || node.isJsonNull) return
            if (node.isJsonObject) {
                val obj = node.asJsonObject
                obj.get("simpleText")?.takeIf { it.isJsonPrimitive }?.asString?.let(result::add)
                obj.getAsJsonArray("runs")?.forEach { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.get("text")?.asString?.let(result::add)
                }
                obj.entrySet().forEach { (_, child) -> walk(child) }
            } else if (node.isJsonArray) node.asJsonArray.forEach(::walk)
        }
        walk(root)
        return result
    }

    companion object {
        const val CLIENT_VERSION = "1.20241101.01.00"
        const val SONG_FILTER_PARAMS = "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D"
        const val ORIGIN = "https://music.youtube.com"
        const val REFERER = "https://music.youtube.com/"
        private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
