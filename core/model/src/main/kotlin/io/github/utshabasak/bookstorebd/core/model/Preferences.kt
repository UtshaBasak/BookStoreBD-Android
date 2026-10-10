package io.github.utshabasak.bookstorebd.core.model

/** Light, dark, or whatever the phone is set to. */
enum class ThemePreference { System, Light, Dark }

/** The app's language, by BCP 47 tag; [System] follows the phone. */
enum class AppLanguage(
    val tag: String?,
) {
    System(null),
    English("en"),
    Bangla("bn"),
}
