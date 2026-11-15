package com.aetherwave.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

@Immutable
data class LyricsResult(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val durationSeconds: Double?,
    val plainLyrics: String?,
    val syncedLines: List<LyricLine>,
    val instrumental: Boolean,
)
