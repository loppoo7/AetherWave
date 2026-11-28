package com.aetherwave

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aetherwave.core.crash.CrashReportDialog
import com.aetherwave.core.crash.CrashReporter
import com.aetherwave.core.designsystem.theme.AccentPalette
import com.aetherwave.core.designsystem.theme.AetherWaveTheme
import com.aetherwave.core.island.DynamicIslandOverlay
import com.aetherwave.core.island.IslandPrefs
import com.aetherwave.core.locale.AppLocale
import com.aetherwave.core.theme.ArtworkThemeStore
import com.aetherwave.playback.PlaybackController
import com.aetherwave.ui.navigation.AetherWaveApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playbackController: PlaybackController

    /** True while the overlay permission screen is open, so repeated taps do not open it again. */
    private var overlayPermissionOpen = false

    /** True when the island was turned on but the permission was still missing. */
    private var pendingIslandPermission = false

    /** The island switch. It is state, so the switch updates the moment the setting changes. */
    private val dynamicIslandState = mutableStateOf(false)

    private val preferences by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        DynamicIslandOverlay.hide()
    }

    override fun onStop() {
        super.onStop()
        val enabled = preferences.getBoolean(KEY_DYNAMIC_ISLAND, false)
        if (enabled && Settings.canDrawOverlays(this)) {
            DynamicIslandOverlay.show(this, playbackController)
        }
    }

    override fun onResume() {
        super.onResume()
        overlayPermissionOpen = false
        // The user came back from the permission screen. If they granted it, turn the island on now.
        if (pendingIslandPermission && Settings.canDrawOverlays(this)) {
            pendingIslandPermission = false
            preferences.edit().putBoolean(KEY_DYNAMIC_ISLAND, true).apply()
            dynamicIslandState.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dynamicIslandState.value = preferences.getBoolean(KEY_DYNAMIC_ISLAND, false)
        val pendingCrash = CrashReporter.read(this)
        setContent {
            val systemDark = isSystemInDarkTheme()
            val artworkSeed = ArtworkThemeStore.seed.collectAsState().value

            // Settings are kept as state, so changing them does not recreate the activity.
            var crashReport by remember { mutableStateOf(pendingCrash) }
            var themeMode by remember { mutableStateOf(preferences.getString("theme_mode", "system") ?: "system") }
            var liquidGlassEnabled by remember { mutableStateOf(preferences.getBoolean(KEY_LIQUID_GLASS, false)) }
            var artworkTheme by remember { mutableStateOf(preferences.getBoolean(KEY_ARTWORK_THEME, true)) }
            var pureBlack by remember { mutableStateOf(preferences.getBoolean(KEY_PURE_BLACK, false)) }
            val dynamicIsland = dynamicIslandState.value
            var accent by remember { mutableStateOf(preferences.getString("accent", "default") ?: "default") }
            var language by remember { mutableStateOf(preferences.getString(AppLocale.KEY, AppLocale.SYSTEM) ?: AppLocale.SYSTEM) }
            var islandPosition by remember { mutableStateOf(IslandPrefs.read(this)) }
            var islandLook by remember { mutableStateOf(IslandPrefs.readLook(this)) }

            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            AetherWaveTheme(
                darkTheme = darkTheme,
                accent = AccentPalette.fromKey(accent),
                materialYou = true,
                artworkSeed = artworkSeed,
                useArtwork = artworkTheme,
                pureBlack = pureBlack,
            ) {
                AetherWaveApp(
                    darkTheme = darkTheme,
                    language = language,
                    materialYou = true,
                    onMaterialYouChanged = { _ -> },
                    artworkTheme = artworkTheme,
                    onArtworkThemeChanged = { on ->
                        artworkTheme = on
                        preferences.edit().putBoolean(KEY_ARTWORK_THEME, on).apply()
                    },
                    pureBlack = pureBlack,
                    onPureBlackChanged = { on ->
                        pureBlack = on
                        preferences.edit().putBoolean(KEY_PURE_BLACK, on).apply()
                    },
                    dynamicIsland = dynamicIsland,
                    onDynamicIslandChanged = { enabled -> onDynamicIslandToggled(enabled) },
                    islandX = islandPosition.first,
                    islandY = islandPosition.second,
                    onIslandPositionChanged = { x, y ->
                        IslandPrefs.write(this@MainActivity, x, y)
                        islandPosition = x to y
                    },
                    islandStyle = islandLook.style,
                    islandCorner = islandLook.corner,
                    onIslandLookChanged = { style, corner ->
                        IslandPrefs.writeLook(this@MainActivity, style, corner)
                        islandLook = IslandPrefs.Look(style, corner)
                    },
                    accent = accent,
                    onAccentChanged = { key ->
                        accent = key
                        preferences.edit().putString("accent", key).apply()
                    },
                    onLanguageChanged = { tag ->
                        // Only a real change restarts the activity, so the new language applies.
                        if (tag != language) {
                            preferences.edit().putString(AppLocale.KEY, tag).apply()
                            language = tag
                            recreate()
                        }
                    },
                    themeMode = themeMode,
                    onThemeModeChanged = { mode ->
                        themeMode = mode
                        preferences.edit().putString("theme_mode", mode).apply()
                    },
                    liquidGlassEnabled = liquidGlassEnabled,
                    onLiquidGlassChanged = { enabled ->
                        liquidGlassEnabled = enabled
                        preferences.edit().putBoolean(KEY_LIQUID_GLASS, enabled).apply()
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

    /**
     * Turns the island on or off. Turning it on needs the overlay permission, so the permission
     * screen opens once. Further taps do nothing until the user returns from it.
     */
    private fun onDynamicIslandToggled(enabled: Boolean) {
        if (!enabled) {
            pendingIslandPermission = false
            preferences.edit().putBoolean(KEY_DYNAMIC_ISLAND, false).apply()
            DynamicIslandOverlay.hide()
            dynamicIslandState.value = false
            return
        }
        if (Settings.canDrawOverlays(this)) {
            preferences.edit().putBoolean(KEY_DYNAMIC_ISLAND, true).apply()
            dynamicIslandState.value = true
        } else {
            // Permission is missing: open the screen once, and turn the island on when the user returns.
            pendingIslandPermission = true
            dynamicIslandState.value = false
            if (!overlayPermissionOpen) {
                overlayPermissionOpen = true
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
        }
    }

    private companion object {
        const val PREFS = "aetherwave_preferences"
        const val KEY_DYNAMIC_ISLAND = "dynamic_island"
        const val KEY_LIQUID_GLASS = "liquid_glass_enabled"
        const val KEY_ARTWORK_THEME = "artwork_theme"
        const val KEY_PURE_BLACK = "pure_black"
    }
}
