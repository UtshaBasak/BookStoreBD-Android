package io.github.utshabasak.bookstorebd.core.data.inbox

import io.github.utshabasak.bookstorebd.core.data.session.SessionManager
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.ChatMessage
import io.github.utshabasak.bookstorebd.core.model.Conversation
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.map
import io.github.utshabasak.bookstorebd.core.network.ChatApi
import io.github.utshabasak.bookstorebd.core.network.InboxMappers
import io.github.utshabasak.bookstorebd.core.network.apiCall
import io.github.utshabasak.bookstorebd.core.network.dto.ChatReadRequest
import io.github.utshabasak.bookstorebd.core.network.dto.DeleteConversationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** One page of a conversation, oldest first, and whether there are older messages. */
data class ChatPage(
    val messages: List<ChatMessage>,
    val hasOlder: Boolean,
)

/** Buyer and seller messages: the conversations, each thread, sending, and the unread count. */
@Singleton
class ChatRepository
    @Inject
    constructor(
        private val api: ChatApi,
        private val mappers: InboxMappers,
        private val session: SessionManager,
        private val json: Json,
    ) {
        private val list = MutableStateFlow<List<Conversation>?>(null)

        /** The conversations, newest first; null until first loaded. Kept in memory only. */
        val conversations: StateFlow<List<Conversation>?> = list.asStateFlow()

        private val unreadCount = MutableStateFlow(0)

        /** Unread messages across every conversation. */
        val unread: StateFlow<Int> = unreadCount.asStateFlow()

        suspend fun refreshConversations(): Outcome<List<Conversation>> {
            val me = session.currentUser()?.email ?: return Outcome.Failure(AppError.SignInRequired).also { clearLocally() }
            return apiCall(json) { api.conversations(me) }
                .map { rows -> rows.map(mappers::conversation) }
                .also {
                    if (it is Outcome.Success) {
                        list.value = it.value
                        unreadCount.value = it.value.sumOf { conversation -> conversation.unread }
                    }
                }
        }

        suspend fun refreshUnread(): Outcome<Int> {
            val me = session.currentUser()?.email ?: return Outcome.Success(0).also { clearLocally() }
            return apiCall(json) { api.unread(me) }.map { it.count }.also { if (it is Outcome.Success) unreadCount.value = it.value }
        }

        /** A page of the conversation with [other]: page 1 is the newest. */
        suspend fun messages(
            other: String,
            page: Int = 1,
        ): Outcome<ChatPage> {
            val me = session.currentUser()?.email ?: return Outcome.Failure(AppError.SignInRequired)
            return apiCall(json) { api.messages(me, other, page, PAGE_SIZE) }
                .map { dto -> ChatPage(dto.messages.map(mappers::message), hasOlder = dto.messages.size >= PAGE_SIZE) }
        }

        suspend fun send(
            to: String,
            text: String,
            photo: ImageUpload? = null,
        ): Outcome<ChatMessage> {
            val parts =
                buildList {
                    add(MultipartBody.Part.createFormData("receiver", to))
                    add(MultipartBody.Part.createFormData("message", text.trim()))
                    photo?.let {
                        add(MultipartBody.Part.createFormData("image", it.fileName, it.bytes.toRequestBody(it.mimeType.toMediaType())))
                    }
                }
            return apiCall(json) { api.send(parts) }.map(mappers::message)
        }

        /** Marks what [other] has sent as read. */
        suspend fun markRead(other: String): Outcome<Unit> {
            val result = apiCall(json) { api.markRead(ChatReadRequest(other)) }.map { }
            if (result is Outcome.Success) {
                list.update { rows -> rows?.map { if (it.email == other) it.copy(unread = 0) else it } }
                list.value?.let { rows -> unreadCount.value = rows.sumOf { it.unread } } ?: refreshUnread()
            }
            return result
        }

        /** Deletes the conversation for both people, as on the website. */
        suspend fun delete(other: String): Outcome<Unit> {
            val me = session.currentUser()?.email ?: return Outcome.Failure(AppError.SignInRequired)
            val result = apiCall(json) { api.deleteConversation(DeleteConversationRequest(me, other)) }.map { }
            if (result is Outcome.Success) list.update { rows -> rows?.filterNot { it.email == other } }
            return result
        }

        /** A message arrived live: the list shows it at once, ahead of the next refresh. */
        fun noteArrived(message: ChatMessage) {
            list.update { rows ->
                rows?.let {
                    val existing = it.firstOrNull { row -> row.email == message.sender } ?: return@update rows
                    listOf(
                        existing.copy(
                            lastMessage = message.text,
                            lastMessageAt = message.sentAt,
                            unread = existing.unread + 1,
                        ),
                    ) + it.filterNot { row -> row.email == message.sender }
                }
            }
            unreadCount.value += 1
        }

        fun clearLocally() {
            list.value = null
            unreadCount.value = 0
        }

        internal companion object {
            const val PAGE_SIZE = 50
        }
    }
