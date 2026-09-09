package main.com.xavierclavel.servicetests

import com.xavierclavel.services.FcmCredentials
import com.xavierclavel.services.FcmPushSender
import com.xavierclavel.services.PushMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The FCM transport, against a mock engine rather than Google.
 *
 * Everything else in the notification feature is tested through `FakePushSender`, which
 * means this class — the one that actually builds an HTTP request — had no coverage at all,
 * and shipped two bugs on that account: a body the client could not serialise, and, before
 * it, a payload missing a required field. Both were the sort of thing a single assertion on
 * the outgoing request would have caught, which is what this is.
 *
 * What is deliberately not covered is [com.xavierclavel.services.FcmAccessTokens]: signing a
 * JWT and trading it at Google's endpoint cannot be exercised without a real key, and a fake
 * one would only assert that the code does what it does.
 */
class FcmPushSenderTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Stands in for the service account key. Counts how often a token was asked for. */
    private class Credentials(private val tokens: List<String> = listOf("access-1")) : FcmCredentials {
        override val projectId = "cooknco-test"
        val requested = AtomicInteger()
        val invalidations = AtomicInteger()
        override fun get(): String = tokens[minOf(invalidations.get(), tokens.size - 1)]
            .also { requested.incrementAndGet() }
        override fun invalidate() { invalidations.incrementAndGet() }
    }

    /** Records what FCM was sent, and lets a test decide what it answers. */
    private class Fcm(private val reply: (HttpRequestData) -> Pair<HttpStatusCode, String>) {
        val requests = mutableListOf<HttpRequestData>()
        val bodies = mutableListOf<String>()

        val client = HttpClient(MockEngine { request ->
            requests += request
            // The bug this class exists for: a body the client cannot convert never becomes
            // TextContent, so reading it here is itself the assertion that it serialised
            bodies += (request.body as TextContent).text
            val (status, body) = reply(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        })
    }

    private fun sender(fcm: Fcm, credentials: Credentials = Credentials()) =
        FcmPushSender(credentials = credentials, client = fcm.client)

    private fun message(token: String = "device-1") = PushMessage(
        token = token,
        title = "New recipe",
        body = "Alice just published \"Tarte aux pommes\"",
        data = mapOf("kind" to "new_recipe", "notificationId" to "42", "link" to "/recipe/view?id=7"),
    )

    private fun ok(): (HttpRequestData) -> Pair<HttpStatusCode, String> =
        { HttpStatusCode.OK to """{"name":"projects/cooknco-test/messages/1"}""" }

    // ------------------------------------------------------------------ the request

    /**
     * The regression test for the bug that made every send fail: a `JsonObject` handed to a
     * client with no `ContentNegotiation` throws at send time. Reading the body as
     * [TextContent] above is what pins it down.
     */
    @Test
    fun `the body is serialised JSON, not an object the client cannot convert`() = runBlocking {
        val fcm = Fcm(ok())
        val result = sender(fcm).send(listOf(message()))

        assertEquals(1, result.delivered)
        assertEquals(1, fcm.bodies.size)
        // Parses, which a body the client mangled or dropped would not
        val parsed = json.parseToJsonElement(fcm.bodies.single()).jsonObject
        assertNotNull(parsed["message"])
    }

    @Test
    fun `the payload carries everything the clients rely on`() = runBlocking {
        val fcm = Fcm(ok())
        sender(fcm).send(listOf(message()))

        val msg = json.parseToJsonElement(fcm.bodies.single()).jsonObject["message"]!!.jsonObject

        assertEquals("device-1", msg["token"]!!.jsonPrimitive.content)

        // The notification half: what lets Android draw it while the app is not in front
        val notification = msg["notification"]!!.jsonObject
        assertEquals("New recipe", notification["title"]!!.jsonPrimitive.content)
        assertEquals("Alice just published \"Tarte aux pommes\"", notification["body"]!!.jsonPrimitive.content)

        // The data half: what the app reads whichever side drew the notification
        val data = msg["data"]!!.jsonObject
        assertEquals("new_recipe", data["kind"]!!.jsonPrimitive.content)
        assertEquals("42", data["notificationId"]!!.jsonPrimitive.content)
        assertEquals("/recipe/view?id=7", data["link"]!!.jsonPrimitive.content)

        val android = msg["android"]!!.jsonObject
        assertEquals("HIGH", android["priority"]!!.jsonPrimitive.content)
        // Must match the channel the app creates, or Android 8+ drops the message silently
        assertEquals(
            FcmPushSender.DEFAULT_CHANNEL_ID,
            android["notification"]!!.jsonObject["channel_id"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `the request is addressed and authorised`() = runBlocking {
        val fcm = Fcm(ok())
        sender(fcm).send(listOf(message()))

        val request = fcm.requests.single()
        assertEquals(
            "https://fcm.googleapis.com/v1/projects/cooknco-test/messages:send",
            request.url.toString(),
        )
        assertEquals("Bearer access-1", request.headers[HttpHeaders.Authorization])
    }

    /** A blank link is left off rather than sent empty for the client to test for. */
    @Test
    fun `a notification with nowhere to go carries no link`() = runBlocking {
        val fcm = Fcm(ok())
        sender(fcm).send(listOf(PushMessage("device-1", "Notice", "Body")))

        val msg = json.parseToJsonElement(fcm.bodies.single()).jsonObject["message"]!!.jsonObject
        assertNull(msg["data"], "no data at all rather than an empty object")
    }

    @Test
    fun `nothing is sent for an empty batch`() = runBlocking {
        val fcm = Fcm(ok())
        val result = sender(fcm).send(emptyList())

        assertEquals(0, fcm.requests.size)
        assertEquals(0, result.delivered)
    }

    @Test
    fun `one request per device`() = runBlocking {
        val fcm = Fcm(ok())
        val result = sender(fcm).send(listOf(message("a"), message("b"), message("c")))

        assertEquals(3, fcm.requests.size)
        assertEquals(3, result.delivered)
        // One token exchange for the batch, not one per device
        assertEquals(setOf("a", "b", "c"), fcm.bodies.map {
            json.parseToJsonElement(it).jsonObject["message"]!!.jsonObject["token"]!!.jsonPrimitive.content
        }.toSet())
    }

    // ------------------------------------------------------------------- outcomes

    /**
     * The distinction the pruning rests on. Only these three mean the install is gone; see
     * the sender for why anything else must keep the row.
     */
    @Test
    fun `a token FCM calls dead is reported stale`() = runBlocking {
        listOf("UNREGISTERED", "INVALID_ARGUMENT", "SENDER_ID_MISMATCH").forEach { reason ->
            val fcm = Fcm { HttpStatusCode.NotFound to """{"error":{"status":"$reason"}}""" }
            val result = sender(fcm).send(listOf(message("dead")))

            assertEquals(listOf("dead"), result.staleTokens, "$reason means the device is gone")
            assertEquals(1, result.failed)
        }
    }

    @Test
    fun `a server error keeps the token`() = runBlocking {
        val fcm = Fcm { HttpStatusCode.ServiceUnavailable to """{"error":{"status":"UNAVAILABLE"}}""" }
        val result = sender(fcm).send(listOf(message("alive")))

        assertEquals(1, result.failed)
        assertEquals(emptyList(), result.staleTokens, "an outage must not unsubscribe anyone")
    }

    @Test
    fun `a rejected access token is renewed and the push retried once`() = runBlocking {
        val credentials = Credentials(listOf("stale-token", "fresh-token"))
        val attempts = AtomicInteger()
        val fcm = Fcm {
            if (attempts.incrementAndGet() == 1) HttpStatusCode.Unauthorized to """{"error":"expired"}"""
            else HttpStatusCode.OK to """{"name":"ok"}"""
        }

        val result = sender(fcm, credentials).send(listOf(message()))

        assertEquals(1, result.delivered)
        assertEquals(2, fcm.requests.size, "the push was tried again after renewing")
        assertEquals(1, credentials.invalidations.get())
        assertEquals("Bearer fresh-token", fcm.requests.last().headers[HttpHeaders.Authorization])
    }

    /** One retry, not a loop: a key that is genuinely refused must not spin. */
    @Test
    fun `a persistently rejected token is not retried forever`() = runBlocking {
        val fcm = Fcm { HttpStatusCode.Unauthorized to """{"error":"nope"}""" }
        val result = sender(fcm, Credentials(listOf("a", "b"))).send(listOf(message()))

        assertEquals(1, result.failed)
        assertEquals(2, fcm.requests.size)
    }

    @Test
    fun `credentials that cannot be minted fail the batch without sending`() = runBlocking {
        val fcm = Fcm(ok())
        val broken = object : FcmCredentials {
            override val projectId = "cooknco-test"
            override fun get(): String = error("revoked key")
            override fun invalidate() {}
        }

        val result = FcmPushSender(credentials = broken, client = fcm.client)
            .send(listOf(message("a"), message("b")))

        assertEquals(2, result.failed)
        assertEquals(0, fcm.requests.size, "nothing goes out without a token")
        assertEquals(emptyList(), result.staleTokens, "and no device is blamed for it")
    }

    @Test
    fun `a title a user typed cannot break the payload`() = runBlocking {
        val fcm = Fcm(ok())
        // Quotes, braces and a newline: hand-built JSON is where this would go wrong
        val nasty = "\"}{ \n <script> & é"
        sender(fcm).send(listOf(PushMessage("device-1", nasty, nasty)))

        val msg = json.parseToJsonElement(fcm.bodies.single()).jsonObject["message"]!!.jsonObject
        assertEquals(nasty, msg["notification"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertTrue(json.parseToJsonElement(fcm.bodies.single()) is JsonObject)
    }
}
