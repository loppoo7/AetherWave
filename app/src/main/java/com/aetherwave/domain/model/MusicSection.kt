package com.aetherwave.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class MusicSection(
    val title: String,
    val tracks: List<MusicTrack>,
)
