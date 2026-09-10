package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import shared.dto.SessionDto
import shared.utils.URL.AUTH_URL
import shared.utils.URL.MCP_URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Driving `/mcp` the way a client does: raw JSON-RPC over HTTP.
 *
 * Deliberately not built on the MCP SDK's own client. These helpers assert on the bytes on the
 * wire, so the tests cover what a client would actually receive — including the JSON settings
 * the endpoint installs, which an SDK client on both ends would hide.
 */

/** Streamable HTTP requires a POST to accept both; the endpoint answers 406 without it. */
private const val MCP_ACCEPT = "application/json, text/event-stream"

private val json = Json { ignoreUnknownKeys = true }

/**
 * Logs in over HTTP Basic and returns the session token, which is what an MCP client is given.
 *
 * The password is passed in rather than defaulted, like [login]: the fixture's own
 * `ApplicationTest.password` is the one place it is written down.
 */
suspend fun HttpClient.mcpToken(username: String, password: String): String {
    post("$AUTH_URL/login") { basicAuth(username = username, password = password) }.apply {
        assertEquals(HttpStatusCode.OK, status, "login failed: ${bodyAsText()}")
        return json.decodeFromString<SessionDto>(bodyAsText()).token
    }
}

fun jsonRpc(method: String, params: JsonObject? = null, id: Int? = 1): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    id?.let { put("id", it) }
    put("method", method)
    params?.let { put("params", it) }
}

suspend fun HttpClient.mcpPostRaw(
    token: String?,
    body: JsonObject,
    /** The version a client negotiated at initialize and sends on every later call. */
    protocolVersion: String? = null,
): HttpResponse =
    post(MCP_URL) {
        token?.let { bearerAuth(it) }
        header(HttpHeaders.Accept, MCP_ACCEPT)
        protocolVersion?.let { header("MCP-Protocol-Version", it) }
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }

/** Sends one JSON-RPC request and returns its `result`, failing on a transport-level error. */
suspend fun HttpClient.mcpResult(token: String, method: String, params: JsonObject? = null): JsonObject {
    mcpPostRaw(token, jsonRpc(method, params)).apply {
        val body = bodyAsText()
        assertEquals(HttpStatusCode.OK, status, "$method answered $status: $body")
        val envelope = json.parseToJsonElement(body).jsonObject
        assertEquals("2.0", envelope["jsonrpc"]?.jsonPrimitive?.content)
        assertNull(envelope["error"], "$method returned a JSON-RPC error: $body")
        return envelope["result"]?.jsonObject ?: fail("$method returned no result: $body")
    }
}

/** What a tool answered: the text blocks it returned, and whether it reported failure. */
data class McpToolOutcome(val isError: Boolean, val text: String) {
    fun asJson(): JsonObject = json.parseToJsonElement(text).jsonObject
}

suspend fun HttpClient.callTool(
    token: String,
    name: String,
    arguments: JsonObject = JsonObject(emptyMap()),
): McpToolOutcome {
    val result = mcpResult(
        token,
        "tools/call",
        buildJsonObject {
            put("name", name)
            put("arguments", arguments)
        },
    )
    val text = result["content"]?.jsonArray
        ?.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
        ?: fail("tool $name returned no content: $result")
    return McpToolOutcome(isError = result["isError"]?.jsonPrimitive?.boolean ?: false, text = text)
}

/** Calls a tool that is expected to succeed, and parses its JSON payload. */
suspend fun HttpClient.callToolOk(
    token: String,
    name: String,
    arguments: JsonObject = JsonObject(emptyMap()),
): JsonObject {
    val outcome = callTool(token, name, arguments)
    assertFalse(outcome.isError, "tool $name failed: ${outcome.text}")
    return outcome.asJson()
}
