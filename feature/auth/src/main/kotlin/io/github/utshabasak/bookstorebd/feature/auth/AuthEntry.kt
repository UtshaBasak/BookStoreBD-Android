package io.github.utshabasak.bookstorebd.feature.auth

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.navigation.Navigator
import io.github.utshabasak.bookstorebd.core.ui.navigation.ResetPasswordRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignInRoute
import io.github.utshabasak.bookstorebd.core.ui.navigation.SignUpRoute

/** Registers sign-in, sign-up and password reset with the app's navigation. */
fun EntryProviderScope<NavKey>.authEntries(navigator: Navigator) {
    entry<SignInRoute> { SignInRouteScreen(navigator) }
    entry<SignUpRoute> { SignUpRouteScreen(navigator) }
    entry<ResetPasswordRoute> { ResetRouteScreen(navigator) }
}

@Composable
private fun SignInRouteScreen(
    navigator: Navigator,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                // Back to wherever sign-in was asked for: the cart, a book, the account tab.
                SignInEvent.SignedIn -> {
                    navigator.back()
                }

                is SignInEvent.LaunchGoogle -> {
                    val host = activity ?: return@collect viewModel.onGoogleCancelled()
                    when (val result = requestGoogleIdToken(host, event.clientId, event.nonce)) {
                        is GoogleResult.Token -> viewModel.onGoogleToken(result.idToken, event.nonce)
                        GoogleResult.Cancelled -> viewModel.onGoogleCancelled()
                        is GoogleResult.Failed -> viewModel.onGoogleFailed(AppError.Unexpected(result.cause))
                    }
                }
            }
        }
    }
    SignInScreen(
        state = state,
        actions =
            SignInActions(
                onEmail = viewModel::onEmail,
                onPassword = viewModel::onPassword,
                onCode = viewModel::onCode,
                onSubmit = viewModel::submit,
                onSubmitCode = viewModel::submitCode,
                onGoogle = viewModel::startGoogle,
                onForgotPassword = { navigator.navigate(ResetPasswordRoute) },
                onCreateAccount = { navigator.replace(SignUpRoute) },
                onBack = navigator::back,
                onBackToPassword = viewModel::backToPassword,
            ),
    )
    state.challengeUrl?.let { url ->
        ChallengeSheet(url, onToken = viewModel::onChallengeToken, onDismiss = viewModel::onChallengeDismissed)
    }
}

@Composable
private fun SignUpRouteScreen(
    navigator: Navigator,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event -> if (event == AccountEvent.SignedIn) navigator.back() }
    }
    SignUpScreen(state = state, vm = viewModel, onBack = navigator::back, onSignIn = { navigator.replace(SignInRoute) })
    state.challengeUrl?.let { url ->
        ChallengeSheet(url, onToken = viewModel::onChallengeToken, onDismiss = viewModel::onChallengeDismissed)
    }
}

@Composable
private fun ResetRouteScreen(
    navigator: Navigator,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val done = stringResource(R.string.feature_auth_reset_done)
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is AccountEvent.PasswordReset) {
                Toast.makeText(context, done, Toast.LENGTH_LONG).show()
                navigator.back()
            }
        }
    }
    ResetPasswordScreen(state = state, vm = viewModel, onBack = navigator::back)
    state.challengeUrl?.let { url ->
        ChallengeSheet(url, onToken = viewModel::onChallengeToken, onDismiss = viewModel::onChallengeDismissed)
    }
}
