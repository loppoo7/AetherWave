package com.aetherwave.core.island

import android.content.Context

/**
 * Where the Dynamic Island sits. x is the offset from the centre, from -1 (left edge) to 1 (right
 * edge). y is the distance from the top, as a fraction of the screen height.
 */
object IslandPrefs {
    private const val NAME = "aetherwave_island"
    private const val DEFAULT_X = 0f
    private const val DEFAULT_Y = 0.02f

    fun read(context: Context): Pair<Float, Float> {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return prefs.getFloat("x", DEFAULT_X) to prefs.getFloat("y", DEFAULT_Y)
    }

    fun write(context: Context, x: Float, y: Float) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putFloat("x", x.coerceIn(-1f, 1f))
            .putFloat("y", y.coerceIn(0f, 0.4f))
            .apply()
    }
}
