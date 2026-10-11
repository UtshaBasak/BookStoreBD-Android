package io.github.utshabasak.bookstorebd.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    @SerialName("_id") val id: String,
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val link: String = "",
    val read: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto> = emptyList(),
    /** Across all of them: the number on the bell. */
    val unread: Int = 0,
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 0,
    val pageCount: Int = 1,
)

/** No ids: all of them. */
@Serializable
data class MarkReadRequest(
    val ids: List<String>? = null,
)

@Serializable
data class NotificationCategoryDto(
    val id: String,
    val label: String = "",
    val description: String = "",
    val emailAvailable: Boolean = false,
    val inApp: Boolean = true,
    val email: Boolean = true,
)

@Serializable
data class NotificationSettingsDto(
    val categories: List<NotificationCategoryDto> = emptyList(),
)

@Serializable
data class ChannelChoiceDto(
    val inApp: Boolean,
    val email: Boolean,
)

@Serializable
data class NotificationSettingsRequest(
    val prefs: Map<String, ChannelChoiceDto>,
)

@Serializable
data class PushRegistrationRequest(
    val refreshToken: String,
    val pushToken: String,
)

@Serializable
data class PushRegistrationDto(
    val message: String = "",
    val enabled: Boolean = false,
)

@Serializable
data class ConversationDto(
    /** The other person's address. */
    val email: String,
    val username: String = "",
    val profilePicture: String? = null,
    val lastMessage: String = "",
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0,
)

@Serializable
data class ChatMessageDto(
    @SerialName("_id") val id: String,
    val sender: String = "",
    val receiver: String = "",
    val message: String = "",
    val image: String? = null,
    val timestamp: String = "",
    val read: Boolean = false,
)

@Serializable
data class ChatPageDto(
    /** Oldest first, within the page. */
    val messages: List<ChatMessageDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 0,
)

@Serializable
data class ChatReadRequest(
    val sender: String,
)

@Serializable
data class DeleteConversationRequest(
    val user1: String,
    val user2: String,
)

@Serializable
data class UnreadCountDto(
    val count: Int = 0,
)
