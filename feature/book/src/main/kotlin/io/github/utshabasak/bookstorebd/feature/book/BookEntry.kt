package io.github.utshabasak.bookstorebd.feature.book

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.ui.navigation.BookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator

/** Registers the book page with the app's navigation. */
fun EntryProviderScope<NavKey>.bookEntry(navigator: Navigator) {
    entry<BookRoute> { route -> BookRouteScreen(route, navigator) }
}
