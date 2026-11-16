package com.aetherwave.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.size

/**
 * Icon set for the whole app. Each glyph string used by the screens maps to one Material Rounded
 * icon, so every button uses the same stroke weight, corner style and optical size.
 * Glyphs without an icon fall back to text, so nothing disappears.
 */
private val GLYPH_ICONS: Map<String, ImageVector> by lazy {
    mapOf(
        "⌂" to Icons.Rounded.Home,
        "⌕" to Icons.Rounded.Search,
        "✦" to Icons.Rounded.AutoAwesome,
        "▦" to Icons.Rounded.GridView,
        "⚙" to Icons.Rounded.Settings,
        "⌄" to Icons.Rounded.KeyboardArrowDown,
        "⋯" to Icons.Rounded.MoreHoriz,
        "⋮" to Icons.Rounded.MoreVert,
        "⤨" to Icons.Rounded.Shuffle,
        "|◀" to Icons.Rounded.SkipPrevious,
        "▶|" to Icons.Rounded.SkipNext,
        "↻" to Icons.Rounded.Repeat,
        "↻1" to Icons.Rounded.RepeatOne,
        "♥" to Icons.Rounded.Favorite,
        "♡" to Icons.Rounded.FavoriteBorder,
        "↓" to Icons.Rounded.Download,
        "↑" to Icons.Rounded.ArrowUpward,
        "≋" to Icons.Rounded.Notes,
        "☰" to Icons.Rounded.QueueMusic,
        "⇪" to Icons.Rounded.Share,
        "▶" to Icons.Rounded.PlayArrow,
        "Ⅱ" to Icons.Rounded.Pause,
        "✕" to Icons.Rounded.Close,
    )
}

/**
 * Draws [glyph] as a Material Rounded icon tinted with [color]. Glyphs without an icon fall back
 * to the original text.
 */
@Composable
fun AwGlyph(
    glyph: String,
    color: Color,
    iconSize: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val icon = GLYPH_ICONS[glyph]
    if (icon == null) {
        Text(glyph, color = color, fontSize = textSize, modifier = modifier)
    } else {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = modifier.size(iconSize),
        )
    }
}
