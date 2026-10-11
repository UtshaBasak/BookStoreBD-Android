package io.github.utshabasak.bookstorebd.feature.home

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.ui.navigation.HomeRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator

/** Registers the home screen with the app's navigation. */
fun EntryProviderScope<NavKey>.homeEntry(navigator: Navigator) {
    entry<HomeRoute> { HomeRouteScreen(navigator) }
}
