package io.github.utshabasak.bookstorebd.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Rounded cards (18dp, as on the website), softer fields and pill-shaped buttons. */
internal val BookStoreShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

private val LocalBookStoreColors = staticCompositionLocalOf { LightBookStoreColors }

/**
 * The shop's look: the website's violet, sunset orange and yellow, in light or
 * dark. The brand colours are used rather than the wallpaper's, so the shop
 * looks like itself on every phone.
 */
@Composable
fun BookStoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBookStoreColors provides if (darkTheme) DarkBookStoreColors else LightBookStoreColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = BookStoreTypography,
            shapes = BookStoreShapes,
            content = content,
        )
    }
}

/** The shop's own colours, beside `MaterialTheme.colorScheme`. */
object BookStoreTheme {
    val colors: BookStoreColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBookStoreColors.current
}
