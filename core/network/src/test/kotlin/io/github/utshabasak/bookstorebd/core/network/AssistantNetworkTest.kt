package io.github.utshabasak.bookstorebd.core.network

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.network.di.NetworkModule
import io.github.utshabasak.bookstorebd.core.network.dto.ApiErrorDto
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantFeedbackRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantReplyDto
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantRequest
import io.github.utshabasak.bookstorebd.core.network.dto.AssistantTurnDto
import org.junit.Test

class AssistantNetworkTest {
    private val json = NetworkModule.providesJson()
    private val mappers = Mappers(ImageUrls("https://shop.example"))

    private val full =
        """
        {
          "requestId": "b1f2",
          "reply": "Here is what I found.",
          "language": "en",
          "books": [{"_id":"b1","title":"Himu","author":"Humayun Ahmed","price":300,"salePrice":240}],
          "links": [
            {"href":"/filter?search=Humayun%20Ahmed","label":"Search results"},
            {"href":"https://elsewhere.example/page","label":"Away"},
            {"href":"//elsewhere.example","label":"Away too"}
          ],
          "orders": [{
            "orderNumber":"BSB123","placedAt":"2026-11-01T10:00:00.000Z",
            "status":"Shipped","statusLabel":"পাঠানো হয়েছে","stage":2,
            "explanation":"Your order is on its way.",
            "books":[{"title":"Himu","quantity":2}],"total":360,
            "canCancel":false,"returnableUntil":null,"link":"/order-tracking/BSB123"
          }],
          "handover": {"kind":"seller","chatWith":"seller@example.com","name":"rahim_books","bookTitle":"Himu","email":null},
          "wanted": {"title":"The Great Gatsby","author":""},
          "fallback": true,
          "outOfScope": false,
          "somethingNew": 1
        }
        """.trimIndent()

    @Test
    fun `reads a full answer, keeping only links to the shop's own pages`() {
        val reply = mappers.assistantReply(json.decodeFromString<AssistantReplyDto>(full))

        assertThat(reply.requestId).isEqualTo("b1f2")
        assertThat(reply.text).isEqualTo("Here is what I found.")
        assertThat(reply.books.single().title).isEqualTo("Himu")
        assertThat(reply.links.map { it.href }).containsExactly("/filter?search=Humayun%20Ahmed")
        val order = reply.orders.single()
        assertThat(order.orderNumber).isEqualTo("BSB123")
        assertThat(order.statusLabel).isEqualTo("পাঠানো হয়েছে")
        assertThat(order.stage).isEqualTo(2)
        assertThat(order.books.single().quantity).isEqualTo(2)
        assertThat(order.total).isEqualTo(360.0)
        assertThat(order.link).isEqualTo("/order-tracking/BSB123")
        assertThat(reply.handover!!.toSeller).isTrue()
        assertThat(reply.handover!!.chatWith).isEqualTo("seller@example.com")
        assertThat(reply.handover!!.bookTitle).isEqualTo("Himu")
        assertThat(reply.wanted!!.title).isEqualTo("The Great Gatsby")
        assertThat(reply.fallback).isTrue()
        assertThat(reply.outOfScope).isFalse()
    }

    @Test
    fun `reads an answer with almost everything left out`() {
        val reply =
            mappers.assistantReply(
                json.decodeFromString<AssistantReplyDto>("""{"reply":"Sorry, only the shop.","outOfScope":true}"""),
            )

        assertThat(reply.text).isEqualTo("Sorry, only the shop.")
        assertThat(reply.outOfScope).isTrue()
        assertThat(reply.books).isEmpty()
        assertThat(reply.links).isEmpty()
        assertThat(reply.orders).isEmpty()
        assertThat(reply.handover).isNull()
        assertThat(reply.wanted).isNull()
    }

    @Test
    fun `the shop without a chat account is reached by e-mail`() {
        val shop = """{"kind":"shop","chatWith":null,"name":"BookStoreBD","email":"shop@example.com"}"""
        val body = """{"reply":"Talk to the shop.","handover":$shop}"""
        val handover = mappers.assistantReply(json.decodeFromString<AssistantReplyDto>(body)).handover!!

        assertThat(handover.toSeller).isFalse()
        assertThat(handover.chatWith).isNull()
        assertThat(handover.email).isEqualTo("shop@example.com")
    }

    @Test
    fun `reads the assistant's refusals`() {
        val off = json.decodeFromString<ApiErrorDto>("""{"code":"assistant-off","message":"The assistant is switched off."}""")
        assertThat(off.code).isEqualTo("assistant-off")
        assertThat(off.message).isEqualTo("The assistant is switched off.")

        val limit = errorFrom(json, 429, """{"code":"assistant-daily-limit","message":"That is all for today."}""")
        assertThat(limit).isEqualTo(AppError.RateLimited("That is all for today."))
    }

    @Test
    fun `books viewed are left out of a question entirely unless they are given`() {
        val without = json.encodeToString(AssistantRequest.serializer(), AssistantRequest("hello"))
        val with =
            json.encodeToString(
                AssistantRequest.serializer(),
                AssistantRequest("hello", listOf(AssistantTurnDto("user", "hi")), listOf("b1")),
            )

        assertThat(without).isEqualTo("""{"message":"hello","history":[]}""")
        assertThat(with).isEqualTo("""{"message":"hello","history":[{"role":"user","text":"hi"}],"seen":["b1"]}""")
        assertThat(json.encodeToString(AssistantFeedbackRequest.serializer(), AssistantFeedbackRequest("r1", true)))
            .isEqualTo("""{"requestId":"r1","helpful":true}""")
    }
}
