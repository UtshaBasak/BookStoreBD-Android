package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.network.dto.CancelOrderDto
import io.github.utshabasak.bookstorebd.core.network.dto.CancelOrderRequest
import io.github.utshabasak.bookstorebd.core.network.dto.CreateReturnDto
import io.github.utshabasak.bookstorebd.core.network.dto.CreateWantedDto
import io.github.utshabasak.bookstorebd.core.network.dto.CreateWantedRequest
import io.github.utshabasak.bookstorebd.core.network.dto.FlagRequest
import io.github.utshabasak.bookstorebd.core.network.dto.InviteListDto
import io.github.utshabasak.bookstorebd.core.network.dto.InviteRequest
import io.github.utshabasak.bookstorebd.core.network.dto.InviteSentDto
import io.github.utshabasak.bookstorebd.core.network.dto.LeaveWantedDto
import io.github.utshabasak.bookstorebd.core.network.dto.MessageDto
import io.github.utshabasak.bookstorebd.core.network.dto.OrderDetailDto
import io.github.utshabasak.bookstorebd.core.network.dto.OrderLineDto
import io.github.utshabasak.bookstorebd.core.network.dto.OwnProfileDto
import io.github.utshabasak.bookstorebd.core.network.dto.PageDto
import io.github.utshabasak.bookstorebd.core.network.dto.PasswordRequest
import io.github.utshabasak.bookstorebd.core.network.dto.ReplyRequest
import io.github.utshabasak.bookstorebd.core.network.dto.ReviewPageDto
import io.github.utshabasak.bookstorebd.core.network.dto.TwoFactorDto
import io.github.utshabasak.bookstorebd.core.network.dto.TwoFactorRequest
import io.github.utshabasak.bookstorebd.core.network.dto.UploadSignatureDto
import io.github.utshabasak.bookstorebd.core.network.dto.WantedItemDto
import io.github.utshabasak.bookstorebd.core.network.dto.WriteReviewRequest
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.QueryMap
import retrofit2.http.Streaming

/** The signed-in person's account: profile, security, invitations and their data. */
interface AccountApi {
    /** Without an e-mail address, the profile of whoever is signed in. */
    @GET("user/profile")
    suspend fun ownProfile(): OwnProfileDto

    /** Every field is sent, as on the website; a blank one clears it. */
    @Multipart
    @PUT("user/profile")
    suspend fun updateProfile(
        @Part parts: List<MultipartBody.Part>,
    ): MessageDto

    @PUT("user/me/two-factor")
    suspend fun setTwoFactor(
        @Body body: TwoFactorRequest,
    ): TwoFactorDto

    @GET("user/me/invites")
    suspend fun invites(): InviteListDto

    @POST("user/me/invites")
    suspend fun invite(
        @Body body: InviteRequest,
    ): InviteSentDto

    /** A JSON file of everything the shop holds about the account. */
    @Streaming
    @GET("user/me/export")
    suspend fun exportData(): ResponseBody

    @HTTP(method = "DELETE", path = "user/me", hasBody = true)
    suspend fun deleteAccount(
        @Body body: PasswordRequest,
    ): MessageDto
}

/** Orders the person placed, and returns. */
interface OrderApi {
    /** Lines of the person's orders; paged by order, so an order is never split across pages. */
    @GET("order/buyer")
    suspend fun buyerOrders(
        @QueryMap query: Map<String, String>,
    ): PageDto<OrderLineDto>

    @GET("order/{orderNumber}")
    suspend fun order(
        @Path("orderNumber") orderNumber: String,
    ): OrderDetailDto

    @POST("order/{orderNumber}/cancel")
    suspend fun cancel(
        @Path("orderNumber") orderNumber: String,
        @Body body: CancelOrderRequest,
    ): CancelOrderDto

    /** 503 when image hosting is not set up; the photos then go in the request itself. */
    @GET("upload/signature")
    suspend fun uploadSignature(): UploadSignatureDto

    @Multipart
    @POST("return")
    suspend fun requestReturn(
        @Part parts: List<MultipartBody.Part>,
    ): CreateReturnDto
}

/**
 * Reviews of books (`review`) and ratings of sellers (`seller-review`), which
 * share their shape, and the Wanted board.
 */
interface CommunityApi {
    @GET("{kind}/{targetId}")
    suspend fun reviews(
        @Path("kind") kind: String,
        @Path("targetId") targetId: String,
    ): ReviewPageDto

    @POST("{kind}/{targetId}")
    suspend fun writeReview(
        @Path("kind") kind: String,
        @Path("targetId") targetId: String,
        @Body body: WriteReviewRequest,
    ): MessageDto

    @DELETE("{kind}/{targetId}")
    suspend fun deleteReview(
        @Path("kind") kind: String,
        @Path("targetId") targetId: String,
    ): MessageDto

    @POST("{kind}/{reviewId}/reply")
    suspend fun reply(
        @Path("kind") kind: String,
        @Path("reviewId") reviewId: String,
        @Body body: ReplyRequest,
    ): MessageDto

    @DELETE("{kind}/{reviewId}/reply")
    suspend fun deleteReply(
        @Path("kind") kind: String,
        @Path("reviewId") reviewId: String,
    ): MessageDto

    @POST("{kind}/{reviewId}/flag")
    suspend fun report(
        @Path("kind") kind: String,
        @Path("reviewId") reviewId: String,
        @Body body: FlagRequest,
    ): MessageDto

    @GET("wanted")
    suspend fun wanted(
        @QueryMap query: Map<String, String>,
    ): PageDto<WantedItemDto>

    @POST("wanted")
    suspend fun askForBook(
        @Body body: CreateWantedRequest,
    ): CreateWantedDto

    @POST("wanted/{id}/join")
    suspend fun joinWanted(
        @Path("id") id: String,
    ): WantedItemDto

    @DELETE("wanted/{id}/join")
    suspend fun leaveWanted(
        @Path("id") id: String,
    ): LeaveWantedDto
}
