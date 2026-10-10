package io.github.utshabasak.bookstorebd.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

internal val LightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Light.Brand,
        onPrimary = Color.White,
        primaryContainer = Light.BrandTint,
        onPrimaryContainer = Light.BrandDark,
        inversePrimary = Dark.Brand,
        secondary = Light.Accent,
        onSecondary = Light.BrandDeep,
        secondaryContainer = Light.AccentTint,
        onSecondaryContainer = Light.AccentInk,
        tertiary = Light.Sun,
        onTertiary = Light.BrandDeep,
        tertiaryContainer = Light.SunTint,
        onTertiaryContainer = Light.SunInk,
        background = Light.Page,
        onBackground = Light.Ink,
        surface = Light.Surface,
        onSurface = Light.Ink,
        surfaceVariant = Light.SurfaceMuted,
        onSurfaceVariant = Light.InkSoft,
        surfaceTint = Light.Brand,
        inverseSurface = Dark.Surface,
        inverseOnSurface = Dark.Ink,
        error = Light.Danger,
        onError = Color.White,
        errorContainer = Light.DangerTint,
        onErrorContainer = Light.DangerInk,
        outline = Light.Outline,
        outlineVariant = Light.Line,
        scrim = Color.Black,
        surfaceBright = Light.Surface,
        surfaceDim = Light.SurfaceMuted,
        surfaceContainerLowest = Light.Surface,
        surfaceContainerLow = Light.Surface,
        surfaceContainer = Light.SurfaceAlt,
        surfaceContainerHigh = Light.BrandTint,
        surfaceContainerHighest = Light.Line,
    )

internal val DarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Dark.Brand,
        onPrimary = Dark.BrandDeep,
        primaryContainer = Dark.BrandTint,
        onPrimaryContainer = Dark.BrandLight,
        inversePrimary = Light.Brand,
        secondary = Dark.Accent,
        onSecondary = Dark.BrandDeep,
        secondaryContainer = Dark.AccentTint,
        onSecondaryContainer = Dark.AccentLight,
        tertiary = Dark.Sun,
        onTertiary = Dark.BrandDeep,
        tertiaryContainer = Dark.SunTint,
        onTertiaryContainer = Dark.SunInk,
        background = Dark.Page,
        onBackground = Dark.Ink,
        surface = Dark.Surface,
        onSurface = Dark.Ink,
        surfaceVariant = Dark.SurfaceMuted,
        onSurfaceVariant = Dark.InkSoft,
        surfaceTint = Dark.Brand,
        inverseSurface = Light.Surface,
        inverseOnSurface = Light.Ink,
        error = Dark.Danger,
        onError = Dark.BrandDeep,
        errorContainer = Dark.DangerTint,
        onErrorContainer = Dark.DangerInk,
        outline = Dark.Outline,
        outlineVariant = Dark.Line,
        scrim = Color.Black,
        surfaceBright = Dark.SurfaceMuted,
        surfaceDim = Dark.Page,
        surfaceContainerLowest = Dark.Page,
        surfaceContainerLow = Dark.Surface,
        surfaceContainer = Dark.SurfaceAlt,
        surfaceContainerHigh = Dark.SurfaceMuted,
        surfaceContainerHighest = Dark.Line,
    )

/** Colours the shop needs that Material's scheme has no role for. */
@Immutable
data class BookStoreColors(
    /** What a copy costs: the sale price on every card and page. */
    val price: Color,
    /** The struck-through list price beside a sale price. */
    val listPrice: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    /** The yellow of highlights and star ratings. */
    val star: Color,
    /** Secondary text, such as an author's name under a title. */
    val textMuted: Color,
    /** The violet fill under white text, for the brand's own badges. */
    val brandFill: Color,
)

internal val LightBookStoreColors =
    BookStoreColors(
        price = Light.Price,
        listPrice = Light.InkMuted,
        success = Light.Success,
        successContainer = Light.SuccessTint,
        warning = Light.WarnInk,
        warningContainer = Light.WarnTint,
        star = Light.SunInk,
        textMuted = Light.InkMuted,
        brandFill = Light.Brand,
    )

internal val DarkBookStoreColors =
    BookStoreColors(
        price = Dark.AccentLight,
        listPrice = Dark.InkMuted,
        success = Dark.Success,
        successContainer = Dark.SuccessTint,
        warning = Dark.WarnInk,
        warningContainer = Dark.WarnTint,
        star = Dark.Sun,
        textMuted = Dark.InkMuted,
        brandFill = Dark.BrandFill,
    )
