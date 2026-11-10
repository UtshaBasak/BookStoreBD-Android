package io.github.utshabasak.bookstorebd.core.data

import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.data.repository.AssistantRepository
import io.github.utshabasak.bookstorebd.core.data.repository.CatalogueRepository
import io.github.utshabasak.bookstorebd.core.data.util.languageTag
import io.github.utshabasak.bookstorebd.core.database.RecentlyViewed
import io.github.utshabasak.bookstorebd.core.model.AppError
import io.github.utshabasak.bookstorebd.core.model.ChatTurn
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.network.di.AccountNetworkModule
import io.github.utshabasak.bookstorebd.core.network.di.NetworkModule
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AssistantDataTest {
    private val server = MockWebServer()
    private lateinit var graph: TestGraph
    private var language = "en"

    @Before fun start() {
        server.start()
        graph = TestGraph(server)
    }

    @After fun stop() {
        graph.close()
        server.close()
    }

    private val catalogue by lazy {
        CatalogueRepository(
            graph.api,
            graph.cache,
            graph.mappers,
            graph.json,
            graph.database.recentSearches(),
            graph.database.recentlyViewed(),
            graph.settings,
        )
    }

    /** Wired as the app wires it: the signed-in client, with resending switched off. */
    private val assistant by lazy {
        val client = NetworkModule.providesApiClient(graph.base, graph.session, graph.session)
        AssistantRepository(
            AccountNetworkModule.providesAssistantApi(client, graph.config, graph.json),
            catalogue,
            graph.settings,
            { language },
            graph.mappers,
            graph.json,
        )
    }

    private fun respond(
        code: Int,
        body: String,
    ) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private val answer = """{"requestId":"r1","reply":"Delivery is ৳70 in Dhaka."}"""

    private suspend fun view(vararg ids: String) =
        ids.forEachIndexed { index, id -> graph.database.recentlyViewed().put(RecentlyViewed(id, 1_000L + index)) }

    @Test
    fun `a question names the app and its language, and goes to the shop's server`() =
        runTest {
            respond(200, answer)
            language = "bn"

            val result = assistant.ask("  delivery charge koto?  ", emptyList())

            assertThat((result as Outcome.Success).value.text).isEqualTo("Delivery is ৳70 in Dhaka.")
            val request = server.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/api/v1/assistant/chat")
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.headers["X-Client"]).isEqualTo("android/100")
            assertThat(request.headers["Accept-Language"]).isEqualTo("bn")
            assertThat(request.body!!.utf8()).isEqualTo("""{"message":"delivery charge koto?","history":[]}""")
        }

    @Test
    fun `the language follows the app's, with English for anything but Bangla`() {
        assertThat(languageTag(Locale.forLanguageTag("bn-BD"))).isEqualTo("bn")
        assertThat(languageTag(Locale.forLanguageTag("en-GB"))).isEqualTo("en")
        assertThat(languageTag(Locale.forLanguageTag("fr"))).isEqualTo("en")
    }

    @Test
    fun `only the last six turns go back with a question`() =
        runTest {
            respond(200, answer)
            val history = List(9) { ChatTurn(fromUser = it % 2 == 0, text = "turn $it") }

            assistant.ask("and now?", history)

            val body = server.takeRequest().body!!.utf8()
            assertThat(body).doesNotContain("turn 2")
            assertThat(body).contains("""[{"role":"assistant","text":"turn 3"},{"role":"user","text":"turn 4"}""")
            assertThat(body).contains("""{"role":"user","text":"turn 8"}]""")
        }

    @Test
    fun `books viewed go along only while the app may remember them, twenty at most`() =
        runTest {
            respond(200, answer)
            respond(200, answer)
            view(*Array(25) { "book$it" })

            assistant.ask("suggest something", emptyList())
            graph.settings.setRememberViewed(false)
            assistant.ask("suggest something else", emptyList())

            val allowed = server.takeRequest().body!!.utf8()
            assertThat(allowed).contains(""""seen":["book24","book23"""")
            assertThat(Regex("book\\d+").findAll(allowed).count()).isIn(1..20)
            assertThat(server.takeRequest().body!!.utf8()).doesNotContain("seen")
        }

    @Test
    fun `nothing of a conversation is written to the phone`() =
        runTest {
            respond(200, """{"requestId":"r1","reply":"A private answer","books":[{"_id":"b1","title":"Himu","price":300}]}""")

            assistant.ask("a private question", listOf(ChatTurn(true, "an earlier question")))

            assertThat(rowsInDatabase()).isEqualTo(0)
        }

    /** Every row in every table of the app's database. */
    private fun rowsInDatabase(): Int {
        val database = graph.database.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        database.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        return tables
            .filterNot { it.startsWith("sqlite_") || it.startsWith("room_") || it == "android_metadata" }
            .sumOf { table -> database.query("SELECT COUNT(*) FROM `$table`").use { if (it.moveToFirst()) it.getInt(0) else 0 } }
    }

    @Test
    fun `the assistant is offered only when the server says so, asked once a minute`() =
        runTest {
            respond(200, """{"enabled":true}""")

            assertThat(assistant.enabled.value).isFalse()
            assertThat(assistant.isEnabled()).isTrue()
            assertThat(assistant.isEnabled()).isTrue()

            assertThat(assistant.enabled.value).isTrue()
            assertThat(server.requestCount).isEqualTo(1)
            assertThat(server.takeRequest().url.encodedPath).isEqualTo("/api/v1/assistant/config")
        }

    @Test
    fun `an older server, a server in trouble and no connection all mean no assistant`() =
        runTest {
            respond(404, """{"message":"Not found"}""")
            assertThat(assistant.isEnabled()).isFalse()

            val second = TestGraph(server)
            respond(500, "{}")
            assertThat(repositoryOn(second).isEnabled()).isFalse()
            second.close()

            val third = TestGraph(server)
            server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
            assertThat(repositoryOn(third).isEnabled()).isFalse()
            third.close()
        }

    private fun repositoryOn(other: TestGraph): AssistantRepository {
        val client = NetworkModule.providesApiClient(other.base, other.session, other.session)
        return AssistantRepository(
            AccountNetworkModule.providesAssistantApi(client, other.config, other.json),
            catalogue,
            other.settings,
            { language },
            other.mappers,
            other.json,
        )
    }

    @Test
    fun `a switched-off assistant says so in the server's words, and is no longer offered`() =
        runTest {
            respond(200, """{"enabled":true}""")
            respond(503, """{"code":"assistant-off","message":"The assistant is resting today."}""")
            assistant.isEnabled()

            val result = assistant.ask("hello", emptyList())

            assertThat((result as Outcome.Failure).error)
                .isEqualTo(AppError.Rejected(503, "The assistant is resting today.", AssistantRepository.CODE_OFF))
            assertThat(assistant.enabled.value).isFalse()
        }

    @Test
    fun `a question over the limit is refused in the server's words and not sent again`() =
        runTest {
            respond(429, """{"code":"assistant-daily-limit","message":"That is all for today."}""")

            val result = assistant.ask("one more", emptyList())

            assertThat((result as Outcome.Failure).error).isEqualTo(AppError.RateLimited("That is all for today."))
            assertThat(server.requestCount).isEqualTo(1)
        }

    @Test
    fun `a question whose connection drops is not sent again by itself`() =
        runTest {
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
            respond(200, answer)

            val result = assistant.ask("hello", emptyList())

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat(server.requestCount).isEqualTo(1)
        }

    @Test
    fun `a thumbs up carries the answer's id and no text`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())

            val result = assistant.feedback("r1", helpful = true)

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            val request = server.takeRequest()
            assertThat(request.url.encodedPath).isEqualTo("/api/v1/assistant/feedback")
            assertThat(request.headers["X-Client"]).isEqualTo("android/100")
            assertThat(request.body!!.utf8()).isEqualTo("""{"requestId":"r1","helpful":true}""")
        }
}
