package com.aetherwave.playback

/**
 * Sound presets. Speed and pitch are applied through ExoPlayer's PlaybackParameters, so the values
 * are approximate equivalents of the usual sped-up and slowed looks. They do not add reverb.
 */
enum class SoundStyle(val label: String, val speed: Float, val pitch: Float) {
    NORMAL("Normal", 1f, 1f),
    SPED_UP("Sped up", 1.2f, 1.1f),
    NIGHTCORE("Nightcore", 1.4f, 1.25f),
    SLOWED("Slowed", 0.85f, 0.9f),
}

/**
 * Target loudness presets. Each sets how much of the full scale a normalized track may use.
 * Lower values leave more headroom. Volume never rises above 1, so Loud and Aggressive only
 * differ from Balanced when a track is quieter than the reference.
 */
enum class LoudnessLevel(val label: String, val headroom: Float) {
    QUIET("Quiet", 0.6f),
    BALANCED("Balanced", 0.75f),
    LOUD("Loud", 0.9f),
    AGGRESSIVE("Aggressive", 1f),
}
