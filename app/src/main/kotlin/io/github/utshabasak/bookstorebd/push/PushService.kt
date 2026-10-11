package io.github.utshabasak.bookstorebd.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import io.github.utshabasak.bookstorebd.core.data.inbox.ActiveConversation
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.data.inbox.NotificationRepository
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Receives pushes. A push says only what kind of thing happened and its id;
 * the details are fetched here over the API with the phone's own session, so
 * nothing personal passes through Google, and a phone that has signed out
 * learns nothing.
 *
 * Firebase calls this on a background thread and allows it a few seconds,
 * which is what the fetch is given.
 */
@AndroidEntryPoint
class PushService : FirebaseMessagingService() {
    @Inject lateinit var tokens: PushTokens

    @Inject lateinit var notifications: NotificationRepository

    @Inject lateinit var chats: ChatRepository

    @Inject lateinit var notifier: PushNotifier

    @Inject lateinit var live: LiveUpdates

    @Inject lateinit var active: ActiveConversation

    override fun onRegistered(token: String) {
        runBlocking { withTimeoutOrNull(FETCH_MS) { tokens.registered(token) } }
    }

    /** Older Play services still announce a refreshed token this way; it is filed the same. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) = onRegistered(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val id = message.data["id"] ?: return
        runBlocking {
            withTimeoutOrNull(FETCH_MS) {
                when (message.data["kind"]) {
                    KIND_NOTIFICATION -> showNotification(id)
                    KIND_CHAT -> showMessage()
                }
            }
        }
    }

    private suspend fun showNotification(id: String) {
        val result = notifications.notification(id)
        if (result !is Outcome.Success) return
        notifier.show(result.value)
        notifications.refreshUnread()
        live.announce(LiveEvent.Changed)
    }

    /** The conversation with the newest unread message, which is the one this push is about. */
    private suspend fun showMessage() {
        val result = chats.refreshConversations()
        live.announce(LiveEvent.Changed)
        if (result !is Outcome.Success) return
        val conversation = result.value.filter { it.unread > 0 }.maxByOrNull { it.lastMessageAt.orEmpty() } ?: return
        if (active.isOpen(conversation.email)) return
        notifier.showMessage(conversation)
    }

    private companion object {
        const val KIND_NOTIFICATION = "notification"
        const val KIND_CHAT = "chat"
        const val FETCH_MS = 8_000L
    }
}
