package io.github.utshabasak.bookstorebd.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PasswordCheck
import io.github.utshabasak.bookstorebd.core.model.getOrNull
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    /** The server's verdict on the password, checked as it is typed. */
    val passwordCheck: PasswordCheck? = null,
    /** The sign-up code has been sent and is now asked for. */
    val awaitingCode: Boolean = false,
    val code: String = "",
    val busy: Boolean = false,
    val error: AppError? = null,
    val challengeUrl: String? = null,
) {
    val canSendCode: Boolean
        get() = username.trim().length >= MIN_USERNAME && email.contains('@') && passwordCheck?.ok == true && !busy

    private companion object {
        const val MIN_USERNAME = 3
    }
}

sealed interface AccountEvent {
    data object SignedIn : AccountEvent

    data class PasswordReset(
        val message: String,
    ) : AccountEvent
}

/**
 * Creating an account: a username, an address and a password that passes the
 * shop's rules; then the code e-mailed to the address; then the account, signed in.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SignUpViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val config: ConfigRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(SignUpUiState())
        val uiState: StateFlow<SignUpUiState> = state.asStateFlow()
        private val events = Channel<AccountEvent>(Channel.BUFFERED)
        val eventFlow: Flow<AccountEvent> = events.receiveAsFlow()

        init {
            state
                .map { Triple(it.password, it.email, it.username) }
                .distinctUntilChanged()
                .filter { it.first.isNotEmpty() }
                .debounce(CHECK_DEBOUNCE_MS)
                .onEach { (password, email, username) ->
                    auth.checkPassword(password, email, username).getOrNull()?.let { check ->
                        state.update { it.copy(passwordCheck = check) }
                    }
                }.launchIn(viewModelScope)
        }

        fun onUsername(value: String) = state.update { it.copy(username = value.take(MAX_USERNAME), error = null) }

        fun onEmail(value: String) = state.update { it.copy(email = value.trim(), error = null) }

        fun onPassword(value: String) = state.update { it.copy(password = value, passwordCheck = null, error = null) }

        fun onCode(value: String) =
            state.update {
                it.copy(code = value.filter(Char::isDigit).take(SignInViewModel.CODE_LENGTH), error = null)
            }

        fun sendCode() {
            if (!state.value.canSendCode) return
            viewModelScope.launch {
                val settings = config.current()
                if (settings?.captchaRequired == true) state.update { it.copy(challengeUrl = settings.challengeUrl) } else send(null)
            }
        }

        fun onChallengeToken(token: String) {
            state.update { it.copy(challengeUrl = null) }
            viewModelScope.launch { send(token) }
        }

        fun onChallengeDismissed() = state.update { it.copy(challengeUrl = null) }

        /** Checks the code, then creates the account and signs in. */
        fun createAccount() {
            val current = state.value
            if (current.code.length != SignInViewModel.CODE_LENGTH || current.busy) return
            viewModelScope.launch {
                state.update { it.copy(busy = true, error = null) }
                val verified = auth.verifyCode(current.email, current.code)
                if (verified is Outcome.Failure) {
                    state.update { it.copy(busy = false, error = verified.error) }
                    return@launch
                }
                when (val created = auth.signUp(current.username, current.email, current.password)) {
                    is Outcome.Success -> {
                        state.update { it.copy(busy = false) }
                        events.send(AccountEvent.SignedIn)
                    }

                    is Outcome.Failure -> {
                        state.update { it.copy(busy = false, error = created.error) }
                    }
                }
            }
        }

        fun editDetails() = state.update { it.copy(awaitingCode = false, code = "", error = null) }

        private suspend fun send(captchaToken: String?) {
            val current = state.value
            state.update { it.copy(busy = true, error = null) }
            when (val sent = auth.sendSignUpCode(current.email, current.username, captchaToken)) {
                is Outcome.Success -> state.update { it.copy(busy = false, awaitingCode = true) }
                is Outcome.Failure -> state.update { it.copy(busy = false, error = sent.error) }
            }
        }

        private companion object {
            const val CHECK_DEBOUNCE_MS = 400L
            const val MAX_USERNAME = 40
        }
    }

data class ResetUiState(
    val email: String = "",
    val awaitingCode: Boolean = false,
    val code: String = "",
    val password: String = "",
    val passwordCheck: PasswordCheck? = null,
    val busy: Boolean = false,
    val error: AppError? = null,
    val challengeUrl: String? = null,
)

/** Resetting a forgotten password: a code to the address, then a new password. It signs out every device. */
@OptIn(FlowPreview::class)
@HiltViewModel
class ResetPasswordViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val config: ConfigRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(ResetUiState())
        val uiState: StateFlow<ResetUiState> = state.asStateFlow()
        private val events = Channel<AccountEvent>(Channel.BUFFERED)
        val eventFlow: Flow<AccountEvent> = events.receiveAsFlow()

        init {
            state
                .map { it.password to it.email }
                .distinctUntilChanged()
                .filter { it.first.isNotEmpty() }
                .debounce(CHECK_DEBOUNCE_MS)
                .onEach { (password, email) ->
                    auth.checkPassword(password, email, "").getOrNull()?.let { check -> state.update { it.copy(passwordCheck = check) } }
                }.launchIn(viewModelScope)
        }

        fun onEmail(value: String) = state.update { it.copy(email = value.trim(), error = null) }

        fun onCode(value: String) =
            state.update {
                it.copy(code = value.filter(Char::isDigit).take(SignInViewModel.CODE_LENGTH), error = null)
            }

        fun onPassword(value: String) = state.update { it.copy(password = value, passwordCheck = null, error = null) }

        fun sendCode() {
            if (!state.value.email.contains('@') || state.value.busy) return
            viewModelScope.launch {
                val settings = config.current()
                if (settings?.captchaRequired == true) state.update { it.copy(challengeUrl = settings.challengeUrl) } else send(null)
            }
        }

        fun onChallengeToken(token: String) {
            state.update { it.copy(challengeUrl = null) }
            viewModelScope.launch { send(token) }
        }

        fun onChallengeDismissed() = state.update { it.copy(challengeUrl = null) }

        fun reset() {
            val current = state.value
            if (current.code.length != SignInViewModel.CODE_LENGTH || current.passwordCheck?.ok != true || current.busy) return
            viewModelScope.launch {
                state.update { it.copy(busy = true, error = null) }
                when (val result = auth.resetPassword(current.email, current.code, current.password)) {
                    is Outcome.Success -> {
                        state.update { it.copy(busy = false) }
                        events.send(AccountEvent.PasswordReset(result.value))
                    }

                    is Outcome.Failure -> {
                        state.update { it.copy(busy = false, error = result.error) }
                    }
                }
            }
        }

        private suspend fun send(captchaToken: String?) {
            state.update { it.copy(busy = true, error = null) }
            when (val sent = auth.sendResetCode(state.value.email, captchaToken)) {
                is Outcome.Success -> state.update { it.copy(busy = false, awaitingCode = true) }
                is Outcome.Failure -> state.update { it.copy(busy = false, error = sent.error) }
            }
        }

        private companion object {
            const val CHECK_DEBOUNCE_MS = 400L
        }
    }
