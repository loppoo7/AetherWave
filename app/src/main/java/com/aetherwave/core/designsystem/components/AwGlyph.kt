package com.aetherwave.core.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Icon drawn from a 24x24 SVG path. Stroke icons use [strokeWidth]; filled icons use the fill.
 * Paths are the same outlines used in the design preview, so the app and the mockup match.
 */
private class GlyphSpec(
    val path: String,
    val filled: Boolean = false,
    val strokeWidth: Float = 1.8f,
)

private const val REPEAT_PATH = "M17 1l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3"
private const val HEART_PATH = "M12 21s-7-4.5-9.5-9A5.5 5.5 0 0 1 12 6a5.5 5.5 0 0 1 9.5 6c-2.5 4.5-9.5 9-9.5 9z"
private const val NOTE_PATH = "M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zm12-2a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"

/** Maps the glyph strings used across the screens to icons. Anything not listed stays as text. */
private val GLYPHS: Map<String, GlyphSpec> = mapOf(
    "⌂" to GlyphSpec("M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"),
    "⌕" to GlyphSpec("M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0zM21 21l-4.3-4.3"),
    "✦" to GlyphSpec("M12 3l2.4 6.6L21 12l-6.6 2.4L12 21l-2.4-6.6L3 12l6.6-2.4z"),
    "▦" to GlyphSpec("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"),
    "⚙" to GlyphSpec("M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"),
    "⌄" to GlyphSpec("M6 9l6 6 6-6"),
    "⋯" to GlyphSpec("M5 12h.01M12 12h.01M19 12h.01", strokeWidth = 3.2f),
    "⤨" to GlyphSpec("M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5"),
    "|◀" to GlyphSpec("M6 6h2v12H6zM18 6v12l-9-6z", filled = true),
    "▶|" to GlyphSpec("M16 6h2v12h-2zM6 6v12l9-6z", filled = true),
    "↻" to GlyphSpec(REPEAT_PATH),
    "↻1" to GlyphSpec(REPEAT_PATH),
    "♥" to GlyphSpec(HEART_PATH, filled = true),
    "♡" to GlyphSpec(HEART_PATH),
    "↓" to GlyphSpec("M12 3v12m0 0l-4-4m4 4l4-4M4 19h16"),
    "≋" to GlyphSpec(NOTE_PATH),
    "☰" to GlyphSpec("M4 6h16M4 12h16M4 18h10"),
    "⇪" to GlyphSpec("M4 12v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8M16 6l-4-4-4 4M12 2v13"),
    "▶" to GlyphSpec("M8 5v14l11-7z", filled = true),
    "Ⅱ" to GlyphSpec("M6 5h4v14H6zM14 5h4v14h-4z", filled = true),
)

private fun buildGlyph(name: String, spec: GlyphSpec): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(spec.path).toNodes(),
        fill = if (spec.filled) SolidColor(Color.Black) else null,
        stroke = if (spec.filled) null else SolidColor(Color.Black),
        strokeLineWidth = if (spec.filled) 0f else spec.strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

/**
 * Draws [glyph] as a vector icon tinted with [color]. Glyphs without an icon fall back to the
 * original text, so nothing disappears if a new glyph is added without a mapping.
 */
@Composable
fun AwGlyph(
    glyph: String,
    color: Color,
    iconSize: Dp,
    textSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val spec = GLYPHS[glyph]
    if (spec == null) {
        Text(glyph, color = color, fontSize = textSize, modifier = modifier)
    } else {
        val icon = remember(glyph) { buildGlyph(glyph, spec) }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = modifier.size(iconSize),
        )
    }
}
