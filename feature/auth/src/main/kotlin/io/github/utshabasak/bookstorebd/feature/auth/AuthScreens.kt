package io.github.utshabasak.bookstorebd.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.utshabasak.bookstorebd.core.designsystem.theme.BookStoreTheme
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.ui.PasswordChecklist
import io.github.utshabasak.bookstorebd.core.ui.errorMessage

/** The frame every sign-in screen shares: a back arrow, a title, a scrolling form, and an error read aloud when it appears. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthFrame(
    title: Int,
    onBack: () -> Unit,
    error: AppError?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.feature_auth_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            if (error != null) {
                Text(
                    text = errorMessage(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            content()
        }
    }
}

@Composable
fun SignInScreen(
    state: SignInUiState,
    actions: SignInActions,
    modifier: Modifier = Modifier,
) {
    val sentTo = state.codeSentTo
    AuthFrame(
        title = if (sentTo == null) R.string.feature_auth_sign_in_title else R.string.feature_auth_code_title,
        onBack = if (sentTo == null) actions.onBack else actions.onBackToPassword,
        error = state.error,
        modifier = modifier,
    ) {
        if (sentTo != null) {
            Text(stringResource(R.string.feature_auth_code_sent, sentTo), style = MaterialTheme.typography.bodyLarge)
            CodeField(state.code, actions.onCode, onDone = actions.onSubmitCode)
            PrimaryButton(R.string.feature_auth_verify, state.busy, state.code.length == SignInViewModel.CODE_LENGTH, actions.onSubmitCode)
            return@AuthFrame
        }
        EmailField(state.email, actions.onEmail)
        PasswordField(state.password, actions.onPassword, R.string.feature_auth_password, isNew = false, onDone = actions.onSubmit)
        TextButton(onClick = actions.onForgotPassword) { Text(stringResource(R.string.feature_auth_forgot)) }
        PrimaryButton(R.string.feature_auth_sign_in, state.busy, state.email.isNotBlank() && state.password.isNotEmpty(), actions.onSubmit)
        if (state.googleAvailable) {
            HorizontalDivider()
            OutlinedButton(onClick = actions.onGoogle, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.feature_auth_google))
            }
        }
        TextButton(onClick = actions.onCreateAccount, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_auth_no_account))
        }
    }
}

data class SignInActions(
    val onEmail: (String) -> Unit,
    val onPassword: (String) -> Unit,
    val onCode: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onSubmitCode: () -> Unit,
    val onGoogle: () -> Unit,
    val onForgotPassword: () -> Unit,
    val onCreateAccount: () -> Unit,
    val onBack: () -> Unit,
    val onBackToPassword: () -> Unit,
)

@Composable
fun SignUpScreen(
    state: SignUpUiState,
    vm: SignUpViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFrame(
        title = if (state.awaitingCode) R.string.feature_auth_code_title else R.string.feature_auth_sign_up_title,
        onBack = if (state.awaitingCode) vm::editDetails else onBack,
        error = state.error,
        modifier = modifier,
    ) {
        if (state.awaitingCode) {
            Text(stringResource(R.string.feature_auth_sign_up_code_sent, state.email), style = MaterialTheme.typography.bodyLarge)
            CodeField(state.code, vm::onCode, onDone = vm::createAccount)
            PrimaryButton(
                R.string.feature_auth_create_account,
                state.busy,
                state.code.length == SignInViewModel.CODE_LENGTH,
                vm::createAccount,
            )
            TextButton(onClick = vm::sendCode) { Text(stringResource(R.string.feature_auth_send_again)) }
            return@AuthFrame
        }
        OutlinedTextField(
            value = state.username,
            onValueChange = vm::onUsername,
            label = { Text(stringResource(R.string.feature_auth_username)) },
            supportingText = { Text(stringResource(R.string.feature_auth_username_hint)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(state.email, vm::onEmail)
        PasswordField(state.password, vm::onPassword, R.string.feature_auth_password, isNew = true, onDone = vm::sendCode)
        PasswordChecklist(state.passwordCheck)
        PrimaryButton(R.string.feature_auth_send_code, state.busy, state.canSendCode, vm::sendCode)
        Text(
            stringResource(R.string.feature_auth_terms_note),
            style = MaterialTheme.typography.bodySmall,
            color = BookStoreTheme.colors.textMuted,
        )
        TextButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.feature_auth_have_account)) }
    }
}

@Composable
fun ResetPasswordScreen(
    state: ResetUiState,
    vm: ResetPasswordViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthFrame(title = R.string.feature_auth_reset_title, onBack = onBack, error = state.error, modifier = modifier) {
        if (!state.awaitingCode) {
            Text(stringResource(R.string.feature_auth_reset_intro), style = MaterialTheme.typography.bodyLarge)
            EmailField(state.email, vm::onEmail)
            PrimaryButton(R.string.feature_auth_send_code, state.busy, state.email.contains('@'), vm::sendCode)
            return@AuthFrame
        }
        Text(stringResource(R.string.feature_auth_reset_code_sent), style = MaterialTheme.typography.bodyLarge)
        CodeField(state.code, vm::onCode, onDone = {})
        PasswordField(state.password, vm::onPassword, R.string.feature_auth_new_password, isNew = true, onDone = vm::reset)
        PasswordChecklist(state.passwordCheck)
        PrimaryButton(
            R.string.feature_auth_reset_button,
            state.busy,
            state.code.length == SignInViewModel.CODE_LENGTH && state.passwordCheck?.ok == true,
            vm::reset,
        )
    }
}
