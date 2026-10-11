package io.github.utshabasak.bookstorebd.core.data

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.cache.ResponseCache
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.model.SignInOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class SessionTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun respond(
        code: Int,
        body: String,
    ) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun session(
        token: String,
        refresh: String,
    ) = """{"token":"$token","user":{"id":"u1","username":"nusrat","email":"nusrat@example.com","role":"user"},
        |"refreshToken":"$refresh","refreshExpiresAt":"2026-12-01T00:00:00Z"}
        """.trimMargin()

    private fun RecordedRequest.json() = Json.parseToJsonElement(body!!.utf8()).jsonObject

    private suspend fun signedIn(graph: TestGraph) {
        respond(200, session("access-1", "refresh-1"))
        graph.auth.signIn("nusrat@example.com", "Tangerine-Lantern-42!", captchaToken = null)
        server.takeRequest()
    }

    @Test
    fun `signing in sends the phone's new public key and installation id, and keeps the session`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            respond(200, session("access-1", "refresh-1"))

            val result = graph.auth.signIn("nusrat@example.com", "Tangerine-Lantern-42!", captchaToken = "turnstile")

            assertThat((result as Outcome.Success).value).isInstanceOf(SignInOutcome.SignedIn::class.java)
            val sent = server.takeRequest()
            assertThat(sent.url.encodedPath).isEqualTo("/api/v1/app/auth/signin")
            val device = sent.json()["device"]!!.jsonObject
            assertThat(device["publicKey"]!!.jsonPrimitive.content).isEqualTo(graph.keys.publicKey())
            assertThat(device["installId"]!!.jsonPrimitive.content).isEqualTo(graph.settings.installId())
            assertThat(device["name"]!!.jsonPrimitive.content).isEqualTo("Test Phone")
            assertThat(sent.json()["captchaToken"]!!.jsonPrimitive.content).isEqualTo("turnstile")
            assertThat(graph.session.currentAccessToken()).isEqualTo("access-1")
            assertThat(graph.sessionStore.refreshToken()).isEqualTo("refresh-1")
            assertThat(graph.session.user.first()?.username).isEqualTo("nusrat")
            graph.close()
        }

    @Test
    fun `a wrong password comes back as the server's own message`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            respond(401, """{"message":"Invalid email or password"}""")

            val result = graph.auth.signIn("nusrat@example.com", "wrong", captchaToken = null)

            assertThat((result as Outcome.Failure).error).isEqualTo(AppError.Rejected(401, "Invalid email or password"))
            assertThat(graph.sessionStore.refreshToken()).isNull()
            graph.close()
        }

    @Test
    fun `two-step sign-in finishes on the key made at the first step`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            respond(200, """{"twoFactor":true,"sentTo":"n•••@example.com","message":"Enter the code"}""")
            respond(200, session("access-1", "refresh-1"))

            val first = graph.auth.signIn("nusrat@example.com", "Tangerine-Lantern-42!", captchaToken = null)
            val firstKey = server.takeRequest().json()["device"]!!.jsonObject["publicKey"]!!.jsonPrimitive.content
            val second = graph.auth.verifySignIn("nusrat@example.com", "123456")
            val secondKey = server.takeRequest().json()["device"]!!.jsonObject["publicKey"]!!.jsonPrimitive.content

            assertThat((first as Outcome.Success).value).isEqualTo(SignInOutcome.CodeSent("nusrat@example.com", "n•••@example.com"))
            assertThat((second as Outcome.Success).value).isInstanceOf(SignInOutcome.SignedIn::class.java)
            assertThat(secondKey).isEqualTo(firstKey)
            assertThat(graph.keys.created).isEqualTo(1)
            graph.close()
        }

    @Test
    fun `an expired access token is renewed with a request signed by the phone's key`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(401, """{"message":"Authentication required"}""")
            respond(200, session("access-2", "refresh-2"))
            respond(200, "[]")

            val result = graph.cart.refresh()

            assertThat(result).isEqualTo(Outcome.Success(Unit))
            assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer access-1")
            val refresh = server.takeRequest()
            assertThat(refresh.url.encodedPath).isEqualTo("/api/v1/app/auth/refresh")
            assertThat(refresh.json()["refreshToken"]!!.jsonPrimitive.content).isEqualTo("refresh-1")
            assertThat(signatureIsValid(graph, refresh, "refresh-1")).isTrue()
            assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer access-2")
            assertThat(graph.sessionStore.refreshToken()).isEqualTo("refresh-2")
            graph.close()
        }

    @Test
    fun `a session the server has ended is forgotten here, with the account's cached answers and the key`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, """[{"_id":"b1","title":"দেয়াল","price":450,"cartQuantity":1}]""")
            graph.cart.refresh()
            assertThat(graph.cart.cart.first()).hasSize(1)
            respond(401, """{"message":"Authentication required"}""")
            respond(401, """{"message":"Session expired, please sign in again"}""")

            val result = graph.cart.refresh()

            assertThat((result as Outcome.Failure).error).isEqualTo(AppError.SignInRequired)
            assertThat(graph.session.user.first()).isNull()
            assertThat(graph.sessionStore.refreshToken()).isNull()
            assertThat(graph.keys.publicKey()).isNull()
            assertThat(graph.database.cachedResponses().get(ResponseCache.userKey("u1", "cart"))).isNull()
            graph.close()
        }

    @Test
    fun `a server that cannot renew the session right now does not sign anyone out`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(401, """{"message":"Authentication required"}""")
            respond(503, "Service Unavailable")

            graph.cart.refresh()

            assertThat(graph.sessionStore.refreshToken()).isEqualTo("refresh-1")
            assertThat(graph.session.user.first()).isNotNull()
            graph.close()
        }

    @Test
    fun `the cart needs an account`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            assertThat((graph.cart.add("b1") as Outcome.Failure).error).isEqualTo(AppError.SignInRequired)
            assertThat(server.requestCount).isEqualTo(0)
            graph.close()
        }

    /** Checks the signature as the server does (server/utils/deviceKey.ts). */
    private fun signatureIsValid(
        graph: TestGraph,
        request: RecordedRequest,
        refreshToken: String,
    ): Boolean {
        val timestamp = request.headers["X-Device-Timestamp"]!!
        val tokenHash = MessageDigest.getInstance("SHA-256").digest(refreshToken.toByteArray()).joinToString("") { "%02x".format(it) }
        val payload = listOf("POST", "/api/v1/app/auth/refresh", timestamp, tokenHash).joinToString("\n").toByteArray()
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(graph.keys.publicKey())))
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(payload)
            verify(Base64.getDecoder().decode(request.headers["X-Device-Signature"]))
        }
    }
}
