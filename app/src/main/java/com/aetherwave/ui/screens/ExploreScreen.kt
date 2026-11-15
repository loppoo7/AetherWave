package com.aetherwave.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.ui.components.TrackRow
import com.aetherwave.ui.viewmodel.ExploreUiState

@Composable
fun ExploreScreen(
    state: ExploreUiState,
    onPlayQueue: (List<MusicTrack>, Int) -> Unit,
) {
    val foreground = MaterialTheme.colorScheme.onBackground
    val muted = foreground.copy(alpha = 0.6f)
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("DISCOVER", color = muted, fontSize = 10.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Text("Explore", color = foreground, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.topPlayed.isNotEmpty()) {
                item(key = "top-played-title") {
                    Text("Your most played", color = foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(state.topPlayed, key = { _, track -> "top-${track.id}" }) { index, track ->
                    TrackRow(track, onPlay = { onPlayQueue(state.topPlayed, index) })
                }
            }
            state.sections.forEachIndexed { sectionIndex, section ->
                item(key = "section-$sectionIndex-title") {
                    Text(section.title, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(section.tracks, key = { index, track -> "s$sectionIndex-$index-${track.id}" }) { index, track ->
                    TrackRow(track, onPlay = { onPlayQueue(section.tracks, index) })
                }
            }
            if (state.isLoading) {
                item(key = "loading") { Text("Loading Explore…", color = muted, fontSize = 12.sp) }
            }
            state.error?.let { message ->
                item(key = "error") { Text(message, color = muted, fontSize = 12.sp) }
            }
        }
    }
}
