package com.aetherwave.core.island

import android.content.Context

/**
 * Where and how the Dynamic Island looks. x is the offset from the centre, from -1 (left edge) to 1
 * (right edge). y is the distance from the top, as a fraction of the screen height. Corner is 0 for
 * tight corners and 1 for the roundest.
 */
object IslandPrefs {
    private const val NAME = "aetherwave_island"
    const val STYLE_CLASSIC = "classic"
    const val STYLE_GLASS = "glass"

    data class Look(val style: String, val corner: Float)

    fun read(context: Context): Pair<Float, Float> {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return prefs.getFloat("x", 0f) to prefs.getFloat("y", 0.02f)
    }

    fun write(context: Context, x: Float, y: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("x", x.coerceIn(-1f, 1f))
            .putFloat("y", y.coerceIn(0f, 0.4f))
            .apply()
    }

    fun readLook(context: Context): Look {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return Look(
            style = prefs.getString("style", STYLE_CLASSIC) ?: STYLE_CLASSIC,
            corner = prefs.getFloat("corner", 0.5f),
        )
    }

    fun writeLook(context: Context, style: String, corner: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("style", style)
            .putFloat("corner", corner.coerceIn(0f, 1f))
            .apply()
    }

    /** Corner radius in dp: 12 for tight corners up to 36 for the roundest. */
    fun cornerDp(corner: Float): Float = 12f + 24f * corner.coerceIn(0f, 1f)
}
