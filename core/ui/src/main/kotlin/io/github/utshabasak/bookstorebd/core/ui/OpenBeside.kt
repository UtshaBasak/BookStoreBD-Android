package io.github.utshabasak.bookstorebd.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey

/**
 * The screen open beside a list on a wide window, such as a conversation beside
 * Messages or an order beside My orders, so the list can show which of its
 * items that is. Null on a phone, and while nothing is open beside the list.
 */
val LocalOpenBeside = compositionLocalOf<NavKey?> { null }
