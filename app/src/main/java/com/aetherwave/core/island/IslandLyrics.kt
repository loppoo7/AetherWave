package com.aetherwave.core.island

import com.aetherwave.domain.model.LyricLine
import kotlinx.coroutines.flow.MutableStateFlow

/** The synced lyrics of the song that is playing, shared with the Dynamic Island overlay. */
object IslandLyrics {
    val lines = MutableStateFlow<List<LyricLine>>(emptyList())
}
