package io.github.utshabasak.bookstorebd.core.data.util

import android.content.Context
import androidx.core.app.LocaleManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** The language the app is showing, as the server names it: `bn` or `en`. */
fun interface RequestLanguage {
    fun tag(): String
}

/**
 * The language chosen in the app, or the phone's when the app follows it.
 * Read from the app's stored choice rather than from a screen, so it is right
 * outside a screen too, on every Android version.
 */
@Singleton
class AppRequestLanguage
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RequestLanguage {
        override fun tag(): String {
            val chosen = LocaleManagerCompat.getApplicationLocales(context)[0]
            return languageTag(chosen ?: Locale.getDefault())
        }
    }

/** Bangla for a Bangla locale; English for every other, as the server does. */
fun languageTag(locale: Locale): String = if (locale.language == "bn") "bn" else "en"
