package main.com.xavierclavel.other

import com.xavierclavel.services.HttpAppShellSource
import com.xavierclavel.utils.Configuration
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The cache is the part worth pinning down: it is what keeps a public page load from costing
 * a round trip, and what must not outlive a frontend deploy by more than its TTL.
 */
class HttpAppShellSourceTest {

    private fun configuration(url: String) = Configuration(
        smtp = Configuration.Smtp("test", "test"),
        frontend = Configuration.Frontend(url = "https://cooknco.eu", internalUrl = url),
        backend = Configuration.Backend("http://localhost:8080/api/v1"),
        encryption = Configuration.Encryption("6r5HI1W9n4U3tOMS"),
        oauth = Configuration.OAuth(Configuration.OAuth.OAuthProvider("test", "test")),
    )

    /** Counts requests, and lets a test decide what the next one answers. */
    private class Upstream(var body: String? = "<html>v1</html>") {
        val requests = AtomicInteger()
        val requestedUrls = mutableListOf<String>()

        val client = HttpClient(MockEngine { request ->
            requests.incrementAndGet()
            requestedUrls += request.url.toString()
            body
                ?.let { respond(it, HttpStatusCode.OK, headersOf("Content-Type", "text/html")) }
                ?: respondError(HttpStatusCode.NotFound)
        })
    }

    private fun source(upstream: Upstream, url: String = "http://cooknco-frontend", clock: () -> Long = { 0 }) =
        HttpAppShellSource(configuration(url), upstream.client, ttlMillis = 60_000, now = clock)

    @Test
    fun `reads index_html from the frontend service`() = runBlocking {
        val upstream = Upstream()

        assertEquals("<html>v1</html>", source(upstream).fetch())
        assertEquals(listOf("http://cooknco-frontend/index.html"), upstream.requestedUrls)
    }

    @Test
    fun `a trailing slash on the configured url does not double up`() = runBlocking {
        val upstream = Upstream()

        source(upstream, url = "http://cooknco-frontend/").fetch()

        assertEquals(listOf("http://cooknco-frontend/index.html"), upstream.requestedUrls)
    }

    @Test
    fun `repeated reads inside the ttl cost one request`() = runBlocking {
        val upstream = Upstream()
        val source = source(upstream)

        repeat(5) { assertEquals("<html>v1</html>", source.fetch()) }

        assertEquals(1, upstream.requests.get())
    }

    @Test
    fun `the shell is re-read once the ttl has passed`() = runBlocking {
        val upstream = Upstream()
        var clock = 0L
        val source = source(upstream, clock = { clock })

        assertEquals("<html>v1</html>", source.fetch())
        upstream.body = "<html>v2</html>"

        // Still inside the window: the deploy is not picked up yet.
        clock = 59_999
        assertEquals("<html>v1</html>", source.fetch())

        clock = 60_001
        assertEquals("<html>v2</html>", source.fetch())
        assertEquals(2, upstream.requests.get())
    }

    @Test
    fun `nothing cached and nothing readable is no shell at all`() = runBlocking {
        val upstream = Upstream(body = null)

        assertNull(source(upstream).fetch())
    }

    @Test
    fun `a stale shell is served when the frontend stops answering`() = runBlocking {
        val upstream = Upstream()
        var clock = 0L
        val source = source(upstream, clock = { clock })
        assertEquals("<html>v1</html>", source.fetch())

        upstream.body = null
        clock = 120_000

        // Its asset names are almost certainly still valid, and the alternative is the
        // visitor getting an error page instead of the app.
        assertEquals("<html>v1</html>", source.fetch())
    }
}
