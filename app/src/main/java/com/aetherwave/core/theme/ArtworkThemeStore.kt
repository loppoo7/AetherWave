package com.aetherwave.core.theme

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.aetherwave.core.designsystem.components.ArtworkPalette
import com.aetherwave.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The colours of the song that is playing, taken from its cover. The theme reads them, so the app
 * recolours as the song changes. Works on every Android version, unlike wallpaper colours.
 */
object ArtworkThemeStore {
    private val mutableSeed = MutableStateFlow<ArtworkPalette?>(null)
    val seed: StateFlow<ArtworkPalette?> = mutableSeed.asStateFlow()

    private val http by lazy {
        OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    }

    /** Follows the playback state and updates [seed] whenever the cover changes. */
    fun follow(scope: CoroutineScope, playback: StateFlow<PlaybackState>) {
        scope.launch {
            playback.map { it.currentMusicTrack?.artworkUri }
                .distinctUntilChanged()
                .collect { url ->
                    mutableSeed.value = if (url.isNullOrBlank()) null else extract(url)
                }
        }
    }

    private suspend fun extract(url: String): ArtworkPalette? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    response.body?.byteStream()?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                }
            } ?: return@runCatching null
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val dominant = palette.getDominantColor(0xFF4F7FD6.toInt())
            val primary = palette.getVibrantColor(dominant)
            val secondary = palette.getMutedColor(palette.getDarkVibrantColor(primary))
            val highlight = palette.getLightVibrantColor(primary)
            ArtworkPalette(
                primary = Color(primary),
                secondary = Color(secondary),
                highlight = Color(highlight),
            )
        }.getOrNull()
    }
}
