package io.github.utshabasak.bookstorebd.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.utshabasak.bookstorebd.core.designsystem.R

/*
 * Plus Jakarta Sans, the website's typeface, as one variable font file with a
 * weight axis. It has no Bengali letters or Taka sign; Android draws those from
 * its own Noto Sans Bengali, which every device has.
 */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) =
    Font(
        resId = R.font.core_designsystem_plus_jakarta_sans,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val PlusJakartaSans: FontFamily =
    FontFamily(
        jakarta(FontWeight.Normal),
        jakarta(FontWeight.Medium),
        jakarta(FontWeight.SemiBold),
        jakarta(FontWeight.Bold),
        jakarta(FontWeight.ExtraBold),
    )

private val Default = Typography()

private fun TextStyle.jakarta(weight: FontWeight? = null) = copy(fontFamily = PlusJakartaSans, fontWeight = weight ?: fontWeight)

internal val BookStoreTypography =
    Typography(
        displayLarge = Default.displayLarge.jakarta(FontWeight.ExtraBold),
        displayMedium = Default.displayMedium.jakarta(FontWeight.ExtraBold),
        displaySmall = Default.displaySmall.jakarta(FontWeight.Bold),
        headlineLarge = Default.headlineLarge.jakarta(FontWeight.Bold),
        headlineMedium = Default.headlineMedium.jakarta(FontWeight.Bold),
        headlineSmall = Default.headlineSmall.jakarta(FontWeight.Bold),
        titleLarge = Default.titleLarge.jakarta(FontWeight.Bold),
        titleMedium = Default.titleMedium.jakarta(FontWeight.SemiBold),
        titleSmall = Default.titleSmall.jakarta(FontWeight.SemiBold),
        bodyLarge = Default.bodyLarge.jakarta(),
        bodyMedium = Default.bodyMedium.jakarta(),
        bodySmall = Default.bodySmall.jakarta(),
        labelLarge = Default.labelLarge.jakarta(FontWeight.SemiBold),
        labelMedium = Default.labelMedium.jakarta(FontWeight.SemiBold),
        labelSmall = Default.labelSmall.jakarta(FontWeight.Medium),
    )
