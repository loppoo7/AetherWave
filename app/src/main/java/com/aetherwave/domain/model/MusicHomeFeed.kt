package com.aetherwave.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class MusicHomeFeed(
    val quickPicks: List<MusicTrack> = emptyList(),
    val trending: List<MusicTrack> = emptyList(),
    val recommended: List<MusicTrack> = emptyList(),
)
