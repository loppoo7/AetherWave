package com.aetherwave.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.domain.model.MusicPlaylist
import dev.chrisbanes.haze.HazeState

@Composable
fun PlaylistPickerDialog(
    playlists: List<MusicPlaylist>,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onSelect: (MusicPlaylist) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .liquidGlass(hazeState, MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), RoundedCornerShape(28.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add to playlist", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            if (playlists.isEmpty()) {
                Text("Create a playlist in Library first.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f), fontSize = 13.sp)
            } else {
                playlists.forEach { playlist ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(playlist) }.padding(vertical = 8.dp)) {
                        Text(playlist.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                    }
                }
            }
            Text("CLOSE", modifier = Modifier.clickable(onClick = onDismiss).padding(top = 6.dp), color = Color(0xFFC6C9FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
