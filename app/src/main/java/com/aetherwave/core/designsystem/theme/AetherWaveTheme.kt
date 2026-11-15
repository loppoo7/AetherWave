package com.aetherwave.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun AetherWaveTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AetherWaveDarkColors else AetherWaveLightColors,
        content = content,
    )
}
