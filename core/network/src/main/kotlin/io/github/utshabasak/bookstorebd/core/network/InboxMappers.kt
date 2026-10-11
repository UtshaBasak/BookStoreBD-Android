package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.AppNotification
import io.github.utshabasak.bookstorebd.core.model.ChatMessage
import io.github.utshabasak.bookstorebd.core.model.Conversation
import io.github.utshabasak.bookstorebd.core.model.NotificationCategory
import io.github.utshabasak.bookstorebd.core.model.NotificationChoice
import io.github.utshabasak.bookstorebd.core.network.dto.ChatMessageDto
import io.github.utshabasak.bookstorebd.core.network.dto.ConversationDto
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationDto
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationSettingsDto

/** Notifications and messages, from the API's shapes to the app's. */
class InboxMappers(
    private val images: ImageUrls,
) {
    fun notification(dto: NotificationDto): AppNotification =
        AppNotification(
            id = dto.id,
            type = dto.type,
            title = dto.title,
            body = dto.body,
            link = dto.link,
            read = dto.read,
            createdAt = dto.createdAt,
        )

    /** Only the categories the app knows; a new one appears once the app is updated. */
    fun choices(dto: NotificationSettingsDto): List<NotificationChoice> =
        dto.categories.mapNotNull { category ->
            NotificationCategory.ofId(category.id)?.let {
                NotificationChoice(
                    category = it,
                    label = category.label,
                    description = category.description,
                    emailAvailable = category.emailAvailable,
                    inApp = category.inApp,
                    email = category.email,
                )
            }
        }

    fun conversation(dto: ConversationDto): Conversation =
        Conversation(
            email = dto.email,
            name = dto.username.ifBlank { dto.email.substringBefore('@') },
            avatarUrl = images.resolve(dto.profilePicture),
            lastMessage = dto.lastMessage,
            lastMessageAt = dto.lastMessageTime,
            unread = dto.unreadCount,
        )

    fun message(dto: ChatMessageDto): ChatMessage =
        ChatMessage(
            id = dto.id,
            sender = dto.sender,
            receiver = dto.receiver,
            text = dto.message,
            imageUrl = images.resolve(dto.image),
            sentAt = dto.timestamp,
            read = dto.read,
        )
}
