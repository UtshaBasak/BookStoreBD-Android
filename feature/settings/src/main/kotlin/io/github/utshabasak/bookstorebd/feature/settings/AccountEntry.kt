package io.github.utshabasak.bookstorebd.feature.settings

import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.AccountRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.EditProfileRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.InviteRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.ListBookRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MessagesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.MyBooksRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.NotificationsRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.OrdersRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.PrivacyRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SalesRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignUpRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WantedRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.WishlistRoute

/** Registers the Account tab with the app's navigation. */
fun EntryProviderScope<NavKey>.accountEntry(navigator: Navigator) {
    entry<AccountRoute> {
        val account: AccountViewModel = hiltViewModel()
        val updates: UpdateViewModel = hiltViewModel()
        val state by account.uiState.collectAsStateWithLifecycle()
        val update by updates.uiState.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        var failure by remember { mutableStateOf<AppError?>(null) }
        var signedOutOthers by remember { mutableStateOf<Int?>(null) }
        val endedText =
            when (val ended = signedOutOthers) {
                null -> null
                0 -> stringResource(R.string.feature_settings_no_other_sessions)
                else -> pluralStringResource(R.plurals.feature_settings_others_ended, ended, ended)
            }
        LaunchedEffect(Unit) { account.refreshProfile() }
        LaunchedEffect(account) {
            account.eventFlow.collect { event ->
                when (event) {
                    is AccountEvent.SignedOutOthers -> signedOutOthers = event.ended
                    is AccountEvent.Failed -> failure = event.error
                    is AccountEvent.TwoStepChanged -> snackbar.showSnackbar(event.message)
                }
            }
        }
        LaunchedEffect(endedText) {
            if (endedText != null) {
                snackbar.showSnackbar(endedText)
                signedOutOthers = null
            }
        }
        ErrorSnackbarEffect(failure, snackbar) { failure = null }
        val inboxCounts: InboxCountsViewModel = hiltViewModel()
        val inbox by inboxCounts.counts.collectAsStateWithLifecycle()
        AccountScreen(
            state = state,
            update = update,
            snackbar = snackbar,
            language = currentAppLanguage(),
            actions =
                accountActions(navigator, account, updates) { openPage(context, it) }
                    .copy(onEmail = { address -> writeTo(context, address) }),
            inbox = inbox,
        )
    }
}

private fun accountActions(
    navigator: Navigator,
    account: AccountViewModel,
    updates: UpdateViewModel,
    open: (String) -> Unit,
) = AccountActions(
    onSignIn = { navigator.navigate(SignInRoute) },
    onCreateAccount = { navigator.navigate(SignUpRoute) },
    onSignOut = account::signOut,
    onSignOutOthers = account::signOutOthers,
    onTheme = account::setTheme,
    onMode = account::setMode,
    onLanguage = ::setAppLanguage,
    onCheckUpdates = updates::checkNow,
    onDownload = updates::download,
    onOpen = open,
    onEditProfile = { focus -> navigator.navigate(EditProfileRoute(focus?.name)) },
    onSetupStep = { step ->
        if (step == SetupItem.TwoStep) account.setTwoStep(true) else navigator.navigate(EditProfileRoute(step.name))
    },
    onOrders = { navigator.navigate(OrdersRoute()) },
    onWishlist = { navigator.navigate(WishlistRoute) },
    onWanted = { navigator.navigate(WantedRoute()) },
    onInvite = { navigator.navigate(InviteRoute) },
    onMessages = { navigator.navigate(MessagesRoute) },
    onListBook = { navigator.navigate(ListBookRoute()) },
    onMyBooks = { navigator.navigate(MyBooksRoute) },
    onSales = { navigator.navigate(SalesRoute) },
    onAddBkash = { navigator.navigate(EditProfileRoute(SetupItem.Bkash.name)) },
    onNotifications = { navigator.navigate(NotificationsRoute) },
    onPrivacy = { navigator.navigate(PrivacyRoute) },
    onTwoStep = account::setTwoStep,
    onSendTwoStepCode = account::sendTwoStepCode,
    onDismissTwoStepProblem = account::clearTwoStepProblem,
)

/** Opens the phone's e-mail app with a message to [address]. */
private fun writeTo(
    context: Context,
    address: String,
) {
    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri())) }
}
