package io.github.utshabasak.bookstorebd.push

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.datastore.SettingsStore
import io.github.utshabasak.bookstorebd.core.model.Conversation
import io.github.utshabasak.bookstorebd.core.testing.InMemoryPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MessageNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = PushNotifier(context).also { it.createChannels() }
    private val conversation =
        Conversation("seller@example.com", "boipoka", null, "Yes, it is still available", "2026-11-10T10:00:00Z", unread = 2)

    private fun messageNotification() =
        shadowOf(manager).allNotifications.first { NotificationCompat.getCategory(it) == NotificationCompat.CATEGORY_MESSAGE }

    @Test
    fun `a message pops up with its words, a reply box and Mark as read`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.showMessage(conversation)

        val notification = messageNotification()
        assertThat(shadowOf(notification).contentTitle.toString()).isEqualTo("boipoka")
        assertThat(shadowOf(notification).contentText.toString()).isEqualTo("Yes, it is still available")
        assertThat(manager.getNotificationChannel(notification.channelId).importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        val actions = List(NotificationCompat.getActionCount(notification)) { NotificationCompat.getAction(notification, it)!! }
        assertThat(actions.map { it.title.toString() }).containsExactly("Reply", "Mark as read").inOrder()
        assertThat(actions[0].remoteInputs!!.single().resultKey).isEqualTo(MessageActionReceiver.KEY_REPLY)
        assertThat(actions[1].remoteInputs).isNull()
    }

    @Test
    fun `without the permission nothing is shown`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.showMessage(conversation)

        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun `a reply typed in the notification is read back trimmed, and an empty one is nothing`() {
        fun typed(text: String): Intent {
            val intent = Intent(MessageActionReceiver.ACTION_REPLY)
            val input = RemoteInput.Builder(MessageActionReceiver.KEY_REPLY).build()
            RemoteInput.addResultsToIntent(
                arrayOf(input),
                intent,
                Bundle().apply {
                    putCharSequence(MessageActionReceiver.KEY_REPLY, text)
                },
            )
            return intent
        }

        assertThat(MessageActionReceiver.replyText(typed("  I will take it  "))).isEqualTo("I will take it")
        assertThat(MessageActionReceiver.replyText(typed("   "))).isNull()
        assertThat(MessageActionReceiver.replyText(Intent(MessageActionReceiver.ACTION_MARK_READ))).isNull()
    }

    @Test
    fun `a reply that did not go says so in the same place`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        notifier.showReplyNotSent("seller@example.com", "boipoka")

        val notification = messageNotification()
        assertThat(shadowOf(notification).contentText.toString()).startsWith("Your reply was not sent.")
        assertThat(NotificationCompat.getActionCount(notification)).isEqualTo(0)
    }

    @Test
    fun `Android is asked about notifications once`() =
        runTest {
            val settings = SettingsStore(InMemoryPreferences())

            assertThat(settings.notificationsAsked.first()).isFalse()
            settings.setNotificationsAsked()

            assertThat(settings.notificationsAsked.first()).isTrue()
        }
}
