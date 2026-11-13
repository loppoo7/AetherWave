package com.aetherwave.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun AetherWaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AetherWaveDarkColors,
        content = content,
    )
}
