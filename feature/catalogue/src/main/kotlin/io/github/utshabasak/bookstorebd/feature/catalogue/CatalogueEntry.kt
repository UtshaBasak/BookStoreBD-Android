package io.github.utshabasak.bookstorebd.feature.catalogue

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.ui.navigation.CatalogueRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator

/** Registers the catalogue with the app's navigation. */
fun EntryProviderScope<NavKey>.catalogueEntry(navigator: Navigator) {
    entry<CatalogueRoute> { route -> CatalogueRouteScreen(route, navigator) }
}
