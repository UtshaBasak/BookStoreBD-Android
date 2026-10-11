package io.github.utshabasak.bookstorebd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.repository.AuthRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.SettingsRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SessionUser
import io.github.utshabasak.bookstorebd.core.model.SiteLinks
import io.github.utshabasak.bookstorebd.core.model.ThemePreference
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
)

sealed interface AccountEvent {
    data class SignedOutOthers(
        val ended: Int,
    ) : AccountEvent

    data class Failed(
        val error: AppError,
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
    ) : ViewModel() {
        val uiState: StateFlow<AccountUiState> =
            combine(session.user, settings.theme, config.config.map { it?.links }) { user, theme, links ->
                AccountUiState(user = user, sessionKnown = true, theme = theme, links = links)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), AccountUiState())

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
        }
    }
