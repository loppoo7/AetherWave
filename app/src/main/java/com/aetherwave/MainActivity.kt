package com.aetherwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aetherwave.core.designsystem.theme.AetherWaveTheme
import com.aetherwave.ui.navigation.AetherWaveApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("aetherwave_preferences", MODE_PRIVATE)
        setContent {
            var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", true)) }
            SideEffect { preferences.edit().putBoolean("dark_theme", darkTheme).apply() }
            AetherWaveTheme(darkTheme = darkTheme) {
                AetherWaveApp(
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme },
                )
            }
        }
    }
}
