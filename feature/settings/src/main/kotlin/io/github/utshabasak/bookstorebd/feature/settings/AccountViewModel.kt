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
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.OwnProfile
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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
    val twoStepBusy: Boolean = false,
    /** Set when the password for turning two-step sign-in off was not right. */
    val twoStepProblem: String? = null,
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
                twoStep,
            ) { user, theme, links, profile, step ->
                AccountUiState(
                    user = user,
                    sessionKnown = true,
                    theme = theme,
                    links = links,
                    profile = profile.takeIf { user != null },
                    twoStepBusy = step.busy,
                    twoStepProblem = step.problem,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), AccountUiState())

        /** Each time the tab is shown, so the profile is current after editing it or signing in. */
        fun refreshProfile() {
            viewModelScope.launch { profiles.refresh() }
        }

        /** Turning two-step sign-in off needs the password; turning it on is one tap, as on the website. */
        fun setTwoStep(
            enabled: Boolean,
            password: String? = null,
        ) {
            viewModelScope.launch {
                twoStep.value = TwoStepState(busy = true)
                when (val result = profiles.setTwoStep(enabled, password)) {
                    is Outcome.Success -> {
                        twoStep.value = TwoStepState()
                        events.send(AccountEvent.TwoStepChanged(result.value))
                    }

                    is Outcome.Failure -> {
                        val rejected = result.error as? AppError.Rejected
                        if (rejected?.status == HTTP_FORBIDDEN) {
                            twoStep.value = TwoStepState(problem = rejected.message ?: "")
                        } else {
                            twoStep.value = TwoStepState()
                            events.send(AccountEvent.Failed(result.error))
                        }
                    }
                }
            }
        }

        fun clearTwoStepProblem() {
            twoStep.value = TwoStepState()
        }

        private data class TwoStepState(
            val busy: Boolean = false,
            val problem: String? = null,
        )

        private val events = Channel<AccountEvent>(Channel.BUFFERED)
        val eventFlow: Flow<AccountEvent> = events.receiveAsFlow()

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
        }
    }
