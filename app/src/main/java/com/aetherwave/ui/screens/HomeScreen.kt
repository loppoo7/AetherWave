package com.aetherwave.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.ui.components.TrackRow
import com.aetherwave.ui.viewmodel.HomeUiState
import dev.chrisbanes.haze.HazeState

@Composable
fun HomeScreen(
    state: HomeUiState,
    hazeState: HazeState,
    onPlayQueue: (List<MusicTrack>, Int) -> Unit,
    onOpenLibrary: (Int) -> Unit,
    onRefreshLocal: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("AETHERWAVE", color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                Text("Sound, in motion.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Good music. Zero friction.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickCard("♡", "Liked Songs", "${state.likedSongs.size} tracks", hazeState, Modifier.weight(1f)) { onOpenLibrary(3) }
                QuickCard("◈", "Local Audio", "${state.localAudio.size} files", hazeState, Modifier.weight(1f)) { onOpenLibrary(1) }
                QuickCard("↓", "Downloads", "Offline", hazeState, Modifier.weight(1f)) { onOpenLibrary(2) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recently Played", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (state.isScanning) CircularProgressIndicator(color = Color(0xFFC6C9FF), strokeWidth = 2.dp)
                else Text("SCAN DEVICE", color = Color(0xFFC6C9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.liquidGlass(hazeState, Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)
                        .then(Modifier).padding(0.dp).clickableNoRipple(onRefreshLocal))
            }
        }
        if (state.recentlyPlayed.isEmpty()) {
            item { EmptyHint("Your recent plays will appear here.") }
        } else {
            itemsIndexed(state.recentlyPlayed, key = { _, track -> "recent-${track.id}" }) { index, track ->
                TrackRow(track, onPlay = { onPlayQueue(state.recentlyPlayed, index) })
            }
        }
        if (state.localAudio.isNotEmpty()) {
            item { Text("On this device", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            itemsIndexed(state.localAudio.take(8), key = { _, track -> "local-${track.id}" }) { index, track ->
                val localTracks = state.localAudio.take(8)
                TrackRow(track, onPlay = { onPlayQueue(localTracks, index) }, trailingLabel = "LOCAL AUDIO")
            }
        }
        state.error?.let { message -> item { Text(message, color = Color(0xFFFFB7B7), fontSize = 12.sp) } }
    }
}

@Composable
private fun QuickCard(icon: String, title: String, subtitle: String, hazeState: HazeState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.liquidGlass(hazeState, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
            .clickableNoRipple(onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(icon, color = Color(0xFFD1D2FF), fontSize = 23.sp)
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(subtitle, color = Color.White.copy(alpha = 0.56f), fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), color = Color.White.copy(alpha = 0.56f), fontSize = 13.sp)
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
