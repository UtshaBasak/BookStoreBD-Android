package io.github.utshabasak.bookstorebd.core.data

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.OrderRequest
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.UnavailableReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The cart and checkout as the server now keeps them: it takes ordered books out of the cart itself. */
@RunWith(RobolectricTestRunner::class)
class ShopDataTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun respond(
        code: Int,
        body: String,
    ) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private suspend fun signedIn(graph: TestGraph) {
        respond(
            200,
            """{"token":"access-1","user":{"id":"u1","username":"nusrat","email":"nusrat@example.com","role":"user"},
            |"refreshToken":"refresh-1","refreshExpiresAt":"2026-12-01T00:00:00Z"}
            """.trimMargin(),
        )
        graph.auth.signIn("nusrat@example.com", "Tangerine-Lantern-42!", captchaToken = null)
        server.takeRequest()
    }

    private val request =
        OrderRequest(
            items = listOf("b1" to 2, "b2" to 1),
            checkoutId = "3f2a9c1e-checkout",
            contactName = "Nusrat Jahan",
            contactEmail = "orders@example.com",
            contactPhone = "01712345678",
            division = "Dhaka",
            district = "Dhaka",
            address = "House 5, Road 2",
            note = "",
            promo = null,
        )

    @Test
    fun `an order never empties the cart - the server leaves the books it could not order, and the cart is read again`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(
                200,
                """{"orderNumber":"BS-1","shippingCharge":70,"discount":0,"unavailable":[
                |{"bookId":"b2","title":"Deyal","available":0,"requested":1,"reserved":0,"reason":"sold-out"}]}
                """.trimMargin(),
            )
            respond(200, """[{"_id":"b2","title":"Deyal","price":450,"stock":0,"cartQuantity":1}]""")

            val placed = (graph.checkout.placeOrder(request) as Outcome.Success).value

            val order = server.takeRequest()
            assertThat(order.url.encodedPath).isEqualTo("/api/v1/order/decrease-stock")
            assertThat(order.body!!.utf8()).contains(""""checkoutId":"3f2a9c1e-checkout"""")
            assertThat(order.body!!.utf8()).contains(""""contactEmail":"orders@example.com"""")
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/cart")
            assertThat(server.requestCount).isEqualTo(3)
            assertThat(placed.unavailable.single().reason).isEqualTo(UnavailableReason.SoldOut)
            assertThat(graph.cart.cart.first().map { it.book.id }).containsExactly("b2")
        }

    @Test
    fun `an order the shop refused reads the cart again, and names the books it could not take`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(
                409,
                """{"message":"None of the selected books can be ordered right now.","unavailable":[
                |{"bookId":"b1","title":"Aparajito","available":0,"requested":2,"reserved":0,"reason":"removed"}]}
                """.trimMargin(),
            )
            respond(200, "[]")

            val error = (graph.checkout.placeOrder(request) as Outcome.Failure).error as AppError.Rejected

            assertThat(error.unavailable.single().reason).isEqualTo(UnavailableReason.Removed)
            server.takeRequest()
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/cart")
        }

    @Test
    fun `a refused cart change reads the cart again, so it shows what the shop has`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(409, """{"message":"Only 1 copy is left."}""")
            respond(200, """[{"_id":"b1","title":"Deyal","price":450,"stock":1,"cartQuantity":1}]""")

            val result = graph.cart.setQuantity("b1", 3)

            assertThat(((result as Outcome.Failure).error as AppError.Rejected).message).isEqualTo("Only 1 copy is left.")
            server.takeRequest()
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/cart")
            assertThat(graph.cart.cart.first().single().quantity).isEqualTo(1)
        }

    @Test
    fun `a card's cart button keeps the copies the cart already holds`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, """[{"_id":"b1","title":"Deyal","price":450,"stock":5,"cartQuantity":3}]""")
            respond(409, """{"code":"own-listing","message":"This is your own listing, so it cannot go in your cart."}""")
            respond(200, "[]")

            graph.cart.toggle("b1", inCart = false)
            assertThat(server.takeRequest().body!!.utf8()).isEqualTo("{}")

            val own = (graph.cart.add("b9") as Outcome.Failure).error as AppError.Rejected
            assertThat(own.code).isEqualTo("own-listing")
        }
}
