package io.github.utshabasak.bookstorebd.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.ErrorSnackbarEffect
import io.github.utshabasak.bookstorebd.core.ui.navigation.AccountRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignUpRoute

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
        LaunchedEffect(account) {
            account.eventFlow.collect { event ->
                when (event) {
                    is AccountEvent.SignedOutOthers -> signedOutOthers = event.ended
                    is AccountEvent.Failed -> failure = event.error
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
        AccountScreen(
            state = state,
            update = update,
            snackbar = snackbar,
            language = currentAppLanguage(),
            actions =
                AccountActions(
                    onSignIn = { navigator.navigate(SignInRoute) },
                    onCreateAccount = { navigator.navigate(SignUpRoute) },
                    onSignOut = account::signOut,
                    onSignOutOthers = account::signOutOthers,
                    onTheme = account::setTheme,
                    onLanguage = ::setAppLanguage,
                    onCheckUpdates = updates::checkNow,
                    onDownload = updates::download,
                    onOpen = { openPage(context, it) },
                ),
        )
    }
}
