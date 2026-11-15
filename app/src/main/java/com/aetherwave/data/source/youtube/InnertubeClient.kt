package com.aetherwave.data.source.youtube

import com.aetherwave.domain.model.MusicHomeFeed
import com.aetherwave.domain.model.MusicSection
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.TrackSource
import com.google.gson.JsonArray
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
 * Defensive WEB_REMIX client for YouTube Music discovery, search, radio and player metadata.
 *
 * Every JSON access goes through the type-checked helpers below. Gson's getAsJsonObject,
 * getAsJsonArray and asString throw on unexpected node types, and an exception escaping this
 * client during playback can crash the app, so nothing here trusts the shape of a response.
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
            .url("$endpoint$path?prettyPrint=false")
            .header("Origin", ORIGIN)
            .header("Referer", REFERER)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("YouTube Music $path failed with HTTP ${response.code}")
            if (payload.isBlank()) throw IOException("YouTube Music returned an empty $path response")
            runCatching { JsonParser.parseString(payload) }.getOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: throw IOException("YouTube Music $path returned unexpected JSON")
        }
    }

    suspend fun homeFeed(): MusicHomeFeed {
        val response = post("browse", JsonObject().apply {
            add("context", requestContext())
            addProperty("browseId", "FEmusic_home")
        })
        val sections = findSections(response).mapNotNull { section ->
            val tracks = findTrackRenderers(section).mapNotNull(::toTrack).distinctBy { it.id }
            if (tracks.isEmpty()) null else (sectionTitle(section) to tracks)
        }
        val all = sections.flatMap { it.second }.distinctBy { it.id }
        val quickIndex = sections.indexOfFirst { it.first.contains("quick", true) }.let { if (it >= 0) it else 0 }
        val quick = sections.getOrNull(quickIndex)?.second.orEmpty()
        val trending = sections.firstOrNull {
            it.first.contains("trending", true) || it.first.contains("chart", true)
        }?.second.orEmpty()
        val recommended = sections.filterIndexed { index, section ->
            index != quickIndex &&
                !section.first.contains("trending", true) &&
                !section.first.contains("chart", true)
        }.flatMap { it.second }.distinctBy { it.id }
        val quickIds = quick.map(MusicTrack::id).toSet()
        return MusicHomeFeed(
            quickPicks = quick.ifEmpty { all }.take(24),
            trending = trending.take(20),
            recommended = recommended.ifEmpty { all.filterNot { it.id in quickIds } }.take(24),
        )
    }

    /** Explore page: charts, moods and new releases from the FEmusic_explore browse. */
    suspend fun exploreSections(): List<MusicSection> {
        val response = post("browse", JsonObject().apply {
            add("context", requestContext())
            addProperty("browseId", "FEmusic_explore")
        })
        return findSections(response).mapNotNull { section ->
            val tracks = findTrackRenderers(section).mapNotNull(::toTrack).distinctBy { it.id }.take(30)
            if (tracks.isEmpty()) null else MusicSection(sectionTitle(section).ifBlank { "Explore" }, tracks)
        }
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
     * Radio / "Up Next" queue for a seed video, using the same RDAMVM radio playlist YouTube Music
     * uses for "Start radio". The seed itself is removed from the result.
     */
    suspend fun upNext(videoId: String, limit: Int = 25): List<MusicTrack> {
        require(videoId.isNotBlank()) { "videoId must not be blank" }
        val response = post("next", JsonObject().apply {
            add("context", requestContext())
            addProperty("videoId", videoId)
            addProperty("playlistId", "RDAMVM$videoId")
            addProperty("isAudioOnly", true)
        })
        return collect(response, setOf("playlistPanelVideoRenderer"))
            .mapNotNull(::toQueueTrack)
            .filterNot { it.sourceId == videoId }
            .distinctBy { it.id }
            .take(limit.coerceIn(1, 50))
    }

    /**
     * Returns the highest-bitrate direct audio URL. Cipher-only formats return null and are
     * delegated to NewPipeExtractor by PlaybackModule for signature deciphering.
     */
    suspend fun playerAudioUrl(videoId: String): PlayerStream? {
        require(videoId.isNotBlank()) { "videoId must not be blank" }
        val response = post("player", JsonObject().apply {
            add("context", requestContext())
            addProperty("videoId", videoId)
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
        })
        val streaming = response.obj("streamingData") ?: return null
        val best = listOfNotNull(streaming.arr("adaptiveFormats"), streaming.arr("formats"))
            .flatMap { array -> array.mapNotNull { element -> element.takeIf { it.isJsonObject }?.asJsonObject } }
            .asSequence()
            .filter { (it.str("mimeType") ?: "").startsWith("audio/") }
            .filter { (it.str("url") ?: "").startsWith("https://") }
            .maxByOrNull { it.long("bitrate") ?: 0L }
            ?: return null
        val url = best.str("url") ?: return null
        // YouTube reports the track loudness relative to its reference level in playerConfig.audioConfig.
        val loudness = response.obj("playerConfig")?.obj("audioConfig")?.get("loudnessDb")
            ?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asFloat }.getOrNull() }
        return PlayerStream(url = url, loudnessDb = loudness)
    }

    private fun sectionTitle(section: JsonObject): String {
        section.obj("header")?.let { header ->
            header.entrySet().forEach { (_, value) ->
                value.takeIf { it.isJsonObject }?.asJsonObject?.get("title")?.let { firstText(it) }?.let { return it }
            }
        }
        return firstText(section.get("title")).orEmpty()
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
                keys.forEach { key -> obj.obj(key)?.let(output::add) }
                obj.entrySet().forEach { (_, child) -> walk(child) }
            } else if (node.isJsonArray) {
                node.asJsonArray.forEach(::walk)
            }
        }
        walk(root)
        return output
    }

    private fun toTrack(renderer: JsonObject): MusicTrack? {
        val columns = renderer.arr("flexColumns")
        val title = columns?.takeIf { it.size() > 0 }?.get(0)?.let(::firstText)
            ?: firstText(renderer.get("title"))
            ?: return null
        val subtitle = columns?.takeIf { it.size() > 1 }?.get(1)?.let(::firstText)
            ?: firstText(renderer.get("subtitle")).orEmpty()
        val videoId = findString(renderer, "videoId") ?: return null
        return buildTrack(
            videoId = videoId,
            title = title,
            artist = artistFrom(subtitle),
            durationMs = durationFrom(renderer),
            artwork = findThumbnail(renderer),
        )
    }

    private fun toQueueTrack(renderer: JsonObject): MusicTrack? {
        val videoId = renderer.str("videoId") ?: findString(renderer, "videoId") ?: return null
        val title = firstText(renderer.get("title")).orEmpty()
        val durationMs = firstText(renderer.get("lengthText"))?.let(::parseDuration) ?: 0L
        return buildTrack(
            videoId = videoId,
            title = title,
            artist = artistFrom(firstText(renderer.get("longBylineText")).orEmpty()),
            durationMs = durationMs,
            artwork = findThumbnail(renderer),
        )
    }

    private fun buildTrack(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        artwork: String?,
    ): MusicTrack? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null
        return MusicTrack(
            id = "youtube:$videoId",
            source = TrackSource.YOUTUBE,
            sourceId = videoId,
            title = cleanTitle,
            artist = artist.ifBlank { "Unknown artist" },
            durationMs = durationMs,
            artworkUri = artwork,
            videoUrl = "https://www.youtube.com/watch?v=$videoId",
        )
    }

    private fun artistFrom(subtitle: String): String = subtitle
        .split(" • ", " · ", " | ")
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
        .takeIf { it.isNotEmpty() && !DURATION_EXACT.matches(it) }
        .orEmpty()

    private fun durationFrom(renderer: JsonObject): Long {
        val fixedColumns = renderer.arr("fixedColumns")
            ?.mapNotNull { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject
                    ?.obj("musicResponsiveListItemFixedColumnRenderer")
                    ?.let(::firstText)
            }
            .orEmpty()
        return (fixedColumns + findAllText(renderer))
            .asSequence()
            .mapNotNull { text -> DURATION_PATTERN.find(text)?.value?.let(::parseDuration) }
            .firstOrNull { it > 0L }
            ?: 0L
    }

    private fun parseDuration(value: String): Long {
        val parts = value.trim().split(":").mapNotNull(String::toLongOrNull)
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
            obj.str(key)?.takeIf(String::isNotBlank)?.let { return it }
            obj.entrySet().forEach { (_, child) -> findString(child, key)?.let { return it } }
        } else if (root.isJsonArray) {
            root.asJsonArray.forEach { child -> findString(child, key)?.let { return it } }
        }
        return null
    }

    private fun findThumbnail(root: JsonElement): String? {
        val candidates = mutableListOf<Pair<Long, String>>()
        fun walk(node: JsonElement?) {
            if (node == null || node.isJsonNull) return
            if (node.isJsonObject) {
                val obj = node.asJsonObject
                obj.arr("thumbnails")?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.let { thumb ->
                        val url = thumb.str("url").orEmpty()
                        if (url.startsWith("http")) candidates += (thumb.long("width") ?: 0L) to url
                    }
                }
                obj.entrySet().forEach { (_, child) -> walk(child) }
            } else if (node.isJsonArray) {
                node.asJsonArray.forEach(::walk)
            }
        }
        walk(root)
        return candidates.maxByOrNull { it.first }?.second?.replace(Regex("w\\d+-h\\d+"), "w544-h544")
    }

    private fun firstText(node: JsonElement?): String? {
        if (node == null || node.isJsonNull) return null
        if (node.isJsonPrimitive) {
            return node.asJsonPrimitive.takeIf { it.isString }?.asString?.takeIf(String::isNotBlank)
        }
        if (node.isJsonObject) {
            val obj = node.asJsonObject
            obj.str("simpleText")?.takeIf(String::isNotBlank)?.let { return it }
            obj.arr("runs")?.let { runs ->
                val joined = runs.mapNotNull { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.str("text")
                }.joinToString("").trim()
                if (joined.isNotBlank()) return joined
            }
            obj.entrySet().forEach { (_, child) -> firstText(child)?.let { return it } }
        } else if (node.isJsonArray) {
            node.asJsonArray.forEach { child -> firstText(child)?.let { return it } }
        }
        return null
    }

    private fun findAllText(root: JsonElement): List<String> {
        val result = mutableListOf<String>()
        fun walk(node: JsonElement?) {
            if (node == null || node.isJsonNull) return
            if (node.isJsonObject) {
                val obj = node.asJsonObject
                obj.str("simpleText")?.let(result::add)
                obj.arr("runs")?.forEach { run ->
                    run.takeIf { it.isJsonObject }?.asJsonObject?.str("text")?.let(result::add)
                }
                obj.entrySet().forEach { (_, child) -> walk(child) }
            } else if (node.isJsonArray) {
                node.asJsonArray.forEach(::walk)
            }
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
        private val DURATION_PATTERN = Regex("""(?:\d{1,2}:)?\d{1,2}:\d{2}""")
        private val DURATION_EXACT = Regex("""\d{1,2}:\d{2}""")
    }
}

private fun JsonObject.obj(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arr(key: String): JsonArray? =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.str(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonObject.long(key: String): Long? =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

/** Direct stream URL plus the loudness reported by the same player response, when present. */
data class PlayerStream(
    val url: String,
    val loudnessDb: Float?,
)
