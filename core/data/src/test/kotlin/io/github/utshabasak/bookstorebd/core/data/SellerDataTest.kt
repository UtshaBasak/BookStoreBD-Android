package io.github.utshabasak.bookstorebd.core.data

import androidx.paging.testing.asSnapshot
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.BookType
import io.github.utshabasak.bookstorebd.core.model.Discount
import io.github.utshabasak.bookstorebd.core.model.DiscountKind
import io.github.utshabasak.bookstorebd.core.model.DiscountProblem
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.ListingDraft
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PayoutState
import io.github.utshabasak.bookstorebd.core.model.Pricing
import io.github.utshabasak.bookstorebd.core.model.SalesQuery
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SellerDataTest {
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
            """{"token":"access-1","user":{"id":"u1","username":"boipoka","email":"seller@example.com","role":"user"},
            |"refreshToken":"refresh-1","refreshExpiresAt":"2026-12-01T00:00:00Z"}
            """.trimMargin(),
        )
        graph.auth.signIn("seller@example.com", "Tangerine-Lantern-42!", captchaToken = null)
        server.takeRequest()
    }

    @Test
    fun `pricing follows the website's rules`() {
        assertThat(Pricing.salePrice(300, Discount(DiscountKind.Percent, 15))).isEqualTo(255)
        assertThat(Pricing.salePrice(300, Discount(DiscountKind.Amount, 50))).isEqualTo(250)
        assertThat(Pricing.problem(300, Discount(DiscountKind.Percent, 91))).isNotNull()
        assertThat(Pricing.problem(300, Discount(DiscountKind.Amount, 300))).isNotNull()
        assertThat(Pricing.problem(300, Discount(DiscountKind.Amount, 271))).isNotNull()
        assertThat(Pricing.problem(0, Discount(DiscountKind.Percent, 10))).isNotNull()
        assertThat(Pricing.salePrice(300, Discount(DiscountKind.Amount, 400))).isEqualTo(300)
        assertThat(Pricing.sellerReceives(1050.0)).isEqualTo(997.5)
    }

    @Test
    fun `a percentage that rounds to nothing off is no discount, as the shop refuses it`() {
        // 1% of 40 Tk rounds to 40: nothing saved.
        assertThat(Pricing.problem(40, Discount(DiscountKind.Percent, 1))).isEqualTo(DiscountProblem.TooSmall)
        assertThat(Pricing.salePrice(40, Discount(DiscountKind.Percent, 1))).isEqualTo(40)
        assertThat(Pricing.problem(400, Discount(DiscountKind.Percent, 1))).isNull()
    }

    @Test
    fun `a listing sends its photos first, then the form, leaving blank details out as the website does`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(503, """{"message":"Image hosting is not set up","fallback":"inline"}""")
            respond(201, """{"message":"Book added successfully!","book":{"_id":"b9","title":"Aparajito"},"waiting":2}""")

            val result =
                graph.sellers.publish(
                    ListingDraft(
                        title = " Aparajito ",
                        author = "Bibhutibhushan",
                        categories = setOf("Novels", "Classics"),
                        type = BookType.Old,
                        condition = "good",
                        price = "300",
                        discount = Discount(DiscountKind.Percent, 10),
                    ),
                    listOf(ImageUpload(byteArrayOf(1, 2), "image/jpeg", "photo.jpg")),
                )

            assertThat((result as Outcome.Success).value.waiting).isEqualTo(2)
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/upload/signature")
            val form = server.takeRequest()
            val body = form.body?.utf8().orEmpty()
            assertThat(form.url.encodedPath).isEqualTo("/api/v1/user/add-book")
            assertThat(body).contains("name=\"title\"\r\n\r\nAparajito\r\n")
            assertThat(body).doesNotContain("name=\"publisher\"")
            assertThat(body).doesNotContain("name=\"desc\"")
            assertThat(body).doesNotContain("N/A")
            assertThat(Regex("name=\"category\"").findAll(body).count()).isEqualTo(2)
            assertThat(body).contains("name=\"condition\"")
            assertThat(body).contains("name=\"discountType\"")
            assertThat(body).contains("filename=\"photo.jpg\"")
        }

    @Test
    fun `a new book is listed with no condition, whatever the form last held`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(201, """{"message":"Book added successfully!","book":{"_id":"b9"},"waiting":0}""")

            graph.sellers.publish(
                ListingDraft(
                    title = "Deyal",
                    author = "Humayun Ahmed",
                    publisher = "Anyaprokash",
                    categories = setOf("Novels"),
                    type = BookType.New,
                    condition = "good",
                    conditionDetails = "Left over",
                    price = "450",
                ),
                emptyList(),
            )

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertThat(body).contains("name=\"publisher\"\r\n\r\nAnyaprokash\r\n")
            assertThat(body).doesNotContain("name=\"condition\"")
            assertThat(body).doesNotContain("name=\"conditionDetails\"")
        }

    @Test
    fun `a seller's order stands where their own books do, not another seller's`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(
                200,
                """{"orderNumber":"BS-1","status":"Order Confirmed","books":[
                |{"_id":"l1","orderNumber":"BS-1","status":"Shipped","sellerEmail":"seller@example.com","title":"Mine","price":100},
                |{"_id":"l2","orderNumber":"BS-1","status":"Order Confirmed","sellerEmail":"other@example.com","title":"Theirs","price":100}
                |]}
                """.trimMargin(),
            )

            val order = (graph.sellers.order("BS-1") as Outcome.Success).value

            assertThat(order.lines.map { it.title }).containsExactly("Mine")
            assertThat(order.status).isEqualTo(OrderStatus.Shipped)
        }

    @Test
    fun `listing without a bKash number is refused with the website's code`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(409, """{"code":"payout-number-required","message":"Add your bKash merchant number to your profile first."}""")

            val result =
                graph.sellers.publish(
                    ListingDraft(title = "A", author = "B", categories = setOf("Novels"), price = "100"),
                    emptyList(),
                )

            assertThat(((result as Outcome.Failure).error as AppError.Rejected).code).isEqualTo("payout-number-required")
        }

    @Test
    fun `my listings carry their discount and how many buyers wait for the sold-out ones`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            // The two requests go out together, so each is answered by its path.
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when (request.url.encodedPath) {
                            "/api/v1/book/requests/mine" -> {
                                MockResponse.Builder().body("""{"b1":3}""").build()
                            }

                            else -> {
                                MockResponse
                                    .Builder()
                                    .body(
                                        """[{"_id":"b1","title":"Pather Panchali","price":400,"salePrice":360,"stock":0,
"discountType":"percent","discountValue":10},
{"_id":"b2","title":"Aparajito","price":300,"stock":2}]
                                        """.trimMargin(),
                                    ).build()
                            }
                        }
                }

            val listings = (graph.sellers.listings() as Outcome.Success).value

            assertThat(listings.map { it.waiting }).containsExactly(3, 0).inOrder()
            assertThat(listings[0].discount).isEqualTo(Discount(DiscountKind.Percent, 10))
            assertThat(listings[1].discount.kind).isEqualTo(DiscountKind.None)
            val paths = listOf(server.takeRequest(), server.takeRequest()).map { it.url.encodedPath }
            assertThat(paths).containsExactly("/api/v1/book/requests/mine", "/api/v1/book/seller/seller@example.com")
        }

    @Test
    fun `a discount is removed with none, and a status is sent as the website names it`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, """{"message":"Discount removed"}""")
            respond(200, """[]""")

            graph.sellers.setDiscount("b1", Discount())
            graph.sellers.setStatus("ORDER00000000001", OrderStatus.Shipped)

            assertThat(server.takeRequest().body?.utf8()).isEqualTo("""{"type":"none"}""")
            val status = server.takeRequest()
            assertThat(status.method).isEqualTo("PATCH")
            assertThat(status.body?.utf8()).isEqualTo("""{"status":"Shipped"}""")
        }

    @Test
    fun `a sale says where its payout stands`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(
                200,
                """{"items":[{"_id":"l1","orderNumber":"ORDER00000000001","status":"Delivered",
                |"buyerEmail":"b@example.com","title":"Aparajito",
                |"price":300,"quantity":1,"payoutState":"paid","sellerPaidAt":"2026-10-20T00:00:00Z","sellerPayoutRef":"TRX123456"}],
                |"total":1,"page":1,"pageSize":25,"pageCount":1}
                """.trimMargin(),
            )

            val sale = graph.sellers.sales(SalesQuery(status = OrderStatus.Delivered)).asSnapshot().single()
            assertThat(sale.payout).isEqualTo(PayoutState.Paid)
            assertThat(PayoutState.from("cancelled")).isEqualTo(PayoutState.Cancelled)
            assertThat(sale.payoutRef).isEqualTo("TRX123456")
            assertThat(server.takeRequest().url.queryParameter("status")).isEqualTo("Delivered")
        }
}
