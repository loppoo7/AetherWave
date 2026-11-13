package com.aetherwave.core.designsystem.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class ArtworkPalette(
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
) {
    companion object {
        val Default = ArtworkPalette(
            primary = Color(0xFF5367D8),
            secondary = Color(0xFF7B3F9D),
            highlight = Color(0xFF42A6B9),
        )
    }
}

/**
 * Extracts a small palette off the main thread. Pass a decoded album-art Bitmap;
 * the bitmap is not copied or retained beyond this composition state.
 */
@Composable
fun rememberArtworkPalette(artwork: Bitmap?): ArtworkPalette {
    val palette by produceState(
        initialValue = ArtworkPalette.Default,
        key1 = artwork,
    ) {
        if (artwork == null || artwork.isRecycled) {
            value = ArtworkPalette.Default
        } else {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    Palette.from(artwork)
                        .clearFilters()
                        .maximumColorCount(12)
                        .generate()
                }.getOrNull()?.let { extracted ->
                    val vibrant = extracted.vibrantSwatch?.rgb
                    val muted = extracted.mutedSwatch?.rgb
                    val darkVibrant = extracted.darkVibrantSwatch?.rgb
                    val dominant = extracted.dominantSwatch?.rgb
                    ArtworkPalette(
                        primary = Color(vibrant ?: dominant ?: 0xFF5367D8.toInt()),
                        secondary = Color(muted ?: darkVibrant ?: 0xFF7B3F9D.toInt()),
                        highlight = Color(extracted.lightVibrantSwatch?.rgb ?: 0xFF42A6B9.toInt()),
                    )
                } ?: ArtworkPalette.Default
            }
        }
    }
    return palette
}

@Composable
fun rememberLiquidGlassState(): HazeState = rememberHazeState()

fun Modifier.liquidGlassSource(state: HazeState): Modifier = hazeSource(state = state)

/**
 * Applies Haze's source-backed Glass effect and a restrained 1 dp specular stroke.
 * Put [liquidGlassSource] on the artwork/background that should appear behind it.
 */
@Composable
fun Modifier.liquidGlass(
    state: HazeState,
    glassTint: Color = Color.White.copy(alpha = 0.12f),
    containerShape: RoundedCornerShape = RoundedCornerShape(28.dp),
    specularStrength: Float = 0.58f,
): Modifier {
    val safeSpecular = specularStrength.coerceIn(0f, 1f)
    val style = remember(glassTint, containerShape, safeSpecular) {
        GlassStyle.regular.then {
            tint(glassTint)
            shape(containerShape)
            specularIntensity(safeSpecular)
            ambientResponse(0.18f)
            edgeSoftness(1.dp)
            optics {
                copy(refractionDetailIntensity = 0.12f)
            }
        }
    }
    val specularBorder = remember(glassTint) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.72f),
                Color.White.copy(alpha = 0.10f),
                glassTint.copy(alpha = 0.42f),
            ),
        )
    }

    return this
        .clip(containerShape)
        .hazeGlass(
            input = HazeInput.Sources(state),
            style = style,
            performanceMode = HazePerformanceMode.Default,
        )
        .border(
            border = BorderStroke(width = 1.dp, brush = specularBorder),
            shape = containerShape,
        )
}

/** Small reusable glass element for controls or compact status chips. */
@Composable
fun LiquidGlass(
    modifier: Modifier = Modifier,
    state: HazeState,
    tint: Color = Color.White.copy(alpha = 0.10f),
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier.liquidGlass(
            state = state,
            glassTint = tint,
            containerShape = RoundedCornerShape(50),
            specularStrength = 0.52f,
        ),
        content = content,
    )
}

/**
 * Low-cost album-art mesh. Only two normalized phase values animate; gradients are
 * rasterized by Compose's drawing pipeline and do not create a per-frame bitmap.
 */
@Composable
fun DynamicMeshBackground(
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "aetherwave-mesh")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mesh-phase",
    )
    val counterPhase by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mesh-counter-phase",
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = (width * width + height * height).let { kotlin.math.sqrt(it) } * 0.78f

        drawRect(color = Color(0xFF080A14))

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to palette.primary.copy(alpha = 0.62f),
                    0.48f to palette.primary.copy(alpha = 0.24f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(
                    x = width * (0.18f + 0.34f * phase),
                    y = height * (0.20f + 0.18f * counterPhase),
                ),
                radius = radius,
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to palette.secondary.copy(alpha = 0.54f),
                    0.52f to palette.secondary.copy(alpha = 0.20f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(
                    x = width * (0.86f - 0.25f * counterPhase),
                    y = height * (0.72f - 0.28f * phase),
                ),
                radius = radius * 0.82f,
            ),
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to palette.highlight.copy(alpha = 0.32f),
                    0.58f to palette.highlight.copy(alpha = 0.10f),
                    1.0f to Color.Transparent,
                ),
                center = Offset(
                    x = width * (0.42f + 0.20f * counterPhase),
                    y = height * (0.96f - 0.32f * phase),
                ),
                radius = radius * 0.62f,
            ),
        )
    }
}
