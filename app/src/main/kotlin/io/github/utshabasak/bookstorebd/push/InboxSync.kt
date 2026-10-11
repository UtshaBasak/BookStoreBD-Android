package io.github.utshabasak.bookstorebd.push

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.utshabasak.bookstorebd.core.common.ApplicationScope
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.data.session.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the bell and the messages badge right. While the app is in front and
 * someone is signed in, the live channel is open and the counts are fresh; in
 * the background the channel closes and pushes take over. Signing out clears
 * everything at once.
 */
@Singleton
class InboxSync
    @Inject
    constructor(
        private val session: UserSession,
        private val live: LiveUpdates,
        private val notifications: NotificationRepository,
        private val chats: ChatRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            scope.launch(Dispatchers.Main) {
                ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        session.user
                            .map { it?.id }
                            .distinctUntilChanged()
                            .collect { id ->
                                if (id == null) {
                                    live.stop()
                                    notifications.clearLocally()
                                    chats.clearLocally()
                                } else {
                                    live.start()
                                    launch { notifications.refreshUnread() }
                                    launch { chats.refreshUnread() }
                                }
                            }
                    } finally {
                        // Left the foreground: pushes carry on from here.
                        live.stop()
                    }
                }
            }
            live.updates
                .onEach { event ->
                    when (event) {
                        is LiveEvent.Notification -> notifications.noteArrived()
                        is LiveEvent.Message -> chats.noteArrived(event.message)
                        LiveEvent.Changed -> Unit
                    }
                }.launchIn(scope)
        }
    }
