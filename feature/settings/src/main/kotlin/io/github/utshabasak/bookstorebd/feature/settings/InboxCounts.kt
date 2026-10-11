package io.github.utshabasak.bookstorebd.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Unread notifications and messages, for the account page's shortcuts. */
data class InboxCounts(
    val notifications: Int = 0,
    val messages: Int = 0,
)

@HiltViewModel
class InboxCountsViewModel
    @Inject
    constructor(
        notifications: NotificationRepository,
        chats: ChatRepository,
    ) : ViewModel() {
        val counts: StateFlow<InboxCounts> =
            combine(notifications.unread, chats.unread, ::InboxCounts)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), InboxCounts())

        private companion object {
            const val STOP_MS = 5_000L
        }
    }
