package io.github.utshabasak.bookstorebd.core.ui

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** The shop works in Dhaka time, so dates read the same as on the website. */
private const val SHOP_ZONE = "Asia/Dhaka"

/**
 * A date from the API (an ISO timestamp or a plain date) as people read it:
 * "11 Oct 2026" in English, "১১ অক্টো, ২০২৬" in Bangla. Blank when there is no
 * date or it cannot be read.
 */
fun formatDate(
    iso: String?,
    locale: Locale,
    withTime: Boolean = false,
): String {
    val instant = parseInstant(iso) ?: return ""
    val uLocale = ULocale.forLocale(if (locale.language == "bn") Locale.forLanguageTag("bn-BD") else Locale.forLanguageTag("en-GB"))
    val format =
        if (withTime) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, uLocale)
        } else {
            DateFormat.getDateInstance(DateFormat.MEDIUM, uLocale)
        }
    format.timeZone = TimeZone.getTimeZone(SHOP_ZONE)
    return format.format(Date.from(instant))
}

/** [formatDate] in the app's current language. */
@Composable
@ReadOnlyComposable
fun formatDate(
    iso: String?,
    withTime: Boolean = false,
): String = formatDate(iso, currentLocale(), withTime)

internal fun parseInstant(iso: String?): Instant? {
    val value = iso?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(value.take(DATE_LENGTH)).atStartOfDay(ZoneId.of(SHOP_ZONE)).toInstant() }.getOrNull()
}

private const val DATE_LENGTH = 10
