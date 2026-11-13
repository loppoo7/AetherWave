package com.aetherwave.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.ui.components.TrackRow
import com.aetherwave.ui.viewmodel.HomeUiState
import dev.chrisbanes.haze.HazeState

@Composable
fun HomeScreen(
    state: HomeUiState,
    hazeState: HazeState,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlayQueue: (List<MusicTrack>, Int) -> Unit,
    onOpenLibrary: (Int) -> Unit,
) {
    val foreground = MaterialTheme.colorScheme.onBackground
    val muted = foreground.copy(alpha = 0.62f)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .liquidGlass(hazeState, MaterialTheme.colorScheme.surface.copy(alpha = 0.66f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AETHERWAVE", color = muted, fontSize = 9.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("Your sound, flowing.", color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (darkTheme) "☀" else "☾",
                    color = foreground,
                    fontSize = 22.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onToggleTheme).padding(9.dp),
                )
                Text(
                    "⌕",
                    color = foreground,
                    fontSize = 26.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenSearch).padding(7.dp),
                )
            }
        }
        item { SectionTitle("Quick Picks", "Made for your next listen", foreground, muted) }
        if (state.isLoading && state.quickPicks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                }
            }
        } else if (state.quickPicks.isEmpty()) {
            item { EmptyHint(state.error ?: "YouTube Music recommendations will appear here.", muted) }
        } else {
            item {
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(316.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.quickPicks, key = { it.id }) { track ->
                        QuickPickItem(
                            track = track,
                            foreground = foreground,
                            muted = muted,
                            onClick = { onPlayQueue(state.quickPicks, state.quickPicks.indexOf(track)) },
                        )
                    }
                }
            }
        }
        if (state.trending.isNotEmpty()) {
            item { SectionTitle("Trending", "Songs people are listening to", foreground, muted) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    itemsIndexed(state.trending, key = { _, track -> track.id }) { index, track ->
                        FeaturedTrackCard(track, hazeState, foreground, muted, "TRENDING", Modifier.width(176.dp)) {
                            onPlayQueue(state.trending, index)
                        }
                    }
                }
            }
        }
        if (state.recommended.isNotEmpty()) {
            item { SectionTitle("Recommended", "Picked from YouTube Music", foreground, muted) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 4.dp)) {
                    itemsIndexed(state.recommended, key = { _, track -> track.id }) { index, track ->
                        FeaturedTrackCard(track, hazeState, foreground, muted, "FOR YOU", Modifier.width(176.dp)) {
                            onPlayQueue(state.recommended, index)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recently Played", "Pick up where you left off", foreground, muted)
                Text(
                    "LIBRARY",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenLibrary(0) }.padding(6.dp),
                )
            }
        }
        if (state.recentlyPlayed.isEmpty()) {
            item { EmptyHint("Your listening history will appear here.", muted) }
        } else {
            itemsIndexed(state.recentlyPlayed, key = { _, track -> track.id }) { index, track ->
                TrackRow(track, onPlay = { onPlayQueue(state.recentlyPlayed, index) })
            }
        }
        state.error?.takeIf { state.quickPicks.isNotEmpty() }?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun QuickPickItem(
    track: MusicTrack,
    foreground: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.width(258.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = track.title,
            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("▶", color = muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 3.dp))
    }
}

@Composable
private fun FeaturedTrackCard(
    track: MusicTrack,
    hazeState: HazeState,
    foreground: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
    eyebrow: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(modifier.height(218.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = track.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .liquidGlass(hazeState, MaterialTheme.colorScheme.surface.copy(alpha = 0.70f), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(eyebrow, color = MaterialTheme.colorScheme.primary, fontSize = 8.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
            Text(track.title, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    foreground: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = foreground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = muted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyHint(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), color = color, fontSize = 12.sp)
}
