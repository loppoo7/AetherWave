package com.aetherwave.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Shown in the lyrics during an instrumental break. A music note fills from the bottom up as the
 * break plays out, grows slightly while it is active, and stays faint otherwise.
 */
@Composable
fun InstrumentalBreak(
    startMs: Long,
    endMs: Long,
    position: Long,
    modifier: Modifier = Modifier,
) {
    val painter = rememberVectorPainter(Icons.Rounded.MusicNote)
    val color = MaterialTheme.colorScheme.onBackground
    val duration = (endMs - startMs).coerceAtLeast(1L)
    val progress = ((position - startMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val active = position in startMs until endMs
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.9f,
        animationSpec = tween(durationMillis = 220),
        label = "instrumentalScale",
    )

    Box(modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            with(painter) { draw(size = size, alpha = 0.28f, colorFilter = ColorFilter.tint(color)) }
            if (progress > 0f) {
                clipRect(
                    left = 0f,
                    top = size.height * (1f - progress),
                    right = size.width,
                    bottom = size.height,
                ) {
                    with(painter) { draw(size = size, alpha = 1f, colorFilter = ColorFilter.tint(color)) }
                }
            }
        }
    }
}
