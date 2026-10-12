package io.github.utshabasak.bookstorebd.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.PageLoadException
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.AppNotification
import io.github.utshabasak.bookstorebd.core.model.NotificationChoice
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the list shows as read: everything when all were marked, or the ones opened. */
data class ReadMarks(
    val all: Boolean = false,
    val ids: Set<String> = emptySet(),
) {
    fun isRead(notification: AppNotification): Boolean = all || notification.read || notification.id in ids
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository,
        live: LiveUpdates,
        session: UserSession,
        config: ConfigRepository,
    ) : ViewModel() {
        private val mutableUnreadOnly = MutableStateFlow(false)

        /** Whether the list shows only the unread ones. */
        val unreadOnly: StateFlow<Boolean> = mutableUnreadOnly.asStateFlow()

        val items: Flow<PagingData<AppNotification>> =
            mutableUnreadOnly.flatMapLatest { repository.notifications(unreadOnly = it) }.cachedIn(viewModelScope)
        val unread: StateFlow<Int> = repository.unread

        val signedIn: StateFlow<Boolean?> =
            session.user.map { it != null }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

        /** The site's address, for links to pages the app does not have. */
        val siteUrl: StateFlow<String> = config.config.map { it?.links?.site.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

        private val marks = MutableStateFlow(ReadMarks())
        val readMarks: StateFlow<ReadMarks> = marks.asStateFlow()

        /** Something new arrived while the list is open: it reloads. */
        val arrivals: Flow<Unit> = live.updates.filter { it is LiveEvent.Notification || it == LiveEvent.Changed }.map { }

        private val failures = Channel<AppError>(Channel.BUFFERED)
        val failureFlow: Flow<AppError> = failures.receiveAsFlow()

        fun markAllRead() {
            viewModelScope.launch {
                when (val result = repository.markRead()) {
                    is Outcome.Success -> marks.value = ReadMarks(all = true)
                    is Outcome.Failure -> failures.send(result.error)
                }
            }
        }

        /** Opening one marks it read, without waiting for the server. */
        fun opened(notification: AppNotification) {
            if (marks.value.isRead(notification)) return
            marks.update { it.copy(ids = it.ids + notification.id) }
            viewModelScope.launch { repository.markRead(listOf(notification.id)) }
        }

        fun setUnreadOnly(only: Boolean) {
            mutableUnreadOnly.value = only
        }

        /** A reload shows the server's own read marks again. */
        fun reloaded() {
            marks.value = ReadMarks()
        }

        private companion object {
            const val STOP_MS = 5_000L
        }
    }

/** Which kinds of notification arrive in the app and by e-mail. */
data class SettingsUiState(
    val choices: List<NotificationChoice> = emptyList(),
    val loading: Boolean = true,
    val error: AppError? = null,
)

@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository,
    ) : ViewModel() {
        private val state = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

        private val failures = Channel<AppError>(Channel.BUFFERED)
        val failureFlow: Flow<AppError> = failures.receiveAsFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                state.update { it.copy(loading = true) }
                state.value =
                    when (val result = repository.choices()) {
                        is Outcome.Success -> SettingsUiState(choices = result.value, loading = false)
                        is Outcome.Failure -> state.value.copy(loading = false, error = result.error)
                    }
            }
        }

        /** Saves at once, as a switch should; puts it back if the server says no. */
        fun change(choice: NotificationChoice) {
            val before = state.value.choices
            val after = before.map { if (it.category == choice.category) choice else it }
            state.update { it.copy(choices = after) }
            viewModelScope.launch {
                when (val result = repository.saveChoices(after)) {
                    is Outcome.Success -> {
                        if (result.value.isNotEmpty()) state.update { it.copy(choices = result.value) }
                    }

                    is Outcome.Failure -> {
                        state.update { it.copy(choices = before) }
                        failures.send(result.error)
                    }
                }
            }
        }
    }

internal fun Throwable.appError(): AppError = (this as? PageLoadException)?.error ?: AppError.Unexpected(this)
