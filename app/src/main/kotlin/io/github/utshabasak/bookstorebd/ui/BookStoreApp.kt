package io.github.utshabasak.bookstorebd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.window.core.layout.WindowSizeClass
import io.github.utshabasak.bookstorebd.BuildConfig
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.ui.OfflineBanner
import io.github.utshabasak.bookstorebd.core.ui.currentLocale
import io.github.utshabasak.bookstorebd.core.ui.formatCount
import io.github.utshabasak.bookstorebd.feature.auth.authEntries
import io.github.utshabasak.bookstorebd.feature.book.bookEntry
import io.github.utshabasak.bookstorebd.feature.cart.cartEntry
import io.github.utshabasak.bookstorebd.feature.catalogue.catalogueEntry
import io.github.utshabasak.bookstorebd.feature.chat.chatEntries
import io.github.utshabasak.bookstorebd.feature.checkout.checkoutEntries
import io.github.utshabasak.bookstorebd.feature.home.homeEntry
import io.github.utshabasak.bookstorebd.feature.notifications.notificationsEntries
import io.github.utshabasak.bookstorebd.feature.orders.ordersEntries
import io.github.utshabasak.bookstorebd.feature.profile.profileEntries
import io.github.utshabasak.bookstorebd.feature.reviews.reviewsEntries
import io.github.utshabasak.bookstorebd.feature.selling.sellingEntries
import io.github.utshabasak.bookstorebd.feature.settings.UpdateOfferDialog
import io.github.utshabasak.bookstorebd.feature.settings.UpdateRequiredScreen
import io.github.utshabasak.bookstorebd.feature.settings.UpdateStatus
import io.github.utshabasak.bookstorebd.feature.settings.UpdateViewModel
import io.github.utshabasak.bookstorebd.feature.settings.accountEntry
import io.github.utshabasak.bookstorebd.feature.settings.openPage
import io.github.utshabasak.bookstorebd.feature.shop.shopEntry
import io.github.utshabasak.bookstorebd.feature.wanted.wantedEntry
import io.github.utshabasak.bookstorebd.feature.wishlist.wishlistEntry

/**
 * The app's root: the five tabs (along the bottom, or down the side of a wide
 * window), the strip shown while offline, and the update prompts. Each screen
 * draws its own top bar.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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
    val pendingScreen by appViewModel.pendingScreen.collectAsStateWithLifecycle()
    LaunchedEffect(pendingScreen) {
        pendingScreen?.let {
            navigation.navigate(it)
            appViewModel.screenShown()
        }
    }

    // Phones held upright have the tabs along the bottom; wider windows have them down the side.
    val adaptive = currentWindowAdaptiveInfoV2()
    val wide = adaptive.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val twoPane = calculatePaneScaffoldDirective(adaptive).maxHorizontalPartitions > 1
    SideEffect { navigation.twoPane = twoPane }
    val showTabs = navigation.showTabs(wide)
    val rail = wide && showTabs

    Row(modifier.fillMaxSize()) {
        if (rail) TabRail(navigation, cartCount)
        Scaffold(
            modifier =
                Modifier
                    .weight(1f)
                    .then(if (rail) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier),
            // The screens inside handle the status bar themselves; this only makes room for the tabs.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Column {
                    OfflineBanner(offline)
                    if (showTabs && !wide) {
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
}

/**
 * Every tab's screens, each tab keeping its own saved state and view models.
 * Home's stack sits under the current tab's, so back from another tab's
 * first screen returns to Home, and back on Home leaves the app.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppNavDisplay(
    navigation: AppNavigationState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val provider: (NavKey) -> NavEntry<NavKey> =
        remember(navigation) {
            val screens =
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
                    profileEntries(navigation, openPage = { url -> openPage(context, url) })
                    ordersEntries(navigation)
                    reviewsEntries(navigation)
                    wantedEntry(navigation)
                    notificationsEntries(navigation)
                    chatEntries(navigation)
                    sellingEntries(navigation)
                }
            val withPanes: (NavKey) -> NavEntry<NavKey> = { route -> withPane(screens(route), route, navigation::openBeside) }
            withPanes
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
    val tabRoots = entries.values.mapNotNull { stack -> stack.firstOrNull()?.contentKey }.toSet()
    val motion = remember(tabRoots) { AppMotion(tabRoots) }
    // Wide windows show a list and the item chosen from it side by side (AppPanes); back closes the item first.
    val panes =
        rememberListDetailSceneStrategy<NavKey>(
            backNavigationBehavior = BackNavigationBehavior.PopLatest,
            directive = paneDirective(),
        )
    NavDisplay(
        entries = shown,
        modifier = modifier,
        sceneStrategies = listOf(panes),
        transitionSpec = motion.forward,
        popTransitionSpec = motion.back,
        predictivePopTransitionSpec = motion.predictiveBack,
        onBack = navigation::back,
    )
}

/**
 * How panes share a wide window, as Material works it out, except that a pane
 * opening does not take the focus: that would put the keyboard up over the
 * search box at the top of a list.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun paneDirective(): PaneScaffoldDirective {
    val material = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    return remember(material) {
        PaneScaffoldDirective(
            maxHorizontalPartitions = material.maxHorizontalPartitions,
            horizontalPartitionSpacerSize = material.horizontalPartitionSpacerSize,
            maxVerticalPartitions = material.maxVerticalPartitions,
            verticalPartitionSpacerSize = material.verticalPartitionSpacerSize,
            defaultPanePreferredWidth = material.defaultPanePreferredWidth,
            defaultPanePreferredHeight = material.defaultPanePreferredHeight,
            excludedBounds = material.excludedBounds,
            shouldAutoFocusCurrentDestination = false,
        )
    }
}

@Composable
private fun TabRail(
    navigation: AppNavigationState,
    cartCount: Int,
) {
    NavigationRail {
        Spacer(Modifier.weight(1f))
        TopLevel.entries.forEach { tab ->
            val selected = navigation.currentTab == tab
            NavigationRailItem(
                selected = selected,
                onClick = { navigation.selectTab(tab) },
                label = { Text(stringResource(tab.label), maxLines = 1) },
                icon = { TabIcon(tab, selected, cartCount) },
            )
        }
        Spacer(Modifier.weight(1f))
    }
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
                icon = { TabIcon(tab, selected, cartCount) },
            )
        }
    }
}

/** A tab's icon, with the number of books in the cart on the cart's. */
@Composable
private fun TabIcon(
    tab: TopLevel,
    selected: Boolean,
    cartCount: Int,
) {
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
}
