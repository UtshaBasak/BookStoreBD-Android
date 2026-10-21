package io.github.utshabasak.bookstorebd.core.data

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.utshabasak.bookstorebd.core.common.AppBuildInfo
import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.data.repository.UpdateRepository
import io.github.utshabasak.bookstorebd.core.model.Outcome
import io.github.utshabasak.bookstorebd.core.network.BaseClientCall
import io.github.utshabasak.bookstorebd.core.network.ReleaseFeed
import kotlinx.coroutines.flow.first
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
class UpdateTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private val sha = "a".repeat(64)

    private fun release(
        name: String,
        code: Int,
    ) = """{"versionName":"$name","versionCode":$code,"apkUrl":"https://github.com/x/$name.apk","apkSize":10,"sha256":"$sha"}"""

    private fun config(latest: String?) =
        """{"android":{"minVersionCode":0,"latest":${latest ?: "null"}},"auth":{},""" +
            """"shop":{"delivery":{"insideDhaka":70,"outsideDhaka":120}},"links":{}}"""

    /** Answers the server's configuration and the GitHub feed with [configCode]/[configBody] and [feedCode]/[feedBody]. */
    private fun answer(
        configCode: Int,
        configBody: String,
        feedCode: Int,
        feedBody: String,
    ) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.url.encodedPath == "/update.json") {
                        MockResponse.Builder().code(feedCode).body(feedBody).build()
                    } else {
                        MockResponse.Builder().code(configCode).body(configBody).build()
                    }
            }
    }

    private fun repository(
        graph: TestGraph,
        selfUpdate: Boolean = true,
    ): UpdateRepository {
        val config = graph.config.copy(releaseFeedUrl = server.url("/update.json").toString())
        return UpdateRepository(
            configRepository = ConfigRepository(graph.api, graph.cache, graph.mappers, graph.json),
            settings = graph.settings,
            gate = graph.gate,
            download = BaseClientCall(graph.base),
            feed = ReleaseFeed(graph.base, graph.json, config),
            build = AppBuildInfo("0.1.0", config.versionCode, selfUpdate, "https://github.com/x/releases"),
            config = config,
            context = ApplicationProvider.getApplicationContext(),
            io = UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `a newer release on GitHub is found even when the server does not know of it`() =
        runTest {
            answer(200, config(latest = null), 200, release("0.2.0", 200))
            val updates = repository(TestGraph(server))

            val found = updates.check()

            assertThat((found as Outcome.Success).value?.versionName).isEqualTo("0.2.0")
            assertThat(updates.updateRequired.first()).isTrue()
        }

    @Test
    fun `the newer of the two answers wins`() =
        runTest {
            answer(200, config(release("0.3.0", 300)), 200, release("0.2.0", 200))
            val updates = repository(TestGraph(server))

            assertThat((updates.check() as Outcome.Success).value?.versionCode).isEqualTo(300)
        }

    @Test
    fun `this build being the newest is not an update`() =
        runTest {
            answer(200, config(latest = null), 200, release("0.1.0", 100))
            val updates = repository(TestGraph(server))

            assertThat((updates.check() as Outcome.Success).value).isNull()
            assertThat(updates.updateRequired.first()).isFalse()
        }

    @Test
    fun `the server being down is not reported as up to date`() =
        runTest {
            answer(503, "{}", 503, "")
            val updates = repository(TestGraph(server))

            assertThat(updates.check()).isInstanceOf(Outcome.Failure::class.java)
        }

    @Test
    fun `either source answering is enough`() =
        runTest {
            answer(503, "{}", 200, release("0.2.0", 200))
            val updates = repository(TestGraph(server))

            assertThat((updates.check() as Outcome.Success).value?.versionCode).isEqualTo(200)
        }

    @Test
    fun `a release without a feed yet is no update, and a feed without a checksum is ignored`() =
        runTest {
            answer(200, config(latest = null), 404, "Not Found")
            assertThat((repository(TestGraph(server)).check() as Outcome.Success).value).isNull()

            answer(200, config(latest = null), 200, release("0.2.0", 200).replace(sha, "not-a-checksum"))
            assertThat((repository(TestGraph(server)).check() as Outcome.Success).value).isNull()
        }

    @Test
    fun `a phone that saw a newer release stays locked while offline`() =
        runTest {
            val graph = TestGraph(server)
            graph.settings.sawVersion(200)
            answer(503, "{}", 503, "")

            assertThat(repository(graph).updateRequired.first()).isTrue()
        }

    @Test
    fun `the Play build is never locked by a newer GitHub release`() =
        runTest {
            answer(200, config(release("0.2.0", 200)), 200, release("0.2.0", 200))
            val updates = repository(TestGraph(server), selfUpdate = false)

            updates.check()

            assertThat(updates.updateRequired.first()).isFalse()
        }
}
