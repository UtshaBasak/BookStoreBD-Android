package io.github.utshabasak.bookstorebd.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush

/**
 * The website's brand gradient, violet into sunset orange at 135 degrees, for
 * the profile header and banners that have no picture yet.
 */
@Composable
@ReadOnlyComposable
fun brandGradient(): Brush =
    Brush.linearGradient(
        colors = listOf(BookStoreTheme.colors.brandFill, MaterialTheme.colorScheme.secondary),
    )
