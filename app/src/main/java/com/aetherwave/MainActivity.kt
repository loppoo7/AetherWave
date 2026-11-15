package com.aetherwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aetherwave.core.crash.CrashReportDialog
import com.aetherwave.core.crash.CrashReporter
import com.aetherwave.core.designsystem.theme.AetherWaveTheme
import com.aetherwave.ui.navigation.AetherWaveApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("aetherwave_preferences", MODE_PRIVATE)
        val pendingCrash = CrashReporter.read(this)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var crashReport by remember { mutableStateOf(pendingCrash) }
            var themeMode by remember {
                mutableStateOf(preferences.getString("theme_mode", "system") ?: "system")
            }
            var liquidGlassEnabled by remember {
                mutableStateOf(preferences.getBoolean("liquid_glass_enabled", false))
            }
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            AetherWaveTheme(darkTheme = darkTheme) {
                AetherWaveApp(
                    darkTheme = darkTheme,
                    themeMode = themeMode,
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        preferences.edit().putString("theme_mode", mode).apply()
                    },
                    liquidGlassEnabled = liquidGlassEnabled,
                    onLiquidGlassChanged = { enabled ->
                        liquidGlassEnabled = enabled
                        preferences.edit().putBoolean("liquid_glass_enabled", enabled).apply()
                    },
                )
                crashReport?.let { report ->
                    CrashReportDialog(
                        report = report,
                        onDismiss = {
                            CrashReporter.clear(this@MainActivity)
                            crashReport = null
                        },
                    )
                }
            }
        }
    }
}
