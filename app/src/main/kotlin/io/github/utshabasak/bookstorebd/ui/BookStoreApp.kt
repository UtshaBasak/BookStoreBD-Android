package io.github.utshabasak.bookstorebd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.ui.OfflineBanner
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.feature.auth.authEntries
import io.github.utshabasak.bookstorebd.feature.book.bookEntry
import io.github.utshabasak.bookstorebd.feature.cart.cartEntry
import io.github.utshabasak.bookstorebd.feature.catalogue.catalogueEntry
import io.github.utshabasak.bookstorebd.feature.checkout.checkoutEntries
import io.github.utshabasak.bookstorebd.feature.home.homeEntry
import io.github.utshabasak.bookstorebd.feature.settings.UpdateOfferDialog
import io.github.utshabasak.bookstorebd.feature.settings.UpdateRequiredScreen
import io.github.utshabasak.bookstorebd.feature.settings.UpdateStatus
import io.github.utshabasak.bookstorebd.feature.settings.UpdateViewModel
import io.github.utshabasak.bookstorebd.feature.settings.accountEntry
import io.github.utshabasak.bookstorebd.feature.settings.openPage
import io.github.utshabasak.bookstorebd.feature.shop.shopEntry
import io.github.utshabasak.bookstorebd.feature.wishlist.wishlistEntry

/**
 * The app's root: the five tabs, the strip shown while offline, and the
 * update prompts. Each screen draws its own top bar.
 */
@Composable
fun BookStoreApp(
    appViewModel: AppViewModel,
    modifier: Modifier = Modifier,
    updates: UpdateViewModel = hiltViewModel(),
) {
    val update by updates.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(updates) { updates.checkIfDue() }

    if (update.required) {
        Surface(modifier.fillMaxSize()) {
            UpdateRequiredScreen(
                release = update.newer,
                status = update.status,
                canInstall = update.canInstall,
                onInstall = updates::download,
                onOpenReleases = { openPage(context, update.releasesUrl) },
                onOpenWebsite = { openPage(context, BuildConfig.SITE_URL) },
                modifier = Modifier.safeDrawingPadding(),
            )
        }
        return
    }

    update.offered?.let { release ->
        UpdateOfferDialog(
            release = release,
            status = update.status,
            canInstall = update.canInstall,
            onInstall = { if (update.status is UpdateStatus.Ready) updates.install() else updates.download(release) },
            onOpenPage = {
                openPage(context, release.pageUrl.ifBlank { update.releasesUrl })
                updates.dismiss(release)
            },
            onLater = { updates.dismiss(release) },
        )
    }

    val offline by appViewModel.isOffline.collectAsStateWithLifecycle()
    val cartCount by appViewModel.cartCount.collectAsStateWithLifecycle()
    val navigation = rememberAppNavigationState()

    Scaffold(
        modifier = modifier,
        // The screens inside handle the status bar themselves; this only makes room for the tabs.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                OfflineBanner(offline)
                if (navigation.showTabs) {
                    TabBar(navigation, cartCount)
                } else if (offline) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.inverseSurface)
                            .windowInsetsBottomHeight(WindowInsets.navigationBars),
                    )
                }
            }
        },
    ) { padding ->
        AppNavDisplay(
            navigation = navigation,
            modifier =
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
        )
    }
}

/**
 * Every tab's screens, each tab keeping its own saved state and view models.
 * Home's stack sits under the current tab's, so back from another tab's
 * first screen returns to Home, and back on Home leaves the app.
 */
@Composable
private fun AppNavDisplay(
    navigation: AppNavigationState,
    modifier: Modifier = Modifier,
) {
    val provider =
        remember(navigation) {
            entryProvider<NavKey> {
                homeEntry(navigation)
                catalogueEntry(navigation)
                bookEntry(navigation)
                shopEntry(navigation)
                wishlistEntry(navigation)
                cartEntry(navigation)
                checkoutEntries(navigation)
                authEntries(navigation)
                accountEntry(navigation)
            }
        }
    val entries: Map<TopLevel, List<NavEntry<NavKey>>> =
        TopLevel.entries.associateWith { tab ->
            rememberDecoratedNavEntries(
                backStack = navigation.stacks.getValue(tab),
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = provider,
            )
        }
    val shown =
        if (navigation.currentTab == TopLevel.Home) {
            entries.getValue(TopLevel.Home)
        } else {
            entries.getValue(TopLevel.Home) + entries.getValue(navigation.currentTab)
        }
    NavDisplay(
        entries = shown,
        modifier = modifier,
        onBack = navigation::back,
    )
}

@Composable
private fun TabBar(
    navigation: AppNavigationState,
    cartCount: Int,
) {
    NavigationBar {
        TopLevel.entries.forEach { tab ->
            val selected = navigation.currentTab == tab
            val label = stringResource(tab.label)
            NavigationBarItem(
                selected = selected,
                onClick = { navigation.selectTab(tab) },
                label = { Text(label, maxLines = 1) },
                icon = {
                    val icon = if (selected) tab.selectedIcon else tab.icon
                    if (tab == TopLevel.Cart && cartCount > 0) {
                        val description = pluralStringResource(R.plurals.cart_badge_description, cartCount, cartCount)
                        BadgedBox(
                            badge = { Badge { Text(formatCount(cartCount, currentLocale())) } },
                            modifier = Modifier.semantics { contentDescription = description },
                        ) {
                            Icon(icon, contentDescription = null)
                        }
                    } else {
                        Icon(icon, contentDescription = null)
                    }
                },
            )
        }
    }
}
