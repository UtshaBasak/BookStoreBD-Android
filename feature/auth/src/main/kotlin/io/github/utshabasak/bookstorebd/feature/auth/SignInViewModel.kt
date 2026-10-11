package io.github.utshabasak.bookstorebd.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SignInOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    /** Set once the password was right and a two-step code was e-mailed. */
    val codeSentTo: String? = null,
    val code: String = "",
    val busy: Boolean = false,
    val error: AppError? = null,
    /** The bot check page to show before the request goes out; null when none is needed now. */
    val challengeUrl: String? = null,
    val googleAvailable: Boolean = false,
)

sealed interface SignInEvent {
    data object SignedIn : SignInEvent

    /** Ask Google (Credential Manager) for an ID token for [clientId], carrying [nonce]. */
    data class LaunchGoogle(
        val clientId: String,
        val nonce: String,
    ) : SignInEvent
}

/**
 * Sign-in: e-mail and password, behind the bot check when the server has it
 * on; a code by e-mail when two-step sign-in is on; or Google.
 */
@HiltViewModel
class SignInViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val config: ConfigRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(SignInUiState())
        val uiState: StateFlow<SignInUiState> = state.asStateFlow()
        private val events = Channel<SignInEvent>(Channel.BUFFERED)
        val eventFlow: Flow<SignInEvent> = events.receiveAsFlow()
        private var twoStepEmail: String? = null

        init {
            viewModelScope.launch { state.update { it.copy(googleAvailable = config.current()?.googleClientId != null) } }
        }

        fun onEmail(value: String) = state.update { it.copy(email = value.trim(), error = null) }

        fun onPassword(value: String) = state.update { it.copy(password = value, error = null) }

        fun onCode(value: String) = state.update { it.copy(code = value.filter(Char::isDigit).take(CODE_LENGTH), error = null) }

        fun submit() {
            val current = state.value
            if (current.busy || current.email.isBlank() || current.password.isEmpty()) return
            viewModelScope.launch {
                val settings = config.current()
                if (settings?.captchaRequired == true) {
                    state.update { it.copy(challengeUrl = settings.challengeUrl) }
                } else {
                    attempt { auth.signIn(current.email, current.password, captchaToken = null) }
                }
            }
        }

        /** The bot check passed: the sign-in goes out with its token. */
        fun onChallengeToken(token: String) {
            state.update { it.copy(challengeUrl = null) }
            attempt { auth.signIn(state.value.email, state.value.password, token) }
        }

        fun onChallengeDismissed() = state.update { it.copy(challengeUrl = null) }

        fun submitCode() {
            val email = twoStepEmail ?: return
            if (state.value.code.length != CODE_LENGTH || state.value.busy) return
            val code = state.value.code
            attempt { auth.verifySignIn(email, code) }
        }

        /** Starts "Continue with Google": a fresh nonce from the server, then Google's own sheet. */
        fun startGoogle() {
            viewModelScope.launch {
                val clientId = config.current()?.googleClientId ?: return@launch
                state.update { it.copy(busy = true, error = null) }
                when (val nonce = auth.googleNonce()) {
                    is Outcome.Success -> events.send(SignInEvent.LaunchGoogle(clientId, nonce.value))
                    is Outcome.Failure -> state.update { it.copy(busy = false, error = nonce.error) }
                }
            }
        }

        fun onGoogleToken(
            idToken: String,
            nonce: String,
        ) {
            attempt { auth.signInWithGoogle(idToken, nonce) }
        }

        /** The person closed Google's sheet, or the phone has no Google account. */
        fun onGoogleCancelled() = state.update { it.copy(busy = false) }

        fun onGoogleFailed(error: AppError) = state.update { it.copy(busy = false, error = error) }

        fun backToPassword() {
            twoStepEmail = null
            state.update { it.copy(codeSentTo = null, code = "", error = null) }
        }

        /** Sends one sign-in request, with the form waiting, and acts on the answer. */
        private fun attempt(call: suspend () -> Outcome<SignInOutcome>) {
            viewModelScope.launch {
                state.update { it.copy(busy = true, error = null) }
                handle(call())
            }
        }

        private suspend fun handle(result: Outcome<SignInOutcome>) {
            when (result) {
                is Outcome.Success -> {
                    when (val outcome = result.value) {
                        is SignInOutcome.SignedIn -> {
                            state.update { it.copy(busy = false) }
                            events.send(SignInEvent.SignedIn)
                        }

                        is SignInOutcome.CodeSent -> {
                            twoStepEmail = outcome.email
                            state.update { it.copy(busy = false, codeSentTo = outcome.sentTo.ifBlank { outcome.email }, code = "") }
                        }
                    }
                }

                is Outcome.Failure -> {
                    state.update { it.copy(busy = false, error = result.error) }
                }
            }
        }

        companion object {
            const val CODE_LENGTH = 6
        }
    }
