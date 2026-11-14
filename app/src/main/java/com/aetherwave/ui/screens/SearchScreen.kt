package com.aetherwave.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
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
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.domain.model.MusicPlaylist
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.ui.components.PlaylistPickerDialog
import com.aetherwave.ui.components.TrackRow
import com.aetherwave.ui.viewmodel.SearchUiState
import dev.chrisbanes.haze.HazeState

@Composable
fun SearchScreen(
    state: SearchUiState,
    playlists: List<MusicPlaylist>,
    hazeState: HazeState,
    playbackError: String? = null,
    onQueryChanged: (String) -> Unit,
    onPlayQueue: (List<MusicTrack>, Int) -> Unit,
    onAddToPlaylist: (String, MusicTrack) -> Unit,
) {
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    val focusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("DISCOVER", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f), fontSize = 10.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
            Text("Find your next wave.", color = MaterialTheme.colorScheme.onBackground, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth().liquidGlass(hazeState, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.11f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 25.sp)
            TextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search YouTube Music songs...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFFC6C9FF),
                ),
            )
        }
        if (state.isLoading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Color(0xFFC6C9FF), strokeWidth = 2.dp) }
        }
        state.error?.let { Text(it, color = Color(0xFFFFB7B7), fontSize = 12.sp) }
        playbackError?.let { Text("Playback: $it", color = Color(0xFFFFB7B7), fontSize = 12.sp) }
        if (state.hasSearched && !state.isLoading && state.error == null && state.results.isEmpty()) {
            Text("No tracks found. Try another title or artist.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f), fontSize = 13.sp)
        }
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(state.results, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    onPlay = { onPlayQueue(state.results, index) },
                    onAdd = { selectedTrack = track },
                    trailingLabel = "${track.durationMs.div(60_000)} min  •  OPUS / AAC STREAM",
                )
            }
        }
    }
    selectedTrack?.let { track ->
        PlaylistPickerDialog(
            playlists = playlists,
            hazeState = hazeState,
            onDismiss = { selectedTrack = null },
            onSelect = { playlist -> onAddToPlaylist(playlist.id, track); selectedTrack = null },
        )
    }
}
