package io.github.utshabasak.bookstorebd.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * WCAG 2.1 AA: 4.5:1 for text, 3:1 for the outlines of controls. Every
 * foreground the app draws on a background is checked here, in both themes,
 * so a change to a colour cannot quietly make text hard to read.
 */
class ColorContrastTest {
    @Test
    fun `text meets 4_5 to 1 on its background in the light theme`() {
        textPairs(LightColorScheme, LightBookStoreColors).forEach { (name, pair) -> assertContrast(name, pair, TEXT) }
    }

    @Test
    fun `text meets 4_5 to 1 on its background in the dark theme`() {
        textPairs(DarkColorScheme, DarkBookStoreColors).forEach { (name, pair) -> assertContrast(name, pair, TEXT) }
    }

    @Test
    fun `control outlines meet 3 to 1 in both themes`() {
        assertContrast("light outline", LightColorScheme.outline to LightColorScheme.surface, NON_TEXT)
        assertContrast("dark outline", DarkColorScheme.outline to DarkColorScheme.surface, NON_TEXT)
    }

    private fun textPairs(
        scheme: ColorScheme,
        shop: BookStoreColors,
    ): Map<String, Pair<Color, Color>> =
        with(scheme) {
            mapOf(
                "onPrimary on primary" to (onPrimary to primary),
                "onPrimaryContainer on primaryContainer" to (onPrimaryContainer to primaryContainer),
                "onSecondary on secondary" to (onSecondary to secondary),
                "onSecondaryContainer on secondaryContainer" to (onSecondaryContainer to secondaryContainer),
                "onTertiary on tertiary" to (onTertiary to tertiary),
                "onTertiaryContainer on tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
                "onBackground on background" to (onBackground to background),
                "onSurface on surface" to (onSurface to surface),
                "onSurfaceVariant on surface" to (onSurfaceVariant to surface),
                "onSurfaceVariant on surfaceVariant" to (onSurfaceVariant to surfaceVariant),
                "primary on surface" to (primary to surface),
                "primary on background" to (primary to background),
                "onError on error" to (onError to error),
                "onErrorContainer on errorContainer" to (onErrorContainer to errorContainer),
                "error on surface" to (error to surface),
                "price on surface" to (shop.price to surface),
                "price on background" to (shop.price to background),
                "list price on surface" to (shop.listPrice to surface),
                "muted text on surface" to (shop.textMuted to surface),
                "muted text on background" to (shop.textMuted to background),
                "success on successContainer" to (shop.success to shop.successContainer),
                "success on surface" to (shop.success to surface),
                "warning on warningContainer" to (shop.warning to shop.warningContainer),
                "white on brand fill" to (Color.White to shop.brandFill),
            )
        }

    private fun assertContrast(
        name: String,
        pair: Pair<Color, Color>,
        minimum: Double,
    ) {
        val ratio = contrast(pair.first, pair.second)
        assertWithMessage("$name has a contrast of ${"%.2f".format(ratio)}:1").that(ratio).isAtLeast(minimum)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
    }
}
