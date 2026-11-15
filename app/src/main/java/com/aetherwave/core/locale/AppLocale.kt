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
        val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM
        if (tag == SYSTEM) return context
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }
}
