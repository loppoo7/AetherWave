package com.aetherwave.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.ui.components.TrackRow
import com.aetherwave.ui.viewmodel.HomeUiState
import com.aetherwave.ui.viewmodel.LibraryUiState
import dev.chrisbanes.haze.HazeState

private val libraryTabs = listOf("Playlists", "Local Files", "Downloads", "Liked")

@Composable
fun LibraryScreen(
    initialTab: Int,
    state: LibraryUiState,
    homeState: HomeUiState,
    hazeState: HazeState,
    onTabSelected: (Int) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onPlayQueue: (List<MusicTrack>, Int) -> Unit,
    onRemoveTrack: (String, String) -> Unit,
    onRefreshLocal: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var expandedPlaylist by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("YOUR COLLECTION", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f), fontSize = 10.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Text("Library", color = MaterialTheme.colorScheme.onBackground, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            libraryTabs.forEachIndexed { index, title ->
                val selected = initialTab == index
                Text(
                    title,
                    modifier = Modifier.weight(1f).liquidGlass(
                        hazeState,
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                        RoundedCornerShape(14.dp),
                    ).clickable { onTabSelected(index) }.padding(vertical = 11.dp),
                    color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f), fontSize = 11.sp) }
        if (initialTab == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Your playlists", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("+ NEW PLAYLIST", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    modifier = Modifier.clickable { showCreateDialog = true }.padding(8.dp))
            }
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.playlists.isEmpty()) item { EmptyLibraryHint("Create a playlist to start organizing your music.") }
                items(state.playlists, key = { it.id }) { playlist ->
                    Column(Modifier.fillMaxWidth().liquidGlass(hazeState, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { expandedPlaylist = if (expandedPlaylist == playlist.id) null else playlist.id }) {
                                Text(playlist.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${playlist.tracks.size} tracks", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 11.sp)
                            }
                            Text("DELETE", color = Color(0xFFFFB7B7), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onDeletePlaylist(playlist.id) }.padding(8.dp))
                        }
                        if (expandedPlaylist == playlist.id) {
                            playlist.tracks.forEachIndexed { index, track ->
                                TrackRow(track, onPlay = { onPlayQueue(playlist.tracks, index) }, onAdd = { onRemoveTrack(playlist.id, track.id) }, trailingLabel = "REMOVE")
                            }
                        }
                    }
                }
            }
        } else {
            if (initialTab == 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (homeState.isScanning) "SCANNING…" else "SCAN DEVICE",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable(onClick = onRefreshLocal).padding(8.dp),
                    )
                }
            }
            val tracks = when (initialTab) {
                1 -> homeState.localAudio
                2 -> state.downloads
                else -> state.likedSongs
            }
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tracks.isEmpty()) item {
                    EmptyLibraryHint(when (initialTab) {
                        1 -> "No local files found. Grant audio permission and scan your device."
                        2 -> "Offline downloads will appear here."
                        else -> "Tracks you like will appear here."
                    })
                }
                itemsIndexed(tracks, key = { _, track -> "${initialTab}-${track.id}" }) { index, track ->
                    TrackRow(track, onPlay = { onPlayQueue(tracks, index) }, trailingLabel = when (initialTab) {
                        1 -> "LOCAL AUDIO"
                        2 -> "OFFLINE READY"
                        else -> "LIKED"
                    })
                }
            }
        }
    }
    if (showCreateDialog) {
        CreatePlaylistDialog(hazeState, onDismiss = { showCreateDialog = false }, onCreate = { name -> onCreatePlaylist(name); showCreateDialog = false })
    }
}

@Composable
private fun CreatePlaylistDialog(hazeState: HazeState, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().liquidGlass(hazeState, MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), RoundedCornerShape(26.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("New playlist", color = MaterialTheme.colorScheme.onBackground, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(48) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().liquidGlass(hazeState, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(13.dp)).padding(13.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp),
                decorationBox = { inner -> if (name.isEmpty()) Text("Playlist name", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 14.sp); inner() },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("CANCEL", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f), fontSize = 10.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp))
                Text("CREATE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp,
                    modifier = Modifier.clickable { if (name.isNotBlank()) onCreate(name.trim()) }.padding(10.dp))
            }
        }
    }
}

@Composable
private fun EmptyLibraryHint(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 25.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 13.sp)
}
