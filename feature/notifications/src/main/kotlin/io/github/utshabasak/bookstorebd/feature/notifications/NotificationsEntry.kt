package io.github.utshabasak.bookstorebd.feature.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.NotificationSettingsRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.NotificationsRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.routeForSiteLink
import io.github.utshabasak.bookstorebd.core.ui.openInBrowser

/** Registers the notification list and the choice of what to hear about. */
fun EntryProviderScope<NavKey>.notificationsEntries(navigator: Navigator) {
    entry<NotificationsRoute> { NotificationsDestination(navigator) }
    entry<NotificationSettingsRoute> { SettingsDestination(navigator) }
}

@Composable
private fun NotificationsDestination(navigator: Navigator) {
    val viewModel: NotificationsViewModel = hiltViewModel()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val marks by viewModel.readMarks.collectAsStateWithLifecycle()
    val siteUrl by viewModel.siteUrl.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.failureFlow.collect { failure = it } }
    LaunchedEffect(viewModel) { viewModel.arrivals.collect { items.refresh() } }
    // A fresh page from the server carries its own read marks.
    LaunchedEffect(items.loadState.refresh) { if (items.loadState.refresh is LoadState.NotLoading) viewModel.reloaded() }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    NotificationsScreen(
        signedIn = signedIn,
        unread = unread,
        items = items,
        marks = marks,
        snackbar = snackbar,
        actions =
            NotificationsActions(
                onBack = navigator::back,
                onOpen = { notification ->
                    viewModel.opened(notification)
                    val link = notification.link
                    if (link.isNotBlank()) {
                        routeForSiteLink(link)?.let(navigator::navigate) ?: openOnWebsite(context, siteUrl, link)
                    }
                },
                onMarkAllRead = viewModel::markAllRead,
                onSettings = { navigator.navigate(NotificationSettingsRoute) },
                onSignIn = { navigator.navigate(SignInRoute) },
            ),
    )
}

@Composable
private fun SettingsDestination(navigator: Navigator) {
    val viewModel: NotificationSettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var failure by remember { mutableStateOf<AppError?>(null) }
    LaunchedEffect(viewModel) { viewModel.failureFlow.collect { failure = it } }
    ErrorSnackbarEffect(failure, snackbar) { failure = null }
    NotificationSettingsScreen(
        state = state,
        snackbar = snackbar,
        actions =
            NotificationSettingsActions(
                onBack = navigator::back,
                onChange = viewModel::change,
                onRetry = viewModel::load,
                onPhoneSettings = { openPhoneSettings(context) },
            ),
    )
}

/** A page the app does not have yet, such as a seller's tools, on the website. */
private fun openOnWebsite(
    context: Context,
    siteUrl: String,
    link: String,
) {
    if (siteUrl.isBlank()) return
    openInBrowser(context, siteUrl.trimEnd('/') + link)
}

/** Android's own settings for this app's notifications: sounds, and each kind on or off. */
private fun openPhoneSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
