package com.aetherwave.playback

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Remembers the loudness reported for each YouTube video so the playback thread can apply
 * normalization after a track has been resolved. The map is written by the resolver on ExoPlayer's
 * loading thread and read on the main thread, which is why it is concurrent.
 *
 * Normalization only attenuates or gently raises volume, because ExoPlayer's volume range ends at 1.
 * Local files never go through this path, so Pure/Lossless playback is never altered.
 */
object LoudnessRegistry {
    private const val MIN_VOLUME = 0.2f

    private val loudnessByVideoId = ConcurrentHashMap<String, Float>()

    fun record(videoId: String, loudnessDb: Float) {
        if (loudnessByVideoId.size > 500) loudnessByVideoId.clear()
        loudnessByVideoId[videoId] = loudnessDb
    }

    fun loudnessFor(videoId: String): Float? = loudnessByVideoId[videoId]

    /**
     * Converts a reported loudness into a linear volume. The reported value is the offset of the
     * track from the reference level, so the correction is its negation. The level's headroom
     * keeps quiet tracks below full scale, so they can be raised without clipping.
     */
    fun volumeFor(loudnessDb: Float, level: LoudnessLevel): Float {
        val correctionDb = -loudnessDb
        return (10f.pow(correctionDb / 20f) * level.headroom).coerceIn(MIN_VOLUME, 1f)
    }
}
