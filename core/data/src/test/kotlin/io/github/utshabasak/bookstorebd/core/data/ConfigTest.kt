package io.github.utshabasak.bookstorebd.core.data

import io.github.utshabasak.bookstorebd.core.data.repository.ConfigRepository
import io.github.utshabasak.bookstorebd.core.model.Outcome
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    private fun repository(graph: TestGraph) = ConfigRepository(graph.api, graph.cache, graph.mappers, graph.json)

    private val config =
        """{"android":{"minVersionCode":0},"auth":{},"shop":{"delivery":{"insideDhaka":70,"outsideDhaka":120}},"links":{}}"""

    @Test
    fun `callers at the same moment share one request, even when it fails`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(503).body("""{"message":"Down for a moment"}""").build())
            val configs = repository(TestGraph(server))

            val first = async { configs.refresh() }
            val second = async { configs.refresh() }

            assertTrue(first.await() is Outcome.Failure)
            assertTrue(second.await() is Outcome.Failure)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a fresh answer is reused, and a later caller after a failure asks again`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(503).body("{}").build())
            server.enqueue(MockResponse.Builder().code(200).body(config).build())
            val configs = repository(TestGraph(server))

            assertTrue(configs.refresh() is Outcome.Failure)
            val fetched = configs.refresh()
            val reused = configs.refresh()

            assertEquals(70, (fetched as Outcome.Success).value.delivery.insideDhaka)
            assertTrue(reused is Outcome.Success)
            assertEquals(2, server.requestCount)
        }
}
