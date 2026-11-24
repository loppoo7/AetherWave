package com.aetherwave.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext

private val AetherWaveDarkColors = darkColorScheme(
    primary = Color(0xFFE8E8EE),
    onPrimary = Color(0xFF0A0A0C),
    secondary = Color(0xFFA9AAB6),
    onSecondary = Color(0xFF0A0A0C),
    background = Color(0xFF0B0C0F),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF15171C),
    onSurface = Color(0xFFF2F4F7),
    surfaceVariant = Color(0xFF1E2128),
    onSurfaceVariant = Color(0xFFB4BAC6),
    outlineVariant = Color(0xFF292D36),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF0B0C0F),
)

private val AetherWaveLightColors = lightColorScheme(
    primary = Color(0xFF1C1C22),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF5A5D68),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF0E1116),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0E1116),
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = Color(0xFF4A5160),
    outlineVariant = Color(0xFFDFE2E8),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
)

/** Accent colors for highlights. Light variants are darker so text stays readable on white. */
enum class AccentPalette(val key: String, val dark: Color, val light: Color) {
    DEFAULT("default", Color(0xFFE8E8EE), Color(0xFF1C1C22)),
    OCEAN("ocean", Color(0xFF7FB8FF), Color(0xFF1F5FA8)),
    MINT("mint", Color(0xFF7FE3C2), Color(0xFF1E7A5C)),
    AMBER("amber", Color(0xFFFFC86B), Color(0xFF9A6200)),
    ROSE("rose", Color(0xFFFF9AB5), Color(0xFFA3345E));

    companion object {
        fun fromKey(key: String?): AccentPalette = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

@Composable
fun AetherWaveTheme(
    darkTheme: Boolean = true,
    accent: AccentPalette = AccentPalette.DEFAULT,
    materialYou: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = if (materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Material You: colours come from the wallpaper. Needs Android 12 or newer.
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val base = if (darkTheme) AetherWaveDarkColors else AetherWaveLightColors
        if (accent == AccentPalette.DEFAULT) base else base.copy(
            primary = if (darkTheme) accent.dark else accent.light,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
