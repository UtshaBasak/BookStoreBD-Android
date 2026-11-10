package io.github.utshabasak.bookstorebd.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import io.github.utshabasak.bookstorebd.core.common.ApplicationScope
import io.github.utshabasak.bookstorebd.core.data.inbox.ChatRepository
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The buttons on a message notification: Reply, typed in the notification
 * itself, and Mark as read. Both go through the same repository as the chat
 * screen, with the phone's own session.
 */
@AndroidEntryPoint
class MessageActionReceiver : BroadcastReceiver() {
    @Inject lateinit var chats: ChatRepository

    @Inject lateinit var notifier: PushNotifier

    @Inject lateinit var live: LiveUpdates

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val email = intent.getStringExtra(PushNotifier.EXTRA_CHAT_EMAIL)?.takeIf { it.isNotBlank() } ?: return
        val name = intent.getStringExtra(PushNotifier.EXTRA_CHAT_NAME).orEmpty()
        val reply = replyText(intent)
        val pending = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(WORK_MS) {
                    when (intent.action) {
                        ACTION_REPLY -> if (reply != null) reply(email, name, reply) else notifier.clearMessages(email)
                        ACTION_MARK_READ -> markRead(email)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun reply(
        email: String,
        name: String,
        text: String,
    ) {
        if (chats.send(email, text) is Outcome.Success) {
            markRead(email)
        } else {
            // The words are not lost silently: the notification says so and opens the conversation.
            notifier.showReplyNotSent(email, name)
        }
    }

    private suspend fun markRead(email: String) {
        chats.markRead(email)
        notifier.clearMessages(email)
        live.announce(LiveEvent.Changed)
    }

    companion object {
        const val ACTION_REPLY = "io.github.utshabasak.bookstorebd.action.REPLY"
        const val ACTION_MARK_READ = "io.github.utshabasak.bookstorebd.action.MARK_READ"
        const val KEY_REPLY = "reply"

        /** A broadcast has about ten seconds; the send is given most of it. */
        private const val WORK_MS = 8_000L
        private const val MAX_LENGTH = 2_000

        /** What was typed into the notification, trimmed; null when nothing was. */
        fun replyText(intent: Intent): String? =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(KEY_REPLY)
                ?.toString()
                ?.trim()
                ?.take(MAX_LENGTH)
                ?.takeIf { it.isNotEmpty() }
    }
}
