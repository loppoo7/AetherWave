package com.aetherwave.playback

import android.content.Context

/**
 * Saves the playback options so they survive an app restart. Sleep timer and queue are session-only.
 */
object PlaybackPrefs {
    private const val NAME = "aetherwave_playback"
    private const val CUSTOM = "CUSTOM"

    fun load(context: Context): PlaybackState {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val styleName = prefs.getString("sound_style", SoundStyle.NORMAL.name) ?: SoundStyle.NORMAL.name
        return PlaybackState(
            normalizeVolume = prefs.getBoolean("normalize", true),
            loudnessLevel = LoudnessLevel.entries.firstOrNull { it.name == prefs.getString("loudness", null) }
                ?: LoudnessLevel.BALANCED,
            pureMode = prefs.getBoolean("pure", false),
            skipSilence = prefs.getBoolean("skip_silence", false),
            playbackSpeed = prefs.getFloat("speed", 1f),
            pitch = prefs.getFloat("pitch", 1f),
            soundStyle = if (styleName == CUSTOM) null else SoundStyle.entries.firstOrNull { it.name == styleName } ?: SoundStyle.NORMAL,
        )
    }

    fun save(context: Context, state: PlaybackState) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("normalize", state.normalizeVolume)
            .putString("loudness", state.loudnessLevel.name)
            .putBoolean("pure", state.pureMode)
            .putBoolean("skip_silence", state.skipSilence)
            .putFloat("speed", state.playbackSpeed)
            .putFloat("pitch", state.pitch)
            .putString("sound_style", state.soundStyle?.name ?: CUSTOM)
            .apply()
    }
}
