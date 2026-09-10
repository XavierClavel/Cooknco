package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.TestBuilderWrapper
import io.ktor.client.HttpClient
import com.xavierclavel.plugins.OAuthTokenData
import com.xavierclavel.plugins.RedisService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import main.com.xavierclavel.utils.Pkce
import main.com.xavierclavel.utils.asJson
import main.com.xavierclavel.utils.authorize
import main.com.xavierclavel.utils.completeOAuthFlow
import main.com.xavierclavel.utils.consentRequestId
import main.com.xavierclavel.utils.decide
import main.com.xavierclavel.utils.location
import main.com.xavierclavel.utils.login
import main.com.xavierclavel.utils.mcpPostRaw
import main.com.xavierclavel.utils.mcpPostWithAccessToken
import main.com.xavierclavel.utils.redirectParameter
import main.com.xavierclavel.utils.registerClient
import main.com.xavierclavel.utils.registerClientRaw
import main.com.xavierclavel.utils.token
import org.junit.jupiter.api.Test
import org.koin.test.inject
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The OAuth 2.1 flow that connects an MCP client, driven end to end.
 *
 * The point of the feature is that a client needs nothing but the MCP URL, so the tests start
 * where a client starts — an unauthenticated call — and follow the discovery, registration,
 * consent and exchange steps to a token that works. The rest of them are the refusals: the
 * checks that are the only thing standing between "any client may register" and "any client
 * may read your recipes".
 */
class OAuthControllerTest : ApplicationTest() {
    private val redisService: RedisService by inject()

    /**
     * The browser in these tests: it keeps its cookies and does not chase redirects, which is
     * what lets a test read the Location each step answers with. Logged in here rather than by
     * `runTestAsUser`, because that logs in with the *other* client.
     */
    private suspend fun TestBuilderWrapper.signedIn(): HttpClient =
        noRedirectClient.also { it.login(USER1, password) }

    /** What `application-test.yaml` sets as the public origin. */
    private val origin = "http://localhost:3000"
    private val resourceUri = "$origin/mcp"
    private val loopbackRedirect = "http://localhost:33418/callback"

    // ---------------------------------------------------------------- discovery

    @Test
    fun `a call with no token points the client at the resource metadata`() = runTest {
        val response = noRedirectClient.mcpPostRaw(null, buildJsonObject { put("jsonrpc", "2.0") })
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val challenge = response.headers[HttpHeaders.WWWAuthenticate]
            ?: error("no WWW-Authenticate header on the 401")
        // This is the whole discovery chain: without it a client cannot find the login.
        assertContains(challenge, "Bearer")
        assertContains(challenge, """resource_metadata="$origin/.well-known/oauth-protected-resource/mcp"""")
    }

    @Test
    fun `the protected resource metadata names this endpoint and its authorization server`() = runTest {
        val metadata = noRedirectClient.get("/.well-known/oauth-protected-resource/mcp").let {
            assertEquals(HttpStatusCode.OK, it.status)
            it.asJson()
        }
        assertEquals(resourceUri, metadata["resource"]!!.jsonPrimitive.content)
        assertEquals(
            listOf(origin),
            metadata["authorization_servers"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        // Optional fields must be absent rather than null, or a client reads "no scopes at all".
        assertFalse(metadata.containsKey("resource_documentation"))
    }

    @Test
    fun `the metadata is served at the bare path too, and needs no token`() = runTest {
        assertEquals(HttpStatusCode.OK, noRedirectClient.get("/.well-known/oauth-protected-resource").status)
        assertEquals(HttpStatusCode.OK, noRedirectClient.get("/.well-known/oauth-authorization-server").status)
        assertEquals(HttpStatusCode.OK, noRedirectClient.get("/.well-known/oauth-authorization-server/mcp").status)
    }

    @Test
    fun `the authorization server metadata advertises PKCE, registration and the resource parameter`() = runTest {
        val metadata = noRedirectClient.get("/.well-known/oauth-authorization-server").asJson()
        assertEquals(origin, metadata["issuer"]!!.jsonPrimitive.content)
        assertEquals("$origin/oauth/authorize", metadata["authorization_endpoint"]!!.jsonPrimitive.content)
        assertEquals("$origin/oauth/token", metadata["token_endpoint"]!!.jsonPrimitive.content)
        assertEquals("$origin/oauth/register", metadata["registration_endpoint"]!!.jsonPrimitive.content)
        // S256 only: OAuth 2.1 removed `plain`, and a client offered it might use it.
        assertEquals(
            listOf("S256"),
            metadata["code_challenge_methods_supported"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("none"),
            metadata["token_endpoint_auth_methods_supported"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(metadata["resource_indicators_supported"]!!.jsonPrimitive.content.toBoolean())
    }

    // ------------------------------------------------------------- registration

    @Test
    fun `a client registers itself without anyone's permission`() = runTest {
        val response = noRedirectClient.registerClientRaw(
            """{"redirect_uris":["$loopbackRedirect"],"client_name":"Claude Code"}""",
        )
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.asJson()
        assertTrue(body["client_id"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("Claude Code", body["client_name"]!!.jsonPrimitive.content)
        assertEquals("none", body["token_endpoint_auth_method"]!!.jsonPrimitive.content)
        // A public client gets no secret; PKCE is what protects its code.
        assertFalse(body.containsKey("client_secret"))
    }

    @Test
    fun `a registration with no redirect uri is refused`() = runTest {
        val response = noRedirectClient.registerClientRaw("""{"client_name":"No way back"}""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_client_metadata", response.asJson()["error"]!!.jsonPrimitive.content)
    }

    /** Loopback or https only: a code sent over plain http to a host is a code on the wire. */
    @Test
    fun `a redirect uri that is neither loopback nor https is refused`() = runTest {
        val response = noRedirectClient.registerClientRaw("""{"redirect_uris":["http://evil.example.com/cb"]}""")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.asJson()["error_description"]!!.jsonPrimitive.content, "https")
    }

    @Test
    fun `a registration repeated with the same details does not pile up clients`() = runTest {
        val first = noRedirectClient.registerClient(loopbackRedirect, "Claude Code")
        val second = noRedirectClient.registerClient(loopbackRedirect, "Claude Code")
        assertEquals(first, second)
    }

    // --------------------------------------------------------------- authorizing

    @Test
    fun `an unauthenticated browser is sent to the login page with a way back`() = runTest {
        val clientId = noRedirectClient.registerClient()
        val response = noRedirectClient.authorize(clientId, loopbackRedirect)
        assertEquals(HttpStatusCode.Found, response.status)
        val location = response.location()
        assertContains(location, "$origin/login")
        // The way back has to carry the whole request, or consent resumes with nothing.
        val redirect = response.redirectParameter("redirect")!!
        assertContains(redirect, "/oauth/authorize")
        assertContains(redirect, clientId)
    }

    @Test
    fun `a signed-in user is asked, by name, whether the client may act for them`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect, "Claude Code")
        val response = http.authorize(clientId, loopbackRedirect)
        assertEquals(HttpStatusCode.OK, response.status)
        val page = response.bodyAsText()
        assertContains(page, "Claude Code")
        // Where the code would actually go is shown next to the name the client chose.
        assertContains(page, loopbackRedirect)
        assertContains(page, "request_id")
    }

    /** A client name is attacker-controlled text on a page, so it must not be able to be markup. */
    @Test
    fun `a client name is escaped into the consent page`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect, "<script>alert(1)</script>")
        val page = http.authorize(clientId, loopbackRedirect).bodyAsText()
        assertFalse(page.contains("<script>alert(1)</script>"), "the client name was not escaped")
        assertContains(page, "&lt;script&gt;")
    }

    @Test
    fun `an unknown client is refused on the page rather than redirected`() = runTest {
        val http = signedIn()
        val response = http.authorize("not-a-client", loopbackRedirect)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(response.headers[HttpHeaders.Location])
        assertContains(response.bodyAsText(), "cannot be made")
    }

    /**
     * The check the whole flow rests on: an unregistered URI must not be redirected to, not
     * even to report the error, because redirecting to it is the attack.
     */
    @Test
    fun `an unregistered redirect uri is refused without a redirect`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val response = http.authorize(clientId, "http://localhost:9999/steal")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNull(response.headers[HttpHeaders.Location])
    }

    @Test
    fun `a request without PKCE is reported back to the client`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val response = http.authorize(clientId, loopbackRedirect, codeChallenge = null)
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("invalid_request", response.redirectParameter("error"))
        // The client's own state comes back with the error, so it can match it to its request.
        assertEquals("the-client-state", response.redirectParameter("state"))
    }

    @Test
    fun `a plain code challenge is refused`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val response = http.authorize(clientId, loopbackRedirect, codeChallengeMethod = "plain")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("invalid_request", response.redirectParameter("error"))
    }

    @Test
    fun `a token asked for another server's resource is refused`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val response = http.authorize(clientId, loopbackRedirect, resource = "https://elsewhere.example/mcp")
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("invalid_target", response.redirectParameter("error"))
    }

    // ----------------------------------------------------------- the decision

    @Test
    fun `allowing sends a code to the registered uri, with the client's state`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val consent = http.authorize(clientId, loopbackRedirect)
        val granted = http.decide(consent.bodyAsText().consentRequestId(), allow = true)

        assertEquals(HttpStatusCode.Found, granted.status)
        assertContains(granted.location(), loopbackRedirect)
        assertTrue(granted.redirectParameter("code")!!.isNotBlank())
        assertEquals("the-client-state", granted.redirectParameter("state"))
    }

    @Test
    fun `cancelling tells the client the user declined`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val consent = http.authorize(clientId, loopbackRedirect)
        val denied = http.decide(consent.bodyAsText().consentRequestId(), allow = false)

        assertEquals(HttpStatusCode.Found, denied.status)
        assertEquals("access_denied", denied.redirectParameter("error"))
        assertNull(denied.redirectParameter("code"))
    }

    @Test
    fun `a decision cannot be submitted twice`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val requestId = http.authorize(clientId, loopbackRedirect).bodyAsText().consentRequestId()

        assertEquals(HttpStatusCode.Found, http.decide(requestId, allow = true).status)
        assertEquals(HttpStatusCode.BadRequest, http.decide(requestId, allow = true).status)
    }

    /** A consent form is bound to the account it was rendered for, not merely to a session. */
    @Test
    fun `a consent form cannot be approved by another account`() = runTest {
        val user1 = signedIn()
        val clientId = user1.registerClient(loopbackRedirect)
        val requestId = user1.authorize(clientId, loopbackRedirect).bodyAsText().consentRequestId()

        // A different account, in its own browser, holding an id it should not be able to spend.
        val user2 = newNoRedirectClient().also { it.login(USER2, password) }
        assertEquals(HttpStatusCode.BadRequest, user2.decide(requestId, allow = true).status)
    }

    @Test
    fun `a decision without a session is refused`() = runTest {
        assertEquals(HttpStatusCode.Unauthorized, noRedirectClient.decide("anything", allow = true).status)
    }

    // ------------------------------------------------------------- the exchange

    @Test
    fun `a code exchanges for an access token and a refresh token`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val pkce = Pkce.generate()
        val consent = http.authorize(clientId, loopbackRedirect, codeChallenge = pkce.challenge)
        val code = http.decide(consent.bodyAsText().consentRequestId(), allow = true)
            .redirectParameter("code")!!

        val response = http.token(
            code = code,
            clientId = clientId,
            redirectUri = loopbackRedirect,
            codeVerifier = pkce.verifier,
            resource = resourceUri,
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers["Cache-Control"])
        val body = response.asJson()
        assertTrue(body["access_token"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(body["refresh_token"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("Bearer", body["token_type"]!!.jsonPrimitive.content)
        assertEquals(RedisService.ACCESS_TOKEN_TTL, body["expires_in"]!!.jsonPrimitive.content.toLong())
    }

    /** Without this check, intercepting the code would be enough to get a token. */
    @Test
    fun `a code presented with the wrong verifier is refused`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val consent = http.authorize(clientId, loopbackRedirect, codeChallenge = Pkce.generate().challenge)
        val code = http.decide(consent.bodyAsText().consentRequestId(), allow = true)
            .redirectParameter("code")!!

        val response = http.token(
            code = code,
            clientId = clientId,
            redirectUri = loopbackRedirect,
            codeVerifier = "not-the-verifier-that-made-that-challenge",
        )
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_grant", response.asJson()["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a code works once`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val pkce = Pkce.generate()
        val consent = http.authorize(clientId, loopbackRedirect, codeChallenge = pkce.challenge)
        val code = http.decide(consent.bodyAsText().consentRequestId(), allow = true)
            .redirectParameter("code")!!

        assertEquals(
            HttpStatusCode.OK,
            http.token(code = code, clientId = clientId, codeVerifier = pkce.verifier).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            http.token(code = code, clientId = clientId, codeVerifier = pkce.verifier).status,
        )
    }

    @Test
    fun `a code cannot be redeemed by a different client`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect, "First")
        val otherClientId = noRedirectClient.registerClient("http://127.0.0.1:44444/cb", "Second")
        val pkce = Pkce.generate()
        val consent = http.authorize(clientId, loopbackRedirect, codeChallenge = pkce.challenge)
        val code = http.decide(consent.bodyAsText().consentRequestId(), allow = true)
            .redirectParameter("code")!!

        val response = http.token(code = code, clientId = otherClientId, codeVerifier = pkce.verifier)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_grant", response.asJson()["error"]!!.jsonPrimitive.content)
    }

    /** Rotation is what keeps a stolen refresh token worth one use at most. */
    @Test
    fun `refreshing rotates the token and retires the old one`() = runTest {
        val http = signedIn()
        val clientId = noRedirectClient.registerClient(loopbackRedirect)
        val pkce = Pkce.generate()
        val consent = http.authorize(clientId, loopbackRedirect, codeChallenge = pkce.challenge)
        val code = http.decide(consent.bodyAsText().consentRequestId(), allow = true)
            .redirectParameter("code")!!
        val first = http.token(code = code, clientId = clientId, codeVerifier = pkce.verifier).asJson()
        val firstRefresh = first["refresh_token"]!!.jsonPrimitive.content

        val refreshed = http.token(grantType = "refresh_token", refreshToken = firstRefresh, clientId = clientId)
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val body = refreshed.asJson()
        assertNotEquals(firstRefresh, body["refresh_token"]!!.jsonPrimitive.content)
        assertNotEquals(
            first["access_token"]!!.jsonPrimitive.content,
            body["access_token"]!!.jsonPrimitive.content,
        )

        val replayed = http.token(grantType = "refresh_token", refreshToken = firstRefresh, clientId = clientId)
        assertEquals(HttpStatusCode.BadRequest, replayed.status)
        assertEquals("invalid_grant", replayed.asJson()["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a grant type the server does not implement is refused`() = runTest {
        val http = signedIn()
        val response = http.token(grantType = "password")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("unsupported_grant_type", response.asJson()["error"]!!.jsonPrimitive.content)
    }

    // ------------------------------------------------------------- end to end

    @Test
    fun `a token from the flow works on the MCP endpoint, as the account that approved it`() = runTest {
        val http = signedIn()
        val accessToken = http.completeOAuthFlow(resource = resourceUri)

        val response = http.mcpPostWithAccessToken(
            accessToken,
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                putJsonObject("params") {
                    put("name", "get_user")
                    putJsonObject("arguments") {}
                }
            },
        )
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        // get_user with no argument answers for whoever the token belongs to, so this says the
        // tools ran as the account that clicked Allow rather than as nobody in particular.
        assertContains(response.bodyAsText(), "\\\"isAuthenticatedUser\\\":true")
    }

    /**
     * The audience check. A token this very server issued, but for something else, must not
     * open the MCP endpoint — which is the requirement that makes a stolen token useless
     * anywhere but where it was meant to go. Planted directly, because the authorization
     * endpoint refuses to issue one for a foreign resource in the first place.
     */
    @Test
    fun `an access token issued for another resource is refused`() = runTest {
        val http = signedIn()
        val foreignToken = "planted-token-for-another-resource"
        redisService.createAccessToken(
            foreignToken,
            OAuthTokenData(
                clientId = "whoever",
                userId = 1,
                resource = "https://elsewhere.example/mcp",
                scope = "mcp",
            ),
        )

        val response = http.mcpPostWithAccessToken(
            foreignToken,
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/list")
            },
        )
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertContains(response.headers[HttpHeaders.WWWAuthenticate]!!, "resource_metadata")
    }
}
