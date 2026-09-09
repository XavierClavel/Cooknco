package main.com.xavierclavel.servicetests

import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.services.GotenbergPdfRenderer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.ContentType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The backpressure in front of the renderer.
 *
 * Every print is a browser tab on the other side, and the callers are not all polite: the
 * backoffice paces its live preview, but a second operator or a reloaded tab does not. The
 * limit here is the one that actually holds, so it is worth pinning that it holds — and
 * that a print refused for want of a slot is refused quickly rather than left hanging.
 *
 * Driven through a mock engine rather than a container: the question is what the renderer
 * does with concurrent callers, and a real Gotenberg would answer too fast to ask it.
 */
class GotenbergPdfRendererTest {

    /** Lets a test hold every print open until it chooses to let them finish. */
    private class Gate {
        val opened = CompletableDeferred<Unit>()
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()

        fun engine() = MockEngine {
            val now = inFlight.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            opened.await()
            inFlight.decrementAndGet()
            respond(
                content = "%PDF-1.4 fake",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString()),
            )
        }
    }

    @Test
    fun `no more prints run at once than the renderer allows`(): Unit = runBlocking(Dispatchers.Default) {
        val gate = Gate()
        val renderer = GotenbergPdfRenderer(
            baseUrl = "http://gotenberg",
            concurrency = 2,
            queueMillis = 5_000,
            client = HttpClient(gate.engine()),
        )

        val prints = (1..6).map { async { renderer.render("<p>$it</p>") } }
        // Give them every chance to pile up before anything is allowed to finish
        withTimeout(10_000) { while (gate.inFlight.get() < 2) delay(5) }
        delay(100)
        gate.opened.complete(Unit)
        withTimeout(10_000) { prints.awaitAll() }

        assertEquals(2, gate.peak.get(), "more prints reached the renderer at once than allowed")
    }

    @Test
    fun `a print that cannot get a slot is refused rather than left waiting`(): Unit = runBlocking(Dispatchers.Default) {
        val gate = Gate()
        val renderer = GotenbergPdfRenderer(
            baseUrl = "http://gotenberg",
            concurrency = 1,
            queueMillis = 100,
            client = HttpClient(gate.engine()),
        )

        val held = async { renderer.render("<p>holds the only slot</p>") }
        withTimeout(10_000) { while (gate.inFlight.get() < 1) delay(5) }

        val started = System.currentTimeMillis()
        // Under a timeout: without the limit this call reaches the gated engine and waits
        // for ever, and a regression that hangs the suite is barely better than one that
        // passes it.
        val refused = assertFailsWith<ServiceUnavailableException> {
            withTimeout(10_000) { renderer.render("<p>no slot</p>") }
        }
        val waited = System.currentTimeMillis() - started

        assertEquals(ServiceUnavailableCause.PDF_RENDERER_BUSY.key, refused.message)
        assertTrue(waited < 2_000, "the caller waited ${waited}ms rather than being refused")

        gate.opened.complete(Unit)
        withTimeout(10_000) { held.await() }
    }

    /** A slot has to come back even when the print itself failed, or the limit leaks shut. */
    @Test
    fun `a failed print gives its slot back`(): Unit = runBlocking(Dispatchers.Default) {
        val renderer = GotenbergPdfRenderer(
            baseUrl = "http://gotenberg",
            concurrency = 1,
            queueMillis = 100,
            client = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
        )

        repeat(3) {
            val failure = assertFailsWith<ServiceUnavailableException> { renderer.render("<p>boom</p>") }
            assertEquals(ServiceUnavailableCause.PDF_RENDERER_FAILED.key, failure.message)
        }
    }
}
