package com.aetherwave.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.aetherwave.core.designsystem.components.AwGlyph
import com.aetherwave.ui.components.InstrumentalBreak
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.aetherwave.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import android.content.Intent
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.aetherwave.core.designsystem.components.ArtworkPalette
import com.aetherwave.core.designsystem.components.DynamicMeshBackground
import com.aetherwave.core.designsystem.components.LiquidGlass
import com.aetherwave.core.designsystem.components.liquidGlass
import com.aetherwave.core.designsystem.components.liquidGlassSource
import com.aetherwave.core.designsystem.components.rememberArtworkPalette
import com.aetherwave.domain.model.LyricLine
import com.aetherwave.domain.model.MusicTrack
import com.aetherwave.playback.LoudnessLevel
import com.aetherwave.playback.PlaybackState
import com.aetherwave.playback.SoundStyle
import com.aetherwave.ui.components.formatTrackDuration
import com.aetherwave.ui.viewmodel.MainPlayerUiState
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build
import android.content.Context

@Composable
fun NowPlayingScreen(
    state: MainPlayerUiState,
    hazeState: HazeState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onLike: () -> Unit,
    onDownload: () -> Unit,
    onToggleLyrics: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepEndOfTrack: () -> Unit,
    onCancelSleep: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onJumpQueueItem: (Int) -> Unit,
    onSetNormalize: (Boolean) -> Unit,
    onSetSoundStyle: (SoundStyle) -> Unit,
    onSetLoudness: (LoudnessLevel) -> Unit,
    onSetPure: (Boolean) -> Unit,
) {
    val track = state.currentTrack
    var showOptions by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val tap: () -> Unit = {
        if (state.playback.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val activeLyric = state.lyrics?.syncedLines
        ?.lastOrNull { it.timestampMs <= state.playback.position }?.text
    val context = LocalContext.current
    val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
    val artworkBitmap by produceState<Bitmap?>(initialValue = null, key1 = track?.artworkUri) {
        value = null
        val artwork = track?.artworkUri ?: return@produceState
        val result = runCatching {
            withContext(Dispatchers.IO) {
                imageLoader.execute(
                    ImageRequest.Builder(context).data(artwork).allowHardware(false).build(),
                )
            }
        }.getOrNull()
        value = (result as? SuccessResult)?.image?.let { runCatching { it.toBitmap() }.getOrNull() }
    }
    val palette = rememberArtworkPalette(artworkBitmap)
    val formatBadge = remember(track?.id, track?.localFilePath, track?.mediaUri, context) {
        track?.let { current ->
            val mimeType = current.mediaUri?.let { rawUri ->
                runCatching { context.contentResolver.getType(Uri.parse(rawUri)) }.getOrNull()
            }
            audioFormatBadge(current, mimeType)
        }
    }

    Box(Modifier.fillMaxSize()) {
        DynamicMeshBackground(palette, Modifier.fillMaxSize().liquidGlassSource(hazeState))
        // The cover behind the controls. Blur needs Android 12 or newer; older versions show it softened.
        artworkBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.4f }
                    .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(48.dp) else Modifier),
            )
        }
        Box(Modifier.fillMaxSize().background(Color(0x99080A14)))

        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = { if (totalDrag > 140f) onBack() },
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassRoundButton("⌄", hazeState, onBack)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.np_eyebrow), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f), fontSize = 9.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("AETHERWAVE", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 11.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.weight(1f))
                GlassRoundButton("⋯", hazeState) { showOptions = true }
            }

            AnimatedContent(
                targetState = state.showLyrics,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "lyrics-transition",
            ) { showLyrics ->
                if (showLyrics) {
                    LyricsPanel(
                        lines = state.lyrics?.syncedLines.orEmpty(),
                        position = state.playback.position,
                        isLoading = state.isLyricsLoading,
                        onSeek = onSeek,
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (track != null) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = "Album artwork for ${track.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth(0.86f).widthIn(max = 380.dp).aspectRatio(1f)
                                    .shadow(28.dp, RoundedCornerShape(30.dp), ambientColor = palette.primary, spotColor = palette.primary)
                                    .clip(RoundedCornerShape(30.dp))
                                    .pointerInput(track.id) {
                                        var totalDrag = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { totalDrag = 0f },
                                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                            onDragEnd = {
                                                if (totalDrag < -120f) onNext() else if (totalDrag > 120f) onPrevious()
                                            },
                                        )
                                    },
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(formatBadge.orEmpty(), modifier = Modifier
                                .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)).padding(horizontal = 13.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f), fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        track.artist,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (state.lyrics != null) {
                                    GlassRoundButton("⇪", hazeState) {
                                        shareLyricLine(context, activeLyric, track.title, track.artist)
                                    }
                                }
                                GlassRoundButton(if (track.isLiked) "♥" else "♡", hazeState, onLike)
                            }
                        } else {
                            Text(stringResource(R.string.np_choose_track), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.76f), fontSize = 16.sp)
                        }
                    }
                }
            }

            val playback = state.playback
            var dragging by remember { mutableStateOf(false) }
            var sliderPosition by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(playback.position, dragging) {
                if (!dragging) sliderPosition = playback.position.toFloat().coerceIn(0f, playback.duration.coerceAtLeast(1L).toFloat())
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { dragging = true; sliderPosition = it },
                    onValueChangeFinished = { onSeek(sliderPosition.toLong()); dragging = false },
                    valueRange = 0f..playback.duration.coerceAtLeast(1L).toFloat(),
                    enabled = playback.duration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.onBackground,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTrackDuration(playback.position), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 10.sp)
                    Text(formatTrackDuration(playback.duration), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 10.sp)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ControlGlyph("⤨", selected = playback.shuffleMode, onClick = onShuffle)
                ControlGlyph("|◀", onClick = { tap(); onPrevious() }, large = true)
                LiquidGlass(
                    modifier = Modifier.size(78.dp).clickable(onClick = { tap(); onPlayPause() }),
                    state = hazeState,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                    content = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (playback.isPlaying) "Ⅱ" else "▶", color = MaterialTheme.colorScheme.onBackground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                )
                ControlGlyph("▶|", onClick = { tap(); onNext() }, large = true)
                ControlGlyph(if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) "↻1" else "↻", selected = playback.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF, onClick = onRepeat)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ActionGlyph(if (state.isDownloading) "…" else "↓", when { track?.isDownloaded == true -> "SAVED"; track?.source?.name == "LOCAL" -> "ON DEVICE"; else -> "DOWNLOAD" }, track?.isDownloaded == true, onDownload)
                ActionGlyph("≋", if (state.showLyrics) stringResource(R.string.np_artwork) else stringResource(R.string.np_lyrics), state.showLyrics, onToggleLyrics)
                ActionGlyph("☰", stringResource(R.string.np_up_next), showQueue) { showQueue = true }
            }
            state.playback.errorMessage?.let {
                Text(it, color = Color(0xFFFFB7B7), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.playback.queueIndex,
            onDismiss = { showQueue = false },
            onMove = onMoveQueueItem,
            onRemove = onRemoveQueueItem,
            onJump = onJumpQueueItem,
        )
    }

    if (showOptions) {
        PlaybackOptionsSheet(
            playback = state.playback,
            onDismiss = { showOptions = false },
            onSetSpeed = onSetSpeed,
            onSetPitch = onSetPitch,
            onSetSkipSilence = onSetSkipSilence,
            normalize = state.playback.normalizeVolume,
            onSetNormalize = onSetNormalize,
            onSetSoundStyle = onSetSoundStyle,
            onSetLoudness = onSetLoudness,
            pure = state.playback.pureMode,
            onSetPure = onSetPure,
            onSetSleepTimer = onSetSleepTimer,
            onSetSleepEndOfTrack = onSetSleepEndOfTrack,
            onCancelSleep = onCancelSleep,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<MusicTrack>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onJump: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Up Next · ${queue.size} tracks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (queue.isEmpty()) {
                Text("Nothing queued. Play a song to start radio.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    itemsIndexed(queue, key = { index, track -> "$index-${track.id}" }) { index, track ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { onJump(index) }) {
                                Text(
                                    track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) { Text("↑") }
                            TextButton(onClick = { onMove(index, index + 1) }, enabled = index < queue.lastIndex) { Text("↓") }
                            TextButton(onClick = { onRemove(index) }) { Text("✕") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackOptionsSheet(
    playback: PlaybackState,
    onDismiss: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetPitch: (Float) -> Unit,
    onSetSkipSilence: (Boolean) -> Unit,
    normalize: Boolean,
    onSetNormalize: (Boolean) -> Unit,
    onSetSoundStyle: (SoundStyle) -> Unit,
    onSetLoudness: (LoudnessLevel) -> Unit,
    pure: Boolean,
    onSetPure: (Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepEndOfTrack: () -> Unit,
    onCancelSleep: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sleepChoice by remember { mutableStateOf("off") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.np_options_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            ChoiceChips(
                label = stringResource(R.string.np_sleep_timer),
                options = listOf("Off" to "off", "15 min" to "15", "30 min" to "30", "45 min" to "45", "1 hour" to "60", "End of track" to "eot"),
                selected = sleepChoice,
                onSelect = { choice ->
                    sleepChoice = choice
                    when (choice) {
                        "off" -> onCancelSleep()
                        "eot" -> onSetSleepEndOfTrack()
                        else -> onSetSleepTimer(choice.toInt())
                    }
                },
            )
            playback.sleepTimerEndsAt?.let { endsAt ->
                val remainingMinutes = ((endsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1
                Text("Stops in about $remainingMinutes min", style = MaterialTheme.typography.bodySmall)
            }

            ChoiceChips(
                label = stringResource(R.string.np_speed),
                options = listOf("0.75×" to 0.75f, "1×" to 1f, "1.25×" to 1.25f, "1.5×" to 1.5f, "2×" to 2f),
                selected = playback.playbackSpeed,
                onSelect = onSetSpeed,
            )

            ChoiceChips(
                label = stringResource(R.string.np_pitch),
                options = listOf("Lower" to 0.9f, "Original" to 1f, "Higher" to 1.1f),
                selected = playback.pitch,
                onSelect = onSetPitch,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.np_skip_silence), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.np_skip_silence_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = playback.skipSilence, onCheckedChange = onSetSkipSilence)
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.np_normalize), style = MaterialTheme.typography.labelLarge)
                    Text("Evens out loudness between tracks. Local files are never changed.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = normalize, onCheckedChange = onSetNormalize)
            }

            ChoiceChips(
                label = stringResource(R.string.np_sound_style),
                options = SoundStyle.entries.map { it.label to it },
                selected = playback.soundStyle,
                onSelect = { style -> if (style != null) onSetSoundStyle(style) },
            )

            ChoiceChips(
                label = stringResource(R.string.np_loudness),
                options = LoudnessLevel.entries.map { it.label to it },
                selected = playback.loudnessLevel,
                onSelect = onSetLoudness,
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pure / Lossless", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (playback.currentMusicTrack?.source == com.aetherwave.domain.model.TrackSource.YOUTUBE) {
                            "Applies to local files. YouTube streams are lossy, so this does not change them."
                        } else {
                            "Local files play with no speed, pitch, silence skipping, or normalization."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = pure, onCheckedChange = onSetPure)
            }
        }
    }
}

@Composable
private fun <T> ChoiceChips(
    label: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (text, value) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}

private sealed interface LyricRow {
    val startMs: Long
}

private data class LyricLineRow(val index: Int, val line: LyricLine) : LyricRow {
    override val startMs: Long get() = line.timestampMs
}

private data class InstrumentalRow(override val startMs: Long, val endMs: Long) : LyricRow

/** A gap of this length or more between lines is treated as an instrumental break. */
private const val INSTRUMENTAL_GAP_MS = 6_000L
/** Time a sung line is assumed to take before the instrumental break starts. */
private const val INSTRUMENTAL_LEAD_MS = 4_000L

private fun buildLyricRows(lines: List<LyricLine>): List<LyricRow> {
    val rows = mutableListOf<LyricRow>()
    lines.forEachIndexed { index, line ->
        if (index == 0 && line.timestampMs >= INSTRUMENTAL_GAP_MS) {
            rows += InstrumentalRow(startMs = 0L, endMs = line.timestampMs)
        }
        rows += LyricLineRow(index, line)
        val next = lines.getOrNull(index + 1)
        if (next != null && next.timestampMs - line.timestampMs >= INSTRUMENTAL_GAP_MS) {
            rows += InstrumentalRow(startMs = line.timestampMs + INSTRUMENTAL_LEAD_MS, endMs = next.timestampMs)
        }
    }
    return rows
}

@Composable
private fun LyricsPanel(lines: List<LyricLine>, position: Long, isLoading: Boolean, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val rows = remember(lines) { buildLyricRows(lines) }
    val activeLine = lines.indexOfLast { it.timestampMs <= position }.coerceAtLeast(0)
    val activeRow = rows.indexOfLast { it.startMs <= position }.coerceAtLeast(0)
    LaunchedEffect(activeRow, rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem((activeRow - 2).coerceAtLeast(0))
    }
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Finding synchronized lyrics…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 13.sp) }
    } else if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No synchronized lyrics found for this track.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f), fontSize = 13.sp, textAlign = TextAlign.Center) }
    } else {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 32.dp)) {
            itemsIndexed(rows) { _, row ->
                when (row) {
                    is LyricLineRow -> {
                        val isActive = row.index == activeLine
                        Text(
                            row.line.text,
                            modifier = Modifier.fillMaxWidth().clickable { onSeek(row.line.timestampMs) }.padding(horizontal = 12.dp),
                            color = if (isActive) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.36f),
                            fontSize = if (isActive) 24.sp else 18.sp,
                            lineHeight = if (isActive) 32.sp else 25.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    is InstrumentalRow -> InstrumentalBreak(
                        startMs = row.startMs,
                        endMs = row.endMs,
                        position = position,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassRoundButton(text: String, hazeState: HazeState, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).liquidGlass(hazeState, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AwGlyph(text, MaterialTheme.colorScheme.onBackground, 22.dp, 20.sp)
    }
}

@Composable
private fun ControlGlyph(text: String, selected: Boolean = false, large: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(if (large) 48.dp else 40.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f) else Color.Transparent).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AwGlyph(text, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f), if (large) 28.dp else 22.dp, if (large) 20.sp else 17.sp)
    }
}

@Composable
private fun ActionGlyph(text: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 9.dp, vertical = 5.dp)) {
        AwGlyph(text, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f), 22.dp, 20.sp)
        Text(label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f), fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
    }
}

private fun audioFormatBadge(track: MusicTrack, mimeType: String?): String {
    if (track.source.name == "YOUTUBE") return "OPUS STREAM"
    val sourcePath = track.localFilePath ?: track.mediaUri.orEmpty()
    val pathExtension = sourcePath.substringBefore('?').substringAfterLast('.', "").uppercase()
    val mimeExtension = when (mimeType?.lowercase()) {
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "WAV"
        "audio/mpeg" -> "MP3"
        "audio/mp4", "audio/aac", "audio/x-m4a" -> "M4A"
        else -> ""
    }
    val extension = pathExtension.takeIf { it in setOf("FLAC", "WAV", "MP3", "M4A", "AAC") }
        ?: mimeExtension.ifBlank { "AUDIO" }
    return when (extension) {
        "FLAC" -> "FLAC  •  HI-RES"
        "WAV" -> "WAV  •  LOSSLESS"
        "MP3" -> "MP3  •  LOCAL"
        "M4A", "AAC" -> "$extension  •  LOCAL"
        else -> "LOCAL AUDIO"
    }
}


/** Shares the lyric line that is playing, with the song title and artist. */
private fun shareLyricLine(context: Context, line: String?, title: String, artist: String) {
    line?.takeIf(String::isNotBlank)?.let { text ->
        val body = "\"$text\"\n$title — $artist"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(send, "Share lyric").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
