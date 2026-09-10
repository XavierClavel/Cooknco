package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import shared.utils.URL.OAUTH_URL
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Driving the OAuth flow the way a client drives it: real requests, real redirects, real PKCE.
 *
 * The consent step is a form in a page, so these read the request id out of the HTML rather
 * than out of a test-only hook — what the tests exercise is what a browser would post back.
 *
 * Call these on [TestBuilderWrapper.noRedirectClient]: every step of the flow answers with a
 * redirect that the test has to read rather than follow.
 */

private val json = Json { ignoreUnknownKeys = true }

/** The verifier a client keeps and the challenge it publishes: S256, base64url, unpadded. */
data class Pkce(val verifier: String, val challenge: String) {
    companion object {
        fun generate(verifier: String = "verifier-" + "x".repeat(60)): Pkce {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Pkce(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
        }
    }
}

suspend fun HttpClient.registerClientRaw(body: String): HttpResponse =
    post("/$OAUTH_URL/register") {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

/** Registers a client and returns its `client_id`. */
suspend fun HttpClient.registerClient(
    redirectUri: String = "http://localhost:33418/callback",
    clientName: String = "Test client",
): String {
    val response = registerClientRaw("""{"redirect_uris":["$redirectUri"],"client_name":"$clientName"}""")
    assertEquals(HttpStatusCode.Created, response.status, "registration failed: ${response.bodyAsText()}")
    return json.parseToJsonElement(response.bodyAsText()).jsonObject["client_id"]!!.jsonPrimitive.content
}

/**
 * Opens the authorization endpoint as a browser would. Redirects are not followed, so the
 * caller can assert on where it was sent — which for half of these tests is the point.
 */
suspend fun HttpClient.authorize(
    clientId: String?,
    redirectUri: String?,
    codeChallenge: String? = Pkce.generate().challenge,
    codeChallengeMethod: String? = "S256",
    state: String? = "the-client-state",
    resource: String? = null,
    responseType: String? = "code",
    scope: String? = null,
): HttpResponse = get("/$OAUTH_URL/authorize") {
    url {
        clientId?.let { parameters.append("client_id", it) }
        redirectUri?.let { parameters.append("redirect_uri", it) }
        responseType?.let { parameters.append("response_type", it) }
        codeChallenge?.let { parameters.append("code_challenge", it) }
        codeChallengeMethod?.let { parameters.append("code_challenge_method", it) }
        state?.let { parameters.append("state", it) }
        resource?.let { parameters.append("resource", it) }
        scope?.let { parameters.append("scope", it) }
    }
}

/** The hidden field the consent form posts back, read out of the rendered page. */
fun String.consentRequestId(): String =
    Regex("""name="request_id" value="([^"]+)"""").find(this)?.groupValues?.get(1)
        ?: fail("no request_id in the consent page: ${this.take(400)}")

suspend fun HttpClient.decide(requestId: String, allow: Boolean): HttpResponse =
    submitForm(
        url = "/$OAUTH_URL/decision",
        formParameters = parameters {
            append("request_id", requestId)
            append("decision", if (allow) "allow" else "deny")
        },
    )

suspend fun HttpClient.token(
    grantType: String = "authorization_code",
    code: String? = null,
    clientId: String? = null,
    redirectUri: String? = null,
    codeVerifier: String? = null,
    refreshToken: String? = null,
    resource: String? = null,
): HttpResponse = submitForm(
    url = "/$OAUTH_URL/token",
    formParameters = parameters {
        append("grant_type", grantType)
        code?.let { append("code", it) }
        clientId?.let { append("client_id", it) }
        redirectUri?.let { append("redirect_uri", it) }
        codeVerifier?.let { append("code_verifier", it) }
        refreshToken?.let { append("refresh_token", it) }
        resource?.let { append("resource", it) }
    },
)

/** A query parameter of the URL a response redirected to. */
fun HttpResponse.redirectParameter(name: String): String? =
    headers[HttpHeaders.Location]?.let { location ->
        // Relative Locations are legal and the login redirect uses one, so give Url a base.
        Url(if (location.startsWith("http")) location else "http://localhost$location")
            .parameters[name]
    }

fun HttpResponse.location(): String =
    headers[HttpHeaders.Location] ?: fail("expected a redirect, got $status")

suspend fun HttpResponse.asJson(): JsonObject =
    json.parseToJsonElement(bodyAsText()).jsonObject

/**
 * Walks the whole flow and returns the access token at the end of it, which is what a client
 * ends up holding and what the MCP endpoint has to accept.
 */
suspend fun HttpClient.completeOAuthFlow(
    redirectUri: String = "http://localhost:33418/callback",
    resource: String? = null,
): String {
    val clientId = registerClient(redirectUri)
    val pkce = Pkce.generate()

    val consent = authorize(clientId, redirectUri, codeChallenge = pkce.challenge, resource = resource)
    assertEquals(HttpStatusCode.OK, consent.status, "consent page: ${consent.bodyAsText()}")

    val granted = decide(consent.bodyAsText().consentRequestId(), allow = true)
    val code = granted.redirectParameter("code") ?: fail("no code in ${granted.location()}")

    val tokens = token(
        code = code,
        clientId = clientId,
        redirectUri = redirectUri,
        codeVerifier = pkce.verifier,
        resource = resource,
    )
    assertEquals(HttpStatusCode.OK, tokens.status, "token exchange: ${tokens.bodyAsText()}")
    return tokens.asJson()["access_token"]!!.jsonPrimitive.content
}

/** Sends one MCP request with an OAuth access token, as an authorized client would. */
suspend fun HttpClient.mcpPostWithAccessToken(accessToken: String, body: JsonObject): HttpResponse =
    post(shared.utils.URL.MCP_URL) {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
        header(HttpHeaders.Accept, "application/json, text/event-stream")
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }
