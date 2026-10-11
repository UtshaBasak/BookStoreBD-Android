package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.network.dto.ChatMessageDto
import io.github.utshabasak.bookstorebd.core.network.dto.ChatPageDto
import io.github.utshabasak.bookstorebd.core.network.dto.ChatReadRequest
import io.github.utshabasak.bookstorebd.core.network.dto.ConversationDto
import io.github.utshabasak.bookstorebd.core.network.dto.DeleteConversationRequest
import io.github.utshabasak.bookstorebd.core.network.dto.MarkReadRequest
import io.github.utshabasak.bookstorebd.core.network.dto.MessageDto
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationDto
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationPageDto
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationSettingsDto
import io.github.utshabasak.bookstorebd.core.network.dto.NotificationSettingsRequest
import io.github.utshabasak.bookstorebd.core.network.dto.PushRegistrationDto
import io.github.utshabasak.bookstorebd.core.network.dto.PushRegistrationRequest
import io.github.utshabasak.bookstorebd.core.network.dto.UnreadCountDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** The notification bell, what the person hears about, and this phone's push token. */
interface NotificationApi {
    @GET("notification")
    suspend fun notifications(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("unreadOnly") unreadOnly: Int? = null,
    ): NotificationPageDto

    @GET("notification/{id}")
    suspend fun notification(
        @Path("id") id: String,
    ): NotificationDto

    @POST("notification/read")
    suspend fun markRead(
        @Body body: MarkReadRequest,
    ): MessageDto

    @GET("user/me/notifications")
    suspend fun settings(): NotificationSettingsDto

    @PUT("user/me/notifications")
    suspend fun updateSettings(
        @Body body: NotificationSettingsRequest,
    ): NotificationSettingsDto

    @POST("app/push")
    suspend fun registerPush(
        @Body body: PushRegistrationRequest,
    ): PushRegistrationDto
}

/** Buyer and seller messages. The other person is always named by e-mail, as on the website. */
interface ChatApi {
    /** The caller's conversations; the path's address is ignored by the server, which always uses the caller's. */
    @GET("chat/history/{email}")
    suspend fun conversations(
        @Path("email") email: String,
    ): List<ConversationDto>

    @GET("chat/messages")
    suspend fun messages(
        @Query("sender") me: String,
        @Query("receiver") other: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): ChatPageDto

    /** Text, a photo, or both: parts `receiver`, `message` and `image`. */
    @Multipart
    @POST("chat/message")
    suspend fun send(
        @Part parts: List<MultipartBody.Part>,
    ): ChatMessageDto

    @POST("chat/read")
    suspend fun markRead(
        @Body body: ChatReadRequest,
    ): MessageDto

    @HTTP(method = "DELETE", path = "chat/delete", hasBody = true)
    suspend fun deleteConversation(
        @Body body: DeleteConversationRequest,
    ): MessageDto

    @GET("chat/unread/{email}")
    suspend fun unread(
        @Path("email") email: String,
    ): UnreadCountDto
}
