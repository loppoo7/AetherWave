package com.aetherwave.core.designsystem.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
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

/** Liquid Glass is opt-in; standard surfaces are used unless the user enables it. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/**
 * Glass strength tiers, modelled on the three tiers in the reference app. G1 is the clearest and
 * thinnest, G3 the most opaque with the brightest rim.
 */
enum class GlassTier(val rimWidth: Dp, val tintScale: Float) {
    G1(rimWidth = 0.5.dp, tintScale = 0.85f),
    G2(rimWidth = 0.75.dp, tintScale = 1f),
    G3(rimWidth = 1.dp, tintScale = 1.15f),
}

@Immutable
data class ArtworkPalette(
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
) {
    companion object {
        val Default = ArtworkPalette(
            primary = Color(0xFF3A3F4D),
            secondary = Color(0xFF22252F),
            highlight = Color(0xFF4A4F5E),
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
                        primary = Color(vibrant ?: dominant ?: 0xFF3A3F4D.toInt()),
                        secondary = Color(muted ?: darkVibrant ?: 0xFF22252F.toInt()),
                        highlight = Color(extracted.lightVibrantSwatch?.rgb ?: 0xFF4A4F5E.toInt()),
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
    tier: GlassTier = GlassTier.G2,
): Modifier {
    val safeSpecular = specularStrength.coerceIn(0f, 1f)
    val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.45f
    if (!LocalLiquidGlassEnabled.current) {
        return this
            .shadow(4.dp, containerShape, clip = false)
            .clip(containerShape)
            .background(MaterialTheme.colorScheme.surface, containerShape)
            .border(
                BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                containerShape,
            )
    }
    val baseTint = if (darkSurface) Color(0xFF101827).copy(alpha = 0.54f) else Color.White.copy(alpha = 0.58f)
    val resolvedTint = lerp(glassTint, baseTint, if (darkSurface) 0.72f else 0.64f)
        .let { it.copy(alpha = (it.alpha * tier.tintScale).coerceIn(0f, 1f)) }
    val style = remember(resolvedTint, containerShape, safeSpecular) {
        GlassStyle.regular.then {
            tint(resolvedTint)
            shape(containerShape)
            specularIntensity(safeSpecular)
            ambientResponse(0.18f)
            edgeSoftness(1.dp)
            optics {
                copy(refractionDetailIntensity = 0.12f)
            }
        }
    }
    // Micro-specular edge: bright at the upper-left, fading through the middle, faint lower-right.
    val specularBorder = remember(darkSurface) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = if (darkSurface) 0.35f else 0.55f),
                0.5f to Color.White.copy(alpha = 0.05f),
                1.0f to Color.White.copy(alpha = if (darkSurface) 0.15f else 0.28f),
            ),
        )
    }
    val innerHighlight = remember(darkSurface) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (darkSurface) 0.13f else 0.38f),
                Color.Transparent,
                Color.Black.copy(alpha = if (darkSurface) 0.12f else 0.025f),
            ),
        )
    }

    return this
        .shadow(if (darkSurface) 18.dp else 10.dp, containerShape, clip = false)
        .clip(containerShape)
        .background(innerHighlight, containerShape)
        .hazeGlass(
            input = HazeInput.Sources(state),
            style = style,
            performanceMode = HazePerformanceMode.Default,
        )
        .border(
            border = BorderStroke(width = tier.rimWidth, brush = specularBorder),
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
    // Fixed phases: an infinite animation redrew the whole screen every frame and made navigation lag.
    val phase = 0.5f
    val counterPhase = 0.5f

    val baseBackground = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val radius = (width * width + height * height).let { kotlin.math.sqrt(it) } * 0.78f

        drawRect(color = baseBackground)

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to palette.primary.copy(alpha = 0.34f),
                    0.48f to palette.primary.copy(alpha = 0.14f),
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
                    0.0f to palette.secondary.copy(alpha = 0.30f),
                    0.52f to palette.secondary.copy(alpha = 0.12f),
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
                    0.0f to palette.highlight.copy(alpha = 0.18f),
                    0.58f to palette.highlight.copy(alpha = 0.06f),
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
