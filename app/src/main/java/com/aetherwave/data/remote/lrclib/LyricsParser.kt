package com.aetherwave.data.remote.lrclib

import com.aetherwave.domain.model.LyricLine

/** Parses standard LRC timestamps, including repeated timestamps and signed [offset:] headers. */
object LyricsParser {
    private val timestampRegex = Regex("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?]")
    private val offsetRegex = Regex("(?i)\\[offset:([+-]?\\d+)]")

    fun parse(lrc: String?): List<LyricLine> {
        if (lrc.isNullOrBlank()) return emptyList()

        val offsetMs = offsetRegex.find(lrc)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 0L

        return lrc.lineSequence()
            .flatMap { line ->
                val timestamps = timestampRegex.findAll(line).toList()
                if (timestamps.isEmpty()) {
                    emptySequence()
                } else {
                    val lyricText = line.substring(timestamps.last().range.last + 1).trim()
                    if (lyricText.isEmpty()) {
                        emptySequence()
                    } else {
                        timestamps.asSequence().mapNotNull { match ->
                            val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                            val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                            if (seconds > 59L) return@mapNotNull null
                            val fraction = match.groupValues.getOrNull(3).orEmpty()
                            val millis = fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                            val time = (minutes * 60_000L + seconds * 1_000L + millis + offsetMs)
                                .coerceAtLeast(0L)
                            LyricLine(timestampMs = time, text = lyricText)
                        }
                    }
                }
            }
            .sortedWith(compareBy<LyricLine> { it.timestampMs }.thenBy { it.text })
            .toList()
    }
}
