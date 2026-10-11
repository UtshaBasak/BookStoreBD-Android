package io.github.utshabasak.bookstorebd.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.utshabasak.bookstorebd.MainActivity
import io.github.utshabasak.bookstorebd.R
import io.github.utshabasak.bookstorebd.core.model.AppNotification
import io.github.utshabasak.bookstorebd.core.model.Conversation
import io.github.utshabasak.bookstorebd.core.model.NotificationCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows pushes as Android notifications, in channels people can tune in the
 * phone's settings, and opens the right screen when one is tapped.
 */
@Singleton
class PushNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val manager = NotificationManagerCompat.from(context)

        /** Creates the channels; safe to call on every start. */
        fun createChannels() {
            val channels =
                Channel.entries.map { channel ->
                    NotificationChannel(channel.id, context.getString(channel.title), channel.importance).apply {
                        description = context.getString(channel.description)
                    }
                }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(channels)
        }

        fun show(notification: AppNotification) {
            val intent =
                openIntent()
                    .putExtra(EXTRA_LINK, notification.link)
                    .putExtra(EXTRA_NOTIFICATION_ID, notification.id)
            post(
                tag = TAG_NOTIFICATION,
                id = notification.id.hashCode(),
                builder =
                    builder(channelFor(notification.category))
                        .setContentTitle(notification.title)
                        .setContentText(notification.body.ifBlank { null })
                        .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                        .setGroup(GROUP_UPDATES)
                        .setContentIntent(pending(notification.id.hashCode(), intent)),
            )
            // Several at once fold into one, which opens the notification list.
            postSummary(GROUP_UPDATES, channelFor(notification.category), openIntent())
        }

        /** The newest unread message from [conversation], one notification per person, replaced as more arrive. */
        fun showMessage(conversation: Conversation) {
            val text = conversation.lastMessage.ifBlank { context.getString(R.string.push_photo) }
            val intent =
                openIntent()
                    .putExtra(EXTRA_CHAT_EMAIL, conversation.email)
                    .putExtra(EXTRA_CHAT_NAME, conversation.name)
            post(
                tag = TAG_CHAT,
                id = conversation.email.hashCode(),
                builder =
                    builder(Channel.Messages)
                        .setContentTitle(conversation.name)
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setNumber(conversation.unread)
                        .setGroup(GROUP_MESSAGES)
                        .setContentIntent(pending(conversation.email.hashCode(), intent)),
            )
            postSummary(GROUP_MESSAGES, Channel.Messages, openIntent().putExtra(EXTRA_MESSAGES, true))
        }

        private fun postSummary(
            group: String,
            channel: Channel,
            intent: Intent,
        ) = post(
            tag = TAG_SUMMARY,
            id = group.hashCode(),
            builder =
                builder(channel)
                    .setGroup(group)
                    .setGroupSummary(true)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                    .setContentIntent(pending(group.hashCode(), intent)),
        )

        /** Clears what is already on screen: the conversation was read, or the person signed out. */
        fun clearMessages(email: String) = manager.cancel(TAG_CHAT, email.hashCode())

        fun clearAll() = manager.cancelAll()

        private fun builder(channel: Channel): NotificationCompat.Builder =
            NotificationCompat
                .Builder(context, channel.id)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setColor(ContextCompat.getColor(context, R.color.splash_icon_background))
                .setAutoCancel(true)
                // On a locked screen, only that something arrived: the text may be private.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(
                    if (channel.importance >=
                        NotificationManager.IMPORTANCE_HIGH
                    ) {
                        NotificationCompat.PRIORITY_HIGH
                    } else {
                        NotificationCompat.PRIORITY_DEFAULT
                    },
                )

        private fun post(
            tag: String,
            id: Int,
            builder: NotificationCompat.Builder,
        ) {
            // Android 13 and newer ask first; older versions allow it unless turned off in settings.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            runCatching { manager.notify(tag, id, builder.build()) }
        }

        private fun openIntent(): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private fun pending(
            requestCode: Int,
            intent: Intent,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** The channels, from what matters most to what can wait. */
        enum class Channel(
            val id: String,
            val title: Int,
            val description: Int,
            val importance: Int,
        ) {
            Orders("orders", R.string.channel_orders, R.string.channel_orders_body, NotificationManager.IMPORTANCE_HIGH),
            Messages("messages", R.string.channel_messages, R.string.channel_messages_body, NotificationManager.IMPORTANCE_HIGH),
            Account("account", R.string.channel_account, R.string.channel_account_body, NotificationManager.IMPORTANCE_HIGH),
            Deals("deals", R.string.channel_deals, R.string.channel_deals_body, NotificationManager.IMPORTANCE_DEFAULT),
            General("general", R.string.channel_general, R.string.channel_general_body, NotificationManager.IMPORTANCE_DEFAULT),
        }

        companion object {
            const val ACTION_OPEN = "io.github.utshabasak.bookstorebd.OPEN"
            const val EXTRA_LINK = "link"
            const val EXTRA_NOTIFICATION_ID = "notificationId"
            const val EXTRA_CHAT_EMAIL = "chatEmail"
            const val EXTRA_CHAT_NAME = "chatName"
            const val EXTRA_MESSAGES = "messages"
            private const val GROUP_UPDATES = "updates"
            private const val GROUP_MESSAGES = "messages"
            private const val TAG_SUMMARY = "summary"
            private const val TAG_NOTIFICATION = "notification"
            private const val TAG_CHAT = "chat"

            fun channelFor(category: NotificationCategory): Channel =
                when (category) {
                    NotificationCategory.Orders, NotificationCategory.Returns, NotificationCategory.Payouts -> Channel.Orders

                    NotificationCategory.Deals, NotificationCategory.Wanted, NotificationCategory.Stock -> Channel.Deals

                    NotificationCategory.Account -> Channel.Account

                    NotificationCategory.Reviews,
                    NotificationCategory.Community,
                    NotificationCategory.Announcements,
                    NotificationCategory.Moderation,
                    -> Channel.General
                }
        }
    }
