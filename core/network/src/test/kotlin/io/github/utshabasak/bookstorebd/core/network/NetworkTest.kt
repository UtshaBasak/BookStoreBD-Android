package io.github.utshabasak.bookstorebd.core.network

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.UnavailableItem
import io.github.utshabasak.bookstorebd.core.model.UnavailableReason
import io.github.utshabasak.bookstorebd.core.network.di.NetworkModule
import io.github.utshabasak.bookstorebd.core.network.dto.AddToCartRequest
import io.github.utshabasak.bookstorebd.core.network.dto.CreateOrderRequest
import io.github.utshabasak.bookstorebd.core.network.dto.OrderItemDto
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.atomic.AtomicInteger

class NetworkTest {
    private val server = MockWebServer()
    private val json = NetworkModule.providesJson()
    private val config =
        NetworkConfig(
            apiBaseUrl = "",
            siteUrl = "https://shop.example",
            versionCode = 10203,
            versionName = "1.2.3",
            deviceName = "Pixel 9",
            androidRelease = "17",
            logHttp = false,
        )

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun api(client: OkHttpClient = OkHttpClient()): BookStoreApi =
        Retrofit
            .Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client.newBuilder().addInterceptor(ClientHeadersInterceptor(config)).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BookStoreApi::class.java)

    private fun respond(
        code: Int,
        body: String,
    ) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `reads a book, tolerating fields it does not know and ones the server left out`() =
        runTest {
            respond(200, """{"_id":"b1","title":"দেয়াল","price":450,"salePrice":360,"brandNewField":true}""")

            val result = apiCall(json) { api().book("b1") }

            val book = (result as Outcome.Success).value
            assertThat(book.title).isEqualTo("দেয়াল")
            assertThat(book.salePrice).isEqualTo(360.0)
            assertThat(book.images).isEmpty()
        }

    @Test
    fun `names the app on every request`() =
        runTest {
            respond(200, "[]")
            apiCall(json) { api().cart() }

            val request = server.takeRequest()
            assertThat(request.headers["X-Client"]).isEqualTo("android/10203")
            assertThat(request.headers["User-Agent"]).isEqualTo("BookStoreBD-Android/1.2.3 (10203; Android 17; Pixel 9)")
        }

    @Test
    fun `turns the server's refusals into errors a screen can explain`() =
        runTest {
            respond(400, """{"message":"Validation failed","errors":[{"path":"body.quantity","message":"Too many"}]}""")
            respond(401, """{"message":"Authentication required"}""")
            respond(426, """{"code":"app-update-required","message":"Please update","minVersionCode":10300}""")
            respond(429, """{"message":"Too many requests, please try again later."}""")
            respond(400, """{"code":"captcha","message":"Please complete the security check and try again."}""")
            respond(503, "Service Unavailable")

            val errors = List(6) { (apiCall(json) { api().addToCart("b1", AddToCartRequest(99)) } as Outcome.Failure).error }

            assertThat(errors[0]).isEqualTo(AppError.Rejected(400, "Validation failed", null, mapOf("quantity" to "Too many")))
            assertThat(errors[1]).isEqualTo(AppError.SignInRequired)
            assertThat(errors[2]).isEqualTo(AppError.UpdateRequired)
            assertThat(errors[3]).isEqualTo(AppError.RateLimited("Too many requests, please try again later."))
            assertThat(errors[4]).isEqualTo(AppError.CaptchaRequired)
            assertThat(errors[5]).isEqualTo(AppError.Server(503))
        }

    @Test
    fun `a card's cart button sends no quantity, so copies already in the cart stay`() =
        runTest {
            respond(200, "[]")
            respond(200, "[]")

            apiCall(json) { api().addToCart("b1", AddToCartRequest()) }
            apiCall(json) { api().addToCart("b1", AddToCartRequest(3)) }

            assertThat(server.takeRequest().body!!.utf8()).isEqualTo("{}")
            assertThat(server.takeRequest().body!!.utf8()).isEqualTo("""{"quantity":3}""")
        }

    @Test
    fun `a checkout carries its id and the e-mail address for the order`() =
        runTest {
            respond(200, """{"orderNumber":"BS-1","unavailable":[],"replayed":true}""")
            val request =
                CreateOrderRequest(
                    items = listOf(OrderItemDto("b1", 2)),
                    checkoutId = "c0ffee00-1234",
                    contactName = "Nusrat",
                    contactEmail = "orders@example.com",
                    contactPhone = "01712345678",
                    deliveryDivision = "Dhaka",
                    deliveryDistrict = "Dhaka",
                    deliveryAddress = "Road 5, Dhanmondi",
                )

            val placed = (apiCall(json) { api().placeOrder(request) } as Outcome.Success).value

            val sent = server.takeRequest().body!!.utf8()
            assertThat(sent).contains(""""checkoutId":"c0ffee00-1234"""")
            assertThat(sent).contains(""""contactEmail":"orders@example.com"""")
            assertThat(sent).contains(""""paymentMethod":"Cash on Delivery"""")
            assertThat(placed.replayed).isTrue()
        }

    @Test
    fun `keeps what a refusal lists - what to wait for, and the books that could not be ordered`() =
        runTest {
            respond(
                409,
                """{"code":"account-has-open-business","message":"Not yet","blockers":["An order is on its way.","A payout is owed."]}""",
            )
            respond(
                409,
                """{"message":"None of the selected books can be ordered right now.","unavailable":[""" +
                    """{"bookId":"b1","title":"Deyal","available":0,"requested":2,"reserved":0,"reason":"own-listing"}]}""",
            )

            val blocked = (apiCall(json) { api().cart() } as Outcome.Failure).error as AppError.Rejected
            val refused = (apiCall(json) { api().cart() } as Outcome.Failure).error as AppError.Rejected

            assertThat(blocked.code).isEqualTo("account-has-open-business")
            assertThat(blocked.blockers).containsExactly("An order is on its way.", "A payout is owed.").inOrder()
            assertThat(refused.unavailable)
                .containsExactly(UnavailableItem("b1", "Deyal", 0, UnavailableReason.OwnListing, requested = 2, reserved = 0))
        }

    @Test
    fun `keeps the message of a 401 from a sign-in, which means wrong details`() =
        runTest {
            respond(401, """{"message":"Invalid email or password"}""")
            val error = (apiCall(json, credentials = true) { api().cart() } as Outcome.Failure).error
            assertThat(error).isEqualTo(AppError.Rejected(401, "Invalid email or password"))
        }

    @Test
    fun `reports no connection as offline`() =
        runTest {
            server.close()
            val error = (apiCall(json) { api().cart() } as Outcome.Failure).error
            assertThat(error).isEqualTo(AppError.Offline)
        }

    @Test
    fun `renews the session once on a 401 and repeats the request with the new token`() =
        runTest {
            respond(401, """{"message":"Session expired"}""")
            respond(200, "[]")
            val refreshes = AtomicInteger()
            var token = "old"
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(AccessTokenInterceptor { token })
                    .authenticator(
                        TokenAuthenticator { failed ->
                            refreshes.incrementAndGet()
                            assertThat(failed).isEqualTo("old")
                            token = "new"
                            token
                        },
                    ).build()

            val result = apiCall(json) { api(client).cart() }

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(refreshes.get()).isEqualTo(1)
            assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer old")
            assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer new")
        }

    @Test
    fun `gives up when the session cannot be renewed`() =
        runTest {
            respond(401, """{"message":"Session expired"}""")
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(AccessTokenInterceptor { "old" })
                    .authenticator(TokenAuthenticator { null })
                    .build()

            val error = (apiCall(json) { api(client).cart() } as Outcome.Failure).error

            assertThat(error).isEqualTo(AppError.SignInRequired)
            assertThat(server.requestCount).isEqualTo(1)
        }
}
