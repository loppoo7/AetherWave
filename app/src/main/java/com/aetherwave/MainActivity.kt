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
import com.aetherwave.core.designsystem.theme.AccentPalette
import com.aetherwave.core.locale.AppLocale
import android.content.Context
import com.aetherwave.ui.navigation.AetherWaveApp
import dagger.hilt.android.AndroidEntryPoint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.aetherwave.core.island.DynamicIslandOverlay
import com.aetherwave.playback.PlaybackController
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playbackController: PlaybackController

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        DynamicIslandOverlay.hide()
    }

    override fun onStop() {
        super.onStop()
        val enabled = getSharedPreferences("aetherwave_preferences", MODE_PRIVATE).getBoolean("dynamic_island", false)
        if (enabled && Settings.canDrawOverlays(this)) {
            DynamicIslandOverlay.show(this, playbackController)
        }
    }

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
            AetherWaveTheme(
                darkTheme = darkTheme,
                accent = AccentPalette.fromKey(preferences.getString("accent", "default")),
                materialYou = true,
            ) {
                AetherWaveApp(
                    darkTheme = darkTheme,
                    language = preferences.getString(AppLocale.KEY, AppLocale.SYSTEM) ?: AppLocale.SYSTEM,
                    materialYou = true,
                    onMaterialYouChanged = { _ -> },
                    dynamicIsland = preferences.getBoolean("dynamic_island", false),
                    islandX = com.aetherwave.core.island.IslandPrefs.read(this).first,
                    islandY = com.aetherwave.core.island.IslandPrefs.read(this).second,
                    onIslandPositionChanged = { x, y ->
                        com.aetherwave.core.island.IslandPrefs.write(this@MainActivity, x, y)
                    },
                    islandStyle = com.aetherwave.core.island.IslandPrefs.readLook(this).style,
                    islandCorner = com.aetherwave.core.island.IslandPrefs.readLook(this).corner,
                    onIslandLookChanged = { style, corner ->
                        com.aetherwave.core.island.IslandPrefs.writeLook(this@MainActivity, style, corner)
                    },
                    onDynamicIslandChanged = { enabled ->
                        if (enabled && !Settings.canDrawOverlays(this@MainActivity)) {
                            // Send the user to the permission screen. The setting is saved once permission is granted.
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                            )
                        } else {
                            preferences.edit().putBoolean("dynamic_island", enabled).apply()
                            if (!enabled) DynamicIslandOverlay.hide()
                            recreate()
                        }
                    },
                    accent = preferences.getString("accent", "default") ?: "default",
                    onAccentChanged = { key ->
                        preferences.edit().putString("accent", key).apply()
                        recreate()
                    },
                    onLanguageChanged = { tag ->
                        preferences.edit().putString(AppLocale.KEY, tag).apply()
                        recreate()
                    },
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
