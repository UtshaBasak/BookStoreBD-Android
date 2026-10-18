package io.github.utshabasak.bookstorebd.core.data

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.CodeAction
import io.github.utshabasak.bookstorebd.core.model.Gender
import io.github.utshabasak.bookstorebd.core.model.IdentityCheck
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.OrderStatus
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.PictureChange
import io.github.utshabasak.bookstorebd.core.model.ProfileChanges
import io.github.utshabasak.bookstorebd.core.model.ProfileMode
import io.github.utshabasak.bookstorebd.core.model.ReturnDraft
import io.github.utshabasak.bookstorebd.core.model.ReviewBlock
import io.github.utshabasak.bookstorebd.core.model.ReviewDraft
import io.github.utshabasak.bookstorebd.core.model.ReviewTarget
import io.github.utshabasak.bookstorebd.core.model.SetupItem
import io.github.utshabasak.bookstorebd.core.model.WantedDraft
import io.github.utshabasak.bookstorebd.core.model.WantedResult
import io.github.utshabasak.bookstorebd.core.network.dto.OrderLineDto
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

@RunWith(RobolectricTestRunner::class)
class AccountDataTest {
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

    private val profileJson =
        """{"username":"nusrat","email":"nusrat@example.com","profilePicture":"data:image/png;base64,AAAA",
        |"buyerBanner":"/api/user/nusrat%40example.com/banner/buyer?v=2","sellerBanner":null,
        |"address":"12 Road, Dhanmondi","phone":"01712345678","bkashMerchant":"","dateOfBirth":"","gender":"female",
        |"twoFactor":false,"passwordSet":true}
        """.trimMargin()

    @Test
    fun `the profile is kept under the account, shows the setup steps, and is gone after signing out`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, profileJson)

            val profile = (graph.profiles.refresh() as Outcome.Success).value

            assertThat(profile.pictureUrl).startsWith(server.url("/api/user/nusrat%40example.com/avatar?v=").toString())
            assertThat(profile.buyerBannerUrl).isEqualTo(server.url("/api/user/nusrat%40example.com/banner/buyer?v=2").toString())
            assertThat(profile.gender).isEqualTo(Gender.Female)
            val pending = profile.setupSteps(ProfileMode.Seller).filterNot { it.done }.map { it.item }
            assertThat(pending).containsExactly(SetupItem.Banner, SetupItem.TwoStep, SetupItem.Bkash).inOrder()
            assertThat(graph.profiles.profile.first()?.username).isEqualTo("nusrat")

            respond(204, "")
            graph.session.signOut()
            assertThat(graph.profiles.profile.first()).isNull()
        }

    @Test
    fun `an e-mail change waits for its code, then the session carries on under the new address`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(
                200,
                """{"message":"We have e-mailed a code to new@example.com. Enter it to finish.","pendingEmail":"new@example.com"}""",
            )
            respond(200, profileJson.replace("\"passwordSet\":true", "\"passwordSet\":true,\"pendingEmail\":\"new@example.com\""))
            respond(200, """{"message":"Your account now uses new@example.com.","email":"new@example.com"}""")
            respond(200, profileJson.replace("nusrat@example.com", "new@example.com"))

            val started = graph.profiles.changeEmail(" new@example.com ", "Tangerine-Lantern-42!")
            val sent = server.takeRequest()
            assertThat(sent.url.encodedPath).isEqualTo("/api/v1/user/me/email")
            assertThat(sent.body!!.utf8()).isEqualTo("""{"newEmail":"new@example.com","password":"Tangerine-Lantern-42!"}""")
            server.takeRequest()
            assertThat((started as Outcome.Success).value).contains("new@example.com")
            assertThat(graph.profiles.profile.first()?.pendingEmail).isEqualTo("new@example.com")
            assertThat(graph.session.currentUser()?.email).isEqualTo("nusrat@example.com")

            val moved = graph.profiles.confirmEmail(" 123456 ")
            val confirm = server.takeRequest()
            assertThat(confirm.url.encodedPath).isEqualTo("/api/v1/user/me/email/confirm")
            assertThat(confirm.body!!.utf8()).isEqualTo("""{"code":"123456"}""")
            assertThat(moved).isInstanceOf(Outcome.Success::class.java)
            assertThat(graph.session.currentUser()?.email).isEqualTo("new@example.com")
            assertThat(graph.session.currentUser()?.id).isEqualTo("u1")
        }

    @Test
    fun `an account with no password confirms with an e-mailed code, and an open order keeps it from closing`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, """{"message":"We have e-mailed you a code."}""")
            respond(
                409,
                """{"code":"account-has-open-business","message":"Your account cannot be closed yet.",
                |"blockers":["Order BS-1 is on its way."]}
                """.trimMargin(),
            )
            respond(200, """{"message":"Two-step sign-in is off.","twoFactor":false}""")
            respond(200, profileJson)

            assertThat(graph.profiles.sendCode(CodeAction.DeleteAccount)).isInstanceOf(Outcome.Success::class.java)
            assertThat(server.takeRequest().body!!.utf8()).isEqualTo("""{"action":"delete-account"}""")

            val refused = graph.profiles.deleteAccount(IdentityCheck(code = " 654321 "))
            val delete = server.takeRequest()
            assertThat(delete.method).isEqualTo("DELETE")
            assertThat(delete.body!!.utf8()).isEqualTo("""{"code":"654321"}""")
            val error = (refused as Outcome.Failure).error as AppError.Rejected
            assertThat(error.code).isEqualTo("account-has-open-business")
            assertThat(error.blockers).containsExactly("Order BS-1 is on its way.")
            assertThat(graph.session.currentUser()).isNotNull()

            graph.profiles.setTwoStep(false, IdentityCheck(code = "111222"))
            assertThat(server.takeRequest().body!!.utf8()).isEqualTo("""{"enabled":false,"code":"111222"}""")
        }

    @Test
    fun `saving the profile sends every field, the password only when changed, and removals as blanks`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, """{"message":"Profile updated successfully"}""")
            respond(200, profileJson)

            graph.profiles.update(
                ProfileChanges(
                    username = " nusrat ",
                    address = "12 Road",
                    phone = "017-1234-5678",
                    bkashMerchant = "",
                    dateOfBirth = "1999-05-01",
                    gender = null,
                    picture = PictureChange.Remove,
                    buyerBanner = PictureChange.Replace(ImageUpload(byteArrayOf(1, 2, 3), "image/jpeg", "banner.jpg")),
                ),
            )

            val sent = server.takeRequest()
            assertThat(sent.method).isEqualTo("PUT")
            assertThat(sent.url.encodedPath).isEqualTo("/api/v1/user/profile")
            val body = sent.body!!.utf8()
            assertThat(body).contains("name=\"username\"\r\n\r\nnusrat")
            assertThat(body).contains("name=\"phone\"\r\n\r\n01712345678")
            assertThat(body).contains("name=\"gender\"\r\n\r\n\r\n")
            assertThat(body).contains("name=\"profilePicture\"")
            assertThat(body).contains("name=\"buyerBanner\"; filename=\"banner.jpg\"")
            assertThat(body).doesNotContain("name=\"password\"")
            assertThat(body).doesNotContain("name=\"sellerBanner\"")
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/user/profile")
        }

    @Test
    fun `order lines become orders, with cancelled books left out of the totals`() {
        val graph = TestGraph(server)

        fun line(
            id: String,
            number: String,
            status: String,
            price: Double,
        ) = OrderLineDto(
            id = id,
            orderNumber = number,
            status = status,
            title = "Book $id",
            price = price,
            quantity = 1,
            shippingCharge = 70.0,
        )

        val orders =
            graph.accountMappers.orders(
                listOf(
                    line("a", "ORDER00000000001", "Shipped", 300.0),
                    line("b", "ORDER00000000001", "Order Confirmed", 200.0),
                    line("c", "ORDER00000000001", "Cancelled", 500.0),
                    line("d", "ORDER00000000002", "Cancelled", 150.0),
                ),
            )

        assertThat(orders.map { it.orderNumber }).containsExactly("ORDER00000000001", "ORDER00000000002").inOrder()
        val first = orders[0]
        assertThat(first.status).isEqualTo(OrderStatus.Confirmed)
        assertThat(first.booksTotal).isEqualTo(500.0)
        assertThat(first.total).isEqualTo(570.0)
        assertThat(first.cancelledCount).isEqualTo(1)
        assertThat(orders[1].status).isEqualTo(OrderStatus.Cancelled)
        assertThat(orders[1].total).isEqualTo(0.0)
    }

    @Test
    fun `cancelling sends the reason and refreshes the tracking page`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, """{"message":"Order cancelled","cancelled":2}""")
            respond(200, """{"orderNumber":"ORDER00000000001","status":"Cancelled","books":[],"canCancel":false}""")

            val result = graph.orders.cancel("ORDER00000000001", "  Ordered twice ")

            assertThat((result as Outcome.Success).value).isEqualTo("Order cancelled")
            val sent = server.takeRequest()
            assertThat(sent.url.encodedPath).isEqualTo("/api/v1/order/ORDER00000000001/cancel")
            assertThat(sent.body!!.utf8()).isEqualTo("""{"reason":"Ordered twice"}""")
            assertThat(graph.orders.order("ORDER00000000001").first()?.status).isEqualTo(OrderStatus.Cancelled)
        }

    @Test
    fun `return photos go to the image host without the session token, then their addresses to the API`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            val host = server.url("/image-host/upload").toString()
            respond(200, """{"signature":"sig","timestamp":1700000000,"folder":"bookstorebd","apiKey":"key","uploadUrl":"$host"}""")
            respond(200, """{"secure_url":"https://res.cloudinary.com/demo/image/upload/p1.jpg","public_id":"bookstorebd/p1"}""")
            respond(201, """{"message":"Return requested","returnId":"r1","returnIds":["r1"]}""")

            val result =
                graph.orders.requestReturn(
                    ReturnDraft(
                        listOf("line1", "line2"),
                        "Pages 40 to 52 are missing",
                        "+880 1712-345678",
                        listOf(ImageUpload(byteArrayOf(9), "image/jpeg", "p.jpg")),
                    ),
                )

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/upload/signature")
            val upload = server.takeRequest()
            assertThat(upload.url.encodedPath).isEqualTo("/image-host/upload")
            assertThat(upload.headers["Authorization"]).isNull()
            assertThat(upload.body!!.utf8()).contains("name=\"signature\"\r\n\r\nsig")
            val request = server.takeRequest().body!!.utf8()
            assertThat(request).contains("https://res.cloudinary.com/demo/image/upload/p1.jpg")
            assertThat(request).contains("name=\"refundBkash\"\r\n\r\n01712345678")
            assertThat(request.split("name=\"orderId\"").size - 1).isEqualTo(2)
        }

    @Test
    fun `without an image host the photos travel in the request itself`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(503, """{"message":"Image hosting is not configured","fallback":"inline"}""")
            respond(201, """{"message":"Return requested","returnId":"r1","returnIds":["r1"]}""")

            graph.orders.requestReturn(
                ReturnDraft(listOf("line1"), "Torn cover", "01712345678", listOf(ImageUpload(byteArrayOf(9), "image/jpeg", "p.jpg"))),
            )

            server.takeRequest()
            assertThat(server.takeRequest().body!!.utf8()).contains("name=\"images\"; filename=\"p.jpg\"")
        }

    @Test
    fun `seller ratings use their own path, and the page says why someone cannot review`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            respond(
                200,
                """{"average":4.5,"count":2,"distribution":[0,0,0,1,1],"reviews":[],"mine":null,
                |"canReview":false,"reason":"not-purchased","isSeller":false}
                """.trimMargin(),
            )
            respond(200, """{"_id":"r1","rating":5}""")

            val page = (graph.community.reviews(ReviewTarget.OfSeller("s1")) as Outcome.Success).value
            graph.community.writeReview(ReviewTarget.OfBook("b1"), ReviewDraft(5, " Lovely ", ""))

            assertThat(page.block).isEqualTo(ReviewBlock.NotPurchased)
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/seller-review/s1")
            val write = server.takeRequest()
            assertThat(write.url.encodedPath).isEqualTo("/api/v1/review/b1")
            assertThat(write.body!!.utf8()).isEqualTo("""{"rating":5,"title":"Lovely"}""")
        }

    @Test
    fun `asking for a book that is already listed points to it`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            respond(
                200,
                """{"result":"listed","message":"Good news: it is in the shop already.","book":{"_id":"b9","title":"Aparajito"}}""",
            )

            val result = graph.community.askForBook(WantedDraft(title = "Aparajito"))

            assertThat((result as Outcome.Success).value).isEqualTo(WantedResult.Listed("Good news: it is in the shop already.", "b9"))
        }
}
