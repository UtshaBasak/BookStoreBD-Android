package io.github.utshabasak.bookstorebd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ProfileRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.CodeAction
import io.github.utshabasak.bookstorebd.core.model.IdentityCheck
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.ProfileMode
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import io.github.utshabasak.bookstorebd.core.model.getOrNull
import io.github.utshabasak.bookstorebd.core.ui.CodeState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    /** Null until the session has been read; then the person, or no one. */
    val user: SessionUser? = null,
    val sessionKnown: Boolean = false,
    val theme: ThemePreference = ThemePreference.System,
    val links: SiteLinks? = null,
    /** The signed-in person's profile, once loaded. */
    val profile: OwnProfile? = null,
    /** The side of the account shown, as the website's Buyer and Seller profile switch. */
    val mode: ProfileMode = ProfileMode.Buyer,
    val twoStepBusy: Boolean = false,
    /** Set when the password or code for turning two-step sign-in off was not right. */
    val twoStepProblem: String? = null,
    /** The e-mailed code an account made with Google turns it off with. */
    val twoStepCode: CodeState = CodeState(),
)

sealed interface AccountEvent {
    data class SignedOutOthers(
        val ended: Int,
    ) : AccountEvent

    data class Failed(
        val error: AppError,
    ) : AccountEvent

    data class TwoStepChanged(
        val message: String,
    ) : AccountEvent
}

@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        session: UserSession,
        private val settings: SettingsRepository,
        private val auth: AuthRepository,
        config: ConfigRepository,
        private val profiles: ProfileRepository,
    ) : ViewModel() {
        private val twoStep = MutableStateFlow(TwoStepState())

        val uiState: StateFlow<AccountUiState> =
            combine(
                session.user,
                settings.theme,
                config.config.map {
                    it?.links
                },
                profiles.profile,
                combine(twoStep, settings.profileMode, ::Pair),
            ) { user, theme, links, profile, (step, mode) ->
                AccountUiState(
                    user = user,
                    sessionKnown = true,
                    theme = theme,
                    links = links,
                    profile = profile.takeIf { user != null },
                    mode = ProfileMode.entries.firstOrNull { it.name == mode } ?: ProfileMode.Buyer,
                    twoStepBusy = step.busy,
                    twoStepProblem = step.problem,
                    twoStepCode = step.code,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), AccountUiState())

        /** Each time the tab is shown, so the profile is current after editing it or signing in. */
        fun refreshProfile() {
            viewModelScope.launch { profiles.refresh() }
        }

        /**
         * Turning two-step sign-in off needs the password - or the e-mailed code,
         * for an account made with Google; turning it on is one tap, as on the website.
         */
        fun setTwoStep(
            enabled: Boolean,
            check: IdentityCheck = IdentityCheck(),
        ) {
            viewModelScope.launch {
                twoStep.update { it.copy(busy = true, problem = null) }
                when (val result = profiles.setTwoStep(enabled, check)) {
                    is Outcome.Success -> {
                        twoStep.value = TwoStepState()
                        events.send(AccountEvent.TwoStepChanged(result.value))
                    }

                    is Outcome.Failure -> {
                        val rejected = result.error as? AppError.Rejected
                        if (rejected?.status == HTTP_FORBIDDEN || rejected?.status == HTTP_BAD_REQUEST) {
                            twoStep.update { it.copy(busy = false, problem = rejected.message ?: "") }
                        } else {
                            twoStep.value = TwoStepState()
                            events.send(AccountEvent.Failed(result.error))
                        }
                    }
                }
            }
        }

        /** The code an account with no password turns two-step sign-in off with. */
        fun sendTwoStepCode() {
            viewModelScope.launch {
                twoStep.update { it.copy(code = it.code.copy(sending = true), problem = null) }
                val result = profiles.sendCode(CodeAction.TwoStepOff)
                twoStep.update { it.copy(code = CodeState(sent = result.getOrNull() ?: it.code.sent)) }
                if (result is Outcome.Failure) events.send(AccountEvent.Failed(result.error))
            }
        }

        fun clearTwoStepProblem() {
            twoStep.value = TwoStepState()
        }

        private data class TwoStepState(
            val busy: Boolean = false,
            val problem: String? = null,
            val code: CodeState = CodeState(),
        )

        private val events = Channel<AccountEvent>(Channel.BUFFERED)
        val eventFlow: Flow<AccountEvent> = events.receiveAsFlow()

        fun setMode(mode: ProfileMode) {
            viewModelScope.launch { settings.setProfileMode(mode.name) }
        }

        fun setTheme(theme: ThemePreference) {
            viewModelScope.launch { settings.setTheme(theme) }
        }

        fun signOut() {
            viewModelScope.launch { auth.signOut() }
        }

        fun signOutOthers() {
            viewModelScope.launch {
                when (val result = auth.signOutOthers()) {
                    is Outcome.Success -> events.send(AccountEvent.SignedOutOthers(result.value))
                    is Outcome.Failure -> events.send(AccountEvent.Failed(result.error))
                }
            }
        }

        private companion object {
            const val STOP_MS = 5_000L
            const val HTTP_FORBIDDEN = 403
            const val HTTP_BAD_REQUEST = 400
        }
    }
