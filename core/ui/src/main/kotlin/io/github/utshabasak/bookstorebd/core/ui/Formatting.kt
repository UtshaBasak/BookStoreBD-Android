package io.github.utshabasak.bookstorebd.core.ui

import android.icu.text.NumberFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import kotlin.math.abs

/**
 * Taka as the shop writes it: "৳1,200" in English, "৳১,২০০" in Bangla, with the
 * subcontinent's grouping (1,00,000) either way, and paisa only when there are any.
 * ICU's formatter, which knows the grouping; the JDK's does not.
 */
fun formatTaka(
    amount: Double,
    locale: Locale,
): String {
    val numbers = NumberFormat.getNumberInstance(numberLocale(locale))
    val whole = abs(amount - Math.round(amount)) < PAISA
    numbers.minimumFractionDigits = if (whole) 0 else 2
    numbers.maximumFractionDigits = if (whole) 0 else 2
    return "৳" + numbers.format(amount)
}

/** A count, in the locale's digits: "১২" in Bangla. */
fun formatCount(
    count: Int,
    locale: Locale,
): String = NumberFormat.getIntegerInstance(numberLocale(locale)).format(count)

/** Bangla keeps its own digits; English uses India's grouping, as the website does (en-IN). */
private fun numberLocale(locale: Locale): Locale =
    when (locale.language) {
        "bn" -> Locale.forLanguageTag("bn-BD")
        else -> Locale.forLanguageTag("en-IN")
    }

private const val PAISA = 0.005

/** The app's current locale, from the configuration, so a language change redraws prices too. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
