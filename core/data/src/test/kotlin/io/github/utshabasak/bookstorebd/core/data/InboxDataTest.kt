package io.github.utshabasak.bookstorebd.core.data

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveEvent
import io.github.utshabasak.bookstorebd.core.data.inbox.LiveUpdates
import io.github.utshabasak.bookstorebd.core.model.ImageUpload
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
class InboxDataTest {
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

    @Test
    fun `the push token is filed with this session, and filed again on the next start`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(200, """{"message":"Push notifications are on for this phone.","enabled":true}""")
            respond(200, """{"message":"Push notifications are on for this phone.","enabled":true}""")

            val first = graph.notifications.registerPushToken("fcm-token-1")
            val again = graph.notifications.registerPushToken()

            assertThat(first).isEqualTo(Outcome.Success(true))
            assertThat(again).isEqualTo(Outcome.Success(true))
            repeat(2) {
                val request = server.takeRequest()
                assertThat(request.url.encodedPath).isEqualTo("/api/v1/app/push")
                assertThat(request.body?.utf8()).isEqualTo("""{"refreshToken":"refresh-1","pushToken":"fcm-token-1"}""")
            }
        }

    @Test
    fun `nothing is filed while signed out`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)

            assertThat(graph.notifications.registerPushToken("fcm-token-1")).isEqualTo(Outcome.Success(false))
            assertThat(server.requestCount).isEqualTo(0)
        }

    @Test
    fun `a message is sent as the website sends it, and reading a conversation clears its count`() =
        runTest(UnconfinedTestDispatcher()) {
            val graph = TestGraph(server)
            signedIn(graph)
            respond(
                200,
                """[{"email":"seller@example.com","username":"boipoka","lastMessage":"Yes, it is","lastMessageTime":"2026-10-11T10:00:00Z",
                |"unreadCount":2}]
                """.trimMargin(),
            )
            respond(
                201,
                """{"_id":"m9","sender":"nusrat@example.com","receiver":"seller@example.com","message":"Thanks",
                |"image":"/api/chat/messages/m9/image","timestamp":"2026-10-11T10:05:00Z","read":false}
                """.trimMargin(),
            )
            respond(200, """{"success":true}""")

            graph.chats.refreshConversations()
            assertThat(graph.chats.unread.value).isEqualTo(2)
            val sent = graph.chats.send("seller@example.com", " Thanks ", ImageUpload(byteArrayOf(1, 2), "image/jpeg", "photo.jpg"))
            graph.chats.markRead("seller@example.com")

            assertThat((sent as Outcome.Success).value.imageUrl).isEqualTo(server.url("/api/chat/messages/m9/image").toString())
            server.takeRequest()
            val upload = server.takeRequest().body?.utf8().orEmpty()
            assertThat(upload).contains("name=\"receiver\"")
            assertThat(upload).contains("seller@example.com")
            assertThat(upload).contains("Thanks")
            assertThat(upload).contains("filename=\"photo.jpg\"")
            assertThat(server.takeRequest().body?.utf8()).isEqualTo("""{"sender":"seller@example.com"}""")
            assertThat(graph.chats.unread.value).isEqualTo(0)
        }

    @Test
    fun `the live channel signs in with the access token and passes on a new message`() =
        runBlocking {
            val graph = TestGraph(server)
            signedIn(graph)
            val received = CopyOnWriteArrayList<String>()
            server.enqueue(MockResponse.Builder().webSocketUpgrade(FakeSocketServer(received)).build())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val live: LiveUpdates = graph.live(scope)

            try {
                val next = async(Dispatchers.IO) { withTimeout(TIMEOUT_MS) { live.updates.first() } }
                live.start()
                val event = next.await()

                assertThat(received.first()).isEqualTo("""40{"token":"access-1"}""")
                assertThat((event as LiveEvent.Message).message.text).isEqualTo("Is it still available?")
                assertThat(server.takeRequest().url.encodedPath).isEqualTo("/socket.io/")
            } finally {
                live.stop()
                scope.cancel()
            }
        }

    /** The server's half of Socket.IO, as much as the app uses. */
    private class FakeSocketServer(
        private val received: MutableList<String>,
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            webSocket.send("""0{"sid":"engine","upgrades":[],"pingInterval":25000,"pingTimeout":20000,"maxPayload":1000000}""")
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(code, null)
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            received += text
            if (text.startsWith("40")) {
                webSocket.send("""40{"sid":"socket"}""")
                webSocket.send(
                    """42["receive_message",{"_id":"m1","sender":"seller@example.com","receiver":"nusrat@example.com",""" +
                        """"message":"Is it still available?","image":null,"timestamp":"2026-10-11T10:00:00Z","read":false}]""",
                )
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
