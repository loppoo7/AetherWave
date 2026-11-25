package com.aetherwave.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the app language chosen in Settings. "system" follows the phone's language, and the
 * app falls back to English when that language has no translation.
 */
object AppLocale {
    const val PREFS = "aetherwave_preferences"
    const val KEY = "app_language"
    const val SYSTEM = "system"

    fun wrap(context: Context): Context {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
        // Android uses "id" for Indonesian. Older builds stored "in", so map it.
        val tag = if (stored == "in") "id" else stored
        if (tag == SYSTEM) return context
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        Locale.setDefault(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
