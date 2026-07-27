package com.locusmimic.app.manager.localization

import android.content.Context
import android.content.res.Configuration
import com.locusmimic.app.data.DEFAULT_LANGUAGE_TAG
import com.locusmimic.app.data.KEY_LANGUAGE_TAG
import com.locusmimic.app.data.SHARED_PREFS_FILE
import java.util.Locale

object LocaleController {
    fun attachBaseContext(context: Context): Context {
        return localizedContext(context, readLanguageTag(context))
    }

    fun persistLanguageTag(context: Context, languageTag: String) {
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
    }

    fun readLanguageTag(context: Context): String {
        return context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG)
            ?: DEFAULT_LANGUAGE_TAG
    }

    private fun localizedContext(context: Context, languageTag: String): Context {
        if (languageTag.isBlank()) return context

        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
