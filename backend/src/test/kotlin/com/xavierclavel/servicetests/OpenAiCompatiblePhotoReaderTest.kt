package main.com.xavierclavel.servicetests

import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.services.OpenAiCompatiblePhotoReader
import com.xavierclavel.services.RecipePhoto
import com.xavierclavel.services.RecipePhotoReader
import com.xavierclavel.services.UnconfiguredPhotoReader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one class in the recipe scan that speaks to a provider, against a mock engine.
 *
 * The request is asserted field by field because it is the whole of the provider contract:
 * every OpenAI-compatible service reads the same body, so a body this gets wrong is wrong for
 * all of them at once — and the controller tests, which swap the reader out, cannot see it.
 */
class OpenAiCompatiblePhotoReaderTest {

    private class Provider(private val reply: () -> Pair<HttpStatusCode, String>) {
        val requests = mutableListOf<HttpRequestData>()
        val bodies = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            requests += request
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val (status, body) = reply()
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        })
    }

    private fun completion(content: String) = """
        {"id": "x", "choices": [{"index": 0, "message": {"role": "assistant", "content": ${Json.encodeToString(content)}}}],
         "usage": {"prompt_tokens": 1234, "completion_tokens": 321}}
    """.trimIndent()

    private fun reader(provider: Provider, jsonMode: Boolean = true) = OpenAiCompatiblePhotoReader(
        baseUrl = "https://provider.example/v1/",
        apiKey = "secret-key",
        model = "some-vision-model",
        maxOutputTokens = 2048,
        jsonMode = jsonMode,
        client = provider.client,
    )

    private val page = RecipePhoto(byteArrayOf(1, 2, 3), "image/jpeg")

    @Test
    fun `the request is a chat completion carrying the prompt and every page`() = runBlocking {
        val provider = Provider { HttpStatusCode.OK to completion("{}") }
        reader(provider).read(listOf(page, RecipePhoto(byteArrayOf(4, 5), "image/png")))

        val request = provider.requests.single()
        // The trailing slash on the base URL does not double up.
        assertEquals("https://provider.example/v1/chat/completions", request.url.toString())
        assertEquals("Bearer secret-key", request.headers[HttpHeaders.Authorization])

        val body = provider.bodies.single()
        assertEquals("some-vision-model", body["model"]!!.jsonPrimitive.content)
        assertEquals("2048", body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("0", body["temperature"]!!.jsonPrimitive.content)
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val (system, user) = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals("system", system["role"]!!.jsonPrimitive.content)
        assertEquals(RecipePhotoReader.PROMPT, system["content"]!!.jsonPrimitive.content)

        val images = user["content"]!!.jsonArray.map { it.jsonObject }
            .filter { it["type"]!!.jsonPrimitive.content == "image_url" }
            .map { it["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content }
        assertEquals(
            listOf(
                "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))}",
                "data:image/png;base64,${Base64.getEncoder().encodeToString(byteArrayOf(4, 5))}",
            ),
            images,
        )
    }

    @Test
    fun `json mode can be left out for a provider that rejects it`() = runBlocking {
        val provider = Provider { HttpStatusCode.OK to completion("{}") }
        reader(provider, jsonMode = false).read(listOf(page))
        assertNull(provider.bodies.single()["response_format"])
    }

    @Test
    fun `the answer is the message content, with the usage for the cost log`() = runBlocking {
        val provider = Provider { HttpStatusCode.OK to completion("""{"title": "Toast"}""") }
        val reading = reader(provider).read(listOf(page))
        assertEquals("""{"title": "Toast"}""", reading.json)
        assertEquals(1234, reading.inputTokens)
        assertEquals(321, reading.outputTokens)
    }

    @Test
    fun `a provider rate limit is busy, and anything else it refuses is a failure`() = runBlocking {
        assertCause(ServiceUnavailableCause.RECIPE_READER_BUSY, Provider { HttpStatusCode.TooManyRequests to "{}" })
        assertCause(ServiceUnavailableCause.RECIPE_READER_FAILED, Provider { HttpStatusCode.Unauthorized to """{"error": "bad key"}""" })
        assertCause(ServiceUnavailableCause.RECIPE_READER_FAILED, Provider { HttpStatusCode.InternalServerError to "oops" })
    }

    @Test
    fun `an answer with no content is a failure`() = runBlocking {
        assertCause(ServiceUnavailableCause.RECIPE_READER_FAILED, Provider { HttpStatusCode.OK to """{"choices": []}""" })
        assertCause(ServiceUnavailableCause.RECIPE_READER_FAILED, Provider { HttpStatusCode.OK to completion("") })
        assertCause(ServiceUnavailableCause.RECIPE_READER_FAILED, Provider { HttpStatusCode.OK to "not json" })
    }

    @Test
    fun `a provider that cannot be reached is unavailable`() = runBlocking {
        val unreachable = OpenAiCompatiblePhotoReader(
            baseUrl = "https://provider.example/v1",
            apiKey = "k",
            model = "m",
            client = HttpClient(MockEngine { throw java.io.IOException("connection refused") }),
        )
        val error = assertThrows<ServiceUnavailableException> { runBlocking { unreachable.read(listOf(page)) } }
        assertEquals(ServiceUnavailableCause.RECIPE_READER_UNAVAILABLE.key, error.message)
    }

    @Test
    fun `an install with no provider says so`() {
        val error = assertThrows<ServiceUnavailableException> { runBlocking { UnconfiguredPhotoReader().read(listOf(page)) } }
        assertEquals(ServiceUnavailableCause.RECIPE_READER_NOT_CONFIGURED.key, error.message)
    }

    /** The key is a credential: it must not appear in the prompt or anywhere but the header. */
    @Test
    fun `the api key travels only in the authorization header`() = runBlocking {
        val provider = Provider { HttpStatusCode.OK to completion("{}") }
        reader(provider).read(listOf(page))
        assertTrue("secret-key" !in provider.bodies.single().toString())
    }

    private fun assertCause(cause: ServiceUnavailableCause, provider: Provider) {
        val error = assertThrows<ServiceUnavailableException> { runBlocking { reader(provider).read(listOf(page)) } }
        assertEquals(cause.key, error.message)
    }
}
