package com.aetherwave.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherwave.BuildConfig
import com.aetherwave.R
import com.aetherwave.playback.LoudnessLevel
import com.aetherwave.playback.PlaybackState
import com.aetherwave.playback.SoundStyle
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TextButton
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.height

private enum class SettingsPage { HUB, APPEARANCE, SOUND, INTEGRATIONS, LANGUAGE, ABOUT, ISLAND }

@Composable
fun SettingsScreen(
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    liquidGlassEnabled: Boolean,
    onLiquidGlassChanged: (Boolean) -> Unit,
    language: String,
    onLanguageChanged: (String) -> Unit,
    playback: PlaybackState,
    onSetNormalize: (Boolean) -> Unit,
    onSetLoudness: (LoudnessLevel) -> Unit,
    onSetPure: (Boolean) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    onSetSoundStyle: (SoundStyle) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetAutoSkip: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    accent: String,
    onAccentChanged: (String) -> Unit,
    onSetSponsorBlock: (Boolean) -> Unit,
    onSetScrobble: (Boolean) -> Unit,
    onSetListenBrainzToken: (String) -> Unit,
    dynamicIsland: Boolean,
    onDynamicIslandChanged: (Boolean) -> Unit,
    islandX: Float,
    islandY: Float,
    onIslandPositionChanged: (Float, Float) -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.HUB) }
    BackHandler(enabled = page != SettingsPage.HUB) { page = SettingsPage.HUB }

    val title = when (page) {
        SettingsPage.HUB -> stringResource(R.string.settings_title)
        SettingsPage.APPEARANCE -> stringResource(R.string.settings_category_appearance)
        SettingsPage.SOUND -> stringResource(R.string.settings_category_sound)
        SettingsPage.LANGUAGE -> stringResource(R.string.settings_category_language)
        SettingsPage.ABOUT -> stringResource(R.string.settings_category_about)
        SettingsPage.INTEGRATIONS -> stringResource(R.string.settings_category_integrations)
        SettingsPage.ISLAND -> stringResource(R.string.settings_island_title)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page == SettingsPage.HUB) onBack() else page = SettingsPage.HUB }) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }

        when (page) {
            SettingsPage.HUB -> HubPage(onOpen = { page = it })
            SettingsPage.APPEARANCE -> AppearancePage(
                themeMode = themeMode,
                onThemeModeChanged = onThemeModeChanged,
                accent = accent,
                onAccentChanged = onAccentChanged,
                dynamicIsland = dynamicIsland,
                onDynamicIslandChanged = onDynamicIslandChanged,
                onOpenIsland = { page = SettingsPage.ISLAND },
                liquidGlassEnabled = liquidGlassEnabled,
                onLiquidGlassChanged = onLiquidGlassChanged,
            )
            SettingsPage.SOUND -> SoundPage(
                playback = playback,
                onSetNormalize = onSetNormalize,
                onSetLoudness = onSetLoudness,
                onSetPure = onSetPure,
                onSetSkipSilence = onSetSkipSilence,
                onSetSoundStyle = onSetSoundStyle,
                onSetSpeed = onSetSpeed,
                onSetPitch = onSetPitch,
                onSetAutoSkip = onSetAutoSkip,
                onSetHaptics = onSetHaptics,
            )
            SettingsPage.LANGUAGE -> LanguagePage(language = language, onLanguageChanged = onLanguageChanged)
            SettingsPage.INTEGRATIONS -> IntegrationsPage(
                playback = playback,
                onSetSponsorBlock = onSetSponsorBlock,
                onSetScrobble = onSetScrobble,
                onSetListenBrainzToken = onSetListenBrainzToken,
            )
            SettingsPage.ISLAND -> IslandPage(
                islandX = islandX,
                islandY = islandY,
                onIslandPositionChanged = onIslandPositionChanged,
            )
            SettingsPage.ABOUT -> AboutPage()
        }
    }
}

@Composable
private fun HubPage(onOpen: (SettingsPage) -> Unit) {
    SettingsCard {
        HubRow(Icons.Rounded.Palette, stringResource(R.string.settings_category_appearance), stringResource(R.string.settings_category_appearance_desc)) {
            onOpen(SettingsPage.APPEARANCE)
        }
        HubRow(Icons.Rounded.GraphicEq, stringResource(R.string.settings_category_sound), stringResource(R.string.settings_category_sound_desc)) {
            onOpen(SettingsPage.SOUND)
        }
        HubRow(Icons.Rounded.Language, stringResource(R.string.settings_category_language), stringResource(R.string.settings_language_desc)) {
            onOpen(SettingsPage.LANGUAGE)
        }
        HubRow(Icons.Rounded.Link, stringResource(R.string.settings_category_integrations), stringResource(R.string.settings_category_integrations_desc)) {
            onOpen(SettingsPage.INTEGRATIONS)
        }
        HubRow(Icons.Rounded.Info, stringResource(R.string.settings_category_about), stringResource(R.string.settings_category_about_desc)) {
            onOpen(SettingsPage.ABOUT)
        }
    }
}

@Composable
private fun AppearancePage(
    themeMode: String,
    onThemeModeChanged: (String) -> Unit,
    accent: String,
    onAccentChanged: (String) -> Unit,
    dynamicIsland: Boolean,
    onDynamicIslandChanged: (Boolean) -> Unit,
    onOpenIsland: () -> Unit,
    liquidGlassEnabled: Boolean,
    onLiquidGlassChanged: (Boolean) -> Unit,
) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_accent), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_accent_desc), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp)
            OptionChips(
                options = listOf(
                    "default" to stringResource(R.string.accent_default),
                    "ocean" to stringResource(R.string.accent_ocean),
                    "mint" to stringResource(R.string.accent_mint),
                    "amber" to stringResource(R.string.accent_amber),
                    "rose" to stringResource(R.string.accent_rose),
                ),
                selected = accent,
                onSelect = onAccentChanged,
            )
        }
    }
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_theme), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_theme_desc), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp)
            OptionChips(
                options = listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                ),
                selected = themeMode,
                onSelect = onThemeModeChanged,
            )
        }
    }
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_liquid_glass),
            subtitle = stringResource(R.string.settings_liquid_glass_desc),
            trailing = { Switch(checked = liquidGlassEnabled, onCheckedChange = onLiquidGlassChanged) },
        )
    }
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_dynamic_island),
            subtitle = stringResource(R.string.settings_dynamic_island_desc),
            trailing = { Switch(checked = dynamicIsland, onCheckedChange = onDynamicIslandChanged) },
        )
        SettingRow(
            title = stringResource(R.string.settings_island_customize),
            subtitle = stringResource(R.string.settings_island_preview_desc),
            trailing = { TextButton(onClick = onOpenIsland) { Text(stringResource(R.string.settings_island_customize)) } },
        )
    }
}

@Composable
private fun SoundPage(
    playback: PlaybackState,
    onSetNormalize: (Boolean) -> Unit,
    onSetLoudness: (LoudnessLevel) -> Unit,
    onSetPure: (Boolean) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    onSetSoundStyle: (SoundStyle) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetAutoSkip: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
) {
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_auto_skip),
            subtitle = stringResource(R.string.settings_auto_skip_desc),
            trailing = { Switch(checked = playback.autoSkipOnError, onCheckedChange = onSetAutoSkip) },
        )
        SettingRow(
            title = stringResource(R.string.settings_haptics),
            subtitle = stringResource(R.string.settings_haptics_desc),
            trailing = { Switch(checked = playback.hapticsEnabled, onCheckedChange = onSetHaptics) },
        )
    }
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_normalize),
            subtitle = stringResource(R.string.settings_normalize_desc),
            trailing = { Switch(checked = playback.normalizeVolume, onCheckedChange = onSetNormalize) },
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_loudness), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OptionChips(
                options = LoudnessLevel.entries.map { it.label to it },
                selected = playback.loudnessLevel,
                onSelect = onSetLoudness,
            )
        }
    }
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_pure),
            subtitle = stringResource(R.string.settings_pure_desc),
            trailing = { Switch(checked = playback.pureMode, onCheckedChange = onSetPure) },
        )
        SettingRow(
            title = stringResource(R.string.settings_skip_silence),
            subtitle = stringResource(R.string.settings_skip_silence_desc),
            trailing = { Switch(checked = playback.skipSilence, onCheckedChange = onSetSkipSilence) },
        )
    }
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_sound_style), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OptionChips(
                options = SoundStyle.entries.map { it.label to it },
                selected = playback.soundStyle,
                onSelect = { style -> style?.let(onSetSoundStyle) },
            )
            Text(stringResource(R.string.settings_speed), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OptionChips(
                options = listOf("0.75×" to 0.75f, "1×" to 1f, "1.25×" to 1.25f, "1.5×" to 1.5f, "2×" to 2f),
                selected = playback.playbackSpeed,
                onSelect = onSetSpeed,
            )
            Text(stringResource(R.string.settings_pitch), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OptionChips(
                options = listOf("0.9×" to 0.9f, "1×" to 1f, "1.1×" to 1.1f),
                selected = playback.pitch,
                onSelect = onSetPitch,
            )
        }
    }
}

@Composable
private fun LanguagePage(language: String, onLanguageChanged: (String) -> Unit) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.settings_language_desc), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontSize = 12.sp)
            OptionChips(
                options = listOf(
                    "system" to stringResource(R.string.lang_system),
                    "en" to stringResource(R.string.lang_english),
                    "id" to stringResource(R.string.lang_indonesian),
                ),
                selected = language,
                onSelect = onLanguageChanged,
            )
        }
    }
}

@Composable
private fun IntegrationsPage(
    playback: PlaybackState,
    onSetSponsorBlock: (Boolean) -> Unit,
    onSetScrobble: (Boolean) -> Unit,
    onSetListenBrainzToken: (String) -> Unit,
) {
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_sponsorblock),
            subtitle = stringResource(R.string.settings_sponsorblock_desc),
            trailing = { Switch(checked = playback.sponsorBlockEnabled, onCheckedChange = onSetSponsorBlock) },
        )
    }
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_scrobble),
            subtitle = stringResource(R.string.settings_scrobble_desc),
            trailing = { Switch(checked = playback.scrobbleEnabled, onCheckedChange = onSetScrobble) },
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_listenbrainz_token), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = playback.listenBrainzToken,
                onValueChange = onSetListenBrainzToken,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text(stringResource(R.string.settings_listenbrainz_token_desc)) },
            )
        }
    }
}

@Composable
private fun IslandPage(
    islandX: Float,
    islandY: Float,
    onIslandPositionChanged: (Float, Float) -> Unit,
) {
    var fx by remember(islandX) { mutableFloatStateOf(islandX) }
    var fy by remember(islandY) { mutableFloatStateOf(islandY) }
    Text(
        stringResource(R.string.settings_island_hint),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        fontSize = 12.sp,
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(520.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset((fx * widthPx / 2f).roundToInt(), (fy * heightPx).roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            fx = (fx + drag.x / (widthPx / 2f)).coerceIn(-1f, 1f)
                            fy = (fy + drag.y / heightPx).coerceIn(0f, 0.4f)
                        },
                        onDragEnd = { onIslandPositionChanged(fx, fy) },
                        onDragCancel = { onIslandPositionChanged(fx, fy) },
                    )
                }
                .clip(RoundedCornerShape(50))
                .background(Color(0xE6101216))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(width = 6.dp, height = 18.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
                Column {
                    Text("Song title", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Artist", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AboutPage() {
    SettingsCard {
        SettingRow(
            title = stringResource(R.string.settings_version),
            subtitle = BuildConfig.VERSION_NAME,
        )
    }
    SettingsCard {
        Text(
            stringResource(R.string.settings_crash_note),
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(content = content)
    }
}

@Composable
private fun HubRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(38.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}

@Composable
private fun <T> OptionChips(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
