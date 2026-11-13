package com.aetherwave.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AetherWaveDarkColors = darkColorScheme(
    primary = Color(0xFFB6BEFF),
    onPrimary = Color(0xFF11142D),
    secondary = Color(0xFFB9A6E8),
    onSecondary = Color(0xFF201A35),
    background = Color(0xFF080A14),
    onBackground = Color(0xFFF5F5FC),
    surface = Color(0xFF111522),
    onSurface = Color(0xFFF5F5FC),
    surfaceVariant = Color(0xFF242A3A),
    onSurfaceVariant = Color(0xFFD0D3E0),
)

private val AetherWaveLightColors = lightColorScheme(
    primary = Color(0xFF4E5AA8),
    onPrimary = Color.White,
    secondary = Color(0xFF65518D),
    onSecondary = Color.White,
    background = Color(0xFFF1F3F9),
    onBackground = Color(0xFF171A26),
    surface = Color(0xFFF9FAFE),
    onSurface = Color(0xFF171A26),
    surfaceVariant = Color(0xFFE2E6F0),
    onSurfaceVariant = Color(0xFF454B5A),
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
