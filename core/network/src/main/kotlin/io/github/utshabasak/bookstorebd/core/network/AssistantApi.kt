package io.github.utshabasak.bookstorebd.core.network

import io.github.utshabasak.bookstorebd.core.model.AssistantHandover
import io.github.utshabasak.bookstorebd.core.model.AssistantLink
import io.github.utshabasak.bookstorebd.core.model.AssistantOrder
import io.github.utshabasak.bookstorebd.core.model.AssistantOrderBook
import io.github.utshabasak.bookstorebd.core.model.AssistantReply
import io.github.utshabasak.bookstorebd.core.model.AssistantWantedOffer
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantConfigDto
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantFeedbackRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantReplyDto
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * The shopping assistant. The questions go to the shop's own server and
 * nowhere else; the session is sent when there is one, so a signed-in buyer
 * can ask about their orders.
 *
 * [language] is `bn` or `en`: the server uses it for the labels of links and
 * order stages, and for its own messages.
 */
interface AssistantApi {
    @GET("assistant/config")
    suspend fun config(): AssistantConfigDto

    @POST("assistant/chat")
    suspend fun chat(
        @Header("Accept-Language") language: String,
        @Body body: AssistantRequest,
    ): AssistantReplyDto

    /** Answers 204 or 200; the body is not read. */
    @POST("assistant/feedback")
    suspend fun feedback(
        @Header("Accept-Language") language: String,
        @Body body: AssistantFeedbackRequest,
    ): Response<Unit>
}

/** An answer from the wire. Links that are not paths on the shop's site are dropped here, once. */
fun Mappers.assistantReply(dto: AssistantReplyDto): AssistantReply =
    AssistantReply(
        requestId = dto.requestId,
        text = dto.reply,
        books = dto.books.map(::book),
        links = dto.links.map { AssistantLink(it.href, it.label) }.filter { it.isSitePath && it.label.isNotBlank() },
        orders =
            dto.orders.map { order ->
                AssistantOrder(
                    orderNumber = order.orderNumber,
                    placedAt = order.placedAt,
                    statusLabel = order.statusLabel.ifBlank { order.status },
                    stage = order.stage,
                    explanation = order.explanation,
                    books = order.books.map { AssistantOrderBook(it.title, it.quantity) },
                    total = order.total,
                    canCancel = order.canCancel,
                    returnableUntil = order.returnableUntil,
                    link = order.link,
                )
            },
        handover =
            dto.handover?.let { handover ->
                AssistantHandover(
                    toSeller = handover.kind == "seller",
                    chatWith = handover.chatWith?.takeIf { it.isNotBlank() },
                    name = handover.name,
                    bookTitle = handover.bookTitle?.takeIf { it.isNotBlank() },
                    email = handover.email?.takeIf { it.isNotBlank() },
                )
            },
        wanted = dto.wanted?.takeIf { it.title.isNotBlank() }?.let { AssistantWantedOffer(it.title, it.author) },
        fallback = dto.fallback,
        outOfScope = dto.outOfScope,
    )
