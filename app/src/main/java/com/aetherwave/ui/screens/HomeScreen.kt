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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aetherwave.core.designsystem.components.AwGlyph
import com.aetherwave.R
import androidx.compose.ui.res.stringResource
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.domain.model.MusicPlaylist
import com.aetherwave.ui.components.PlaylistPickerDialog
import com.aetherwave.ui.components.TrackRow
import com.aetherwave.ui.viewmodel.HomeUiState
import dev.chrisbanes.haze.HazeState

@Composable
fun HomeScreen(
    state: HomeUiState,
    hazeState: HazeState,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlayQueue: (List<MusicTrack>, Int) -> Unit,
    playlists: List<MusicPlaylist>,
    onAddToPlaylist: (String, MusicTrack) -> Unit,
    onOpenLibrary: (Int) -> Unit,
) {
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.greet_morning)
        in 12..16 -> stringResource(R.string.greet_afternoon)
        in 17..21 -> stringResource(R.string.greet_evening)
        else -> stringResource(R.string.greet_night)
    }
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
                    Text(greeting, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                AwGlyph(
                    "⚙",
                    color = foreground,
                    iconSize = 22.dp,
                    textSize = 21.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenSettings).padding(9.dp),
                )
                AwGlyph(
                    "⌕",
                    color = foreground,
                    iconSize = 22.dp,
                    textSize = 26.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpenSearch).padding(7.dp),
                )
            }
        }
        item { SectionTitle(stringResource(R.string.home_quick_picks), stringResource(R.string.home_quick_picks_sub), foreground, muted) }
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
                            onAddToPlaylist = { selectedTrack = track },
                            onPlayNow = { onPlayQueue(listOf(track), 0) },
                        )
                    }
                }
            }
        }
        if (state.trending.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_trending), stringResource(R.string.home_trending_sub), foreground, muted) }
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
            item { SectionTitle(stringResource(R.string.home_recommended), stringResource(R.string.home_recommended_sub), foreground, muted) }
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
    selectedTrack?.let { track ->
        PlaylistPickerDialog(
            playlists = playlists,
            hazeState = hazeState,
            onDismiss = { selectedTrack = null },
            onSelect = { playlist ->
                onAddToPlaylist(playlist.id, track)
                selectedTrack = null
            },
        )
    }
}

@Composable
private fun QuickPickItem(
    track: MusicTrack,
    foreground: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNow: () -> Unit,
) {
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
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
        Box {
            Text("⋮", color = muted, fontSize = 19.sp, modifier = Modifier.clickable { menuExpanded = true }.padding(horizontal = 5.dp, vertical = 2.dp))
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Play now") },
                    onClick = { menuExpanded = false; onPlayNow() },
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = { menuExpanded = false; onAddToPlaylist() },
                )
            }
        }
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

