package com.xavierclavel.controllers

import com.xavierclavel.mcp.CookncoMcpServer
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.services.OAuthService
import com.xavierclavel.utils.Controller
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import org.koin.java.KoinJavaComponent.inject
import shared.utils.URL.MCP_URL

/**
 * Cook&co as a Model Context Protocol server, over
 * [Streamable HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http).
 *
 * A client is pointed at this URL and told nothing else:
 *
 * ```
 * claude mcp add --transport http cooknco https://cooknco.eu/mcp
 * ```
 *
 * Its first request arrives without a token and is answered with a 401 naming this endpoint's
 * own metadata document. From there the client finds the authorization server, registers itself
 * and opens a browser for the user to approve it (`OAuthController`); afterwards it holds an
 * access token it refreshes on its own, and the tools in [CookncoMcpServer] act as the account
 * that approved it.
 *
 * **Two kinds of bearer are accepted, and no cookie.** An OAuth access token issued for this
 * endpoint is how a client gets in. A session token — what `POST /api/v1/auth/login` returns —
 * is accepted too: it is this server's own session rather than a token minted elsewhere, and it
 * is what keeps the endpoint reachable from a script or a test with no browser in the loop. The
 * session *cookie* is deliberately refused, because CORS here is `anyHost()` with credentials,
 * so a cookie-authenticated `/mcp` would let any page a logged-in user visits drive the write
 * tools from their browser.
 *
 * **Stateless.** One `Server` per POST, no session id, no SSE stream: the tools answer from the
 * database and stream nothing back, so there is no reason to keep a transport alive between
 * calls — or to hold per-session state in a pod that may not serve the next request.
 */
object McpController : Controller(MCP_URL) {
    private val oauthService: OAuthService by inject(OAuthService::class.java)
    private val redisService: RedisService by inject(RedisService::class.java)

    override fun Route.routes() {
        // MCP needs its own JSON settings (`explicitNulls = false`, `encodeDefaults = true`,
        // discriminator off) or JSON-RPC replies serialize wrong — with an explicit `"error": null`
        // beside a result, which strict clients reject. Installed on this route rather than on the
        // application, whose `json()` every other endpoint's responses are shaped by.
        install(ContentNegotiation) { json(McpJson) }

        handleMcpRequest()

        // A client probing with GET or DELETE is told the endpoint is POST-only, rather than
        // being told its token is missing.
        rejectUnsupportedMethods()
    }

    private fun Route.handleMcpRequest() = post {
        // Resolved here rather than by an authentication provider because the 401 has to carry a
        // WWW-Authenticate header pointing at the resource metadata (RFC 9728 §5.1): that header
        // is how a client discovers where to authenticate, and it is what lets the endpoint be
        // added by URL alone. Ktor's bearer provider sends a challenge of its own making.
        val userId = authenticatedUserId() ?: return@post respondUnauthorized()

        val transport = StreamableHttpServerTransport(
            StreamableHttpServerTransport.Configuration(
                // The reply comes back as JSON on this very POST instead of over an SSE stream.
                enableJsonResponse = true,
                // Host and Origin checks guard *local* servers against DNS rebinding. This one is
                // remote, behind nginx, and authenticated by a token no browser can attach on a
                // cross-origin request, so the defaults — which only ever allow localhost — would
                // reject every real caller.
                enableDnsRebindingProtection = false,
            ),
        ).also {
            // No session id: this transport serves exactly this request. See the class comment.
            it.setSessionIdGenerator(null)
        }

        val session = CookncoMcpServer.forUser(userId).createSession(transport)
        try {
            transport.handleRequest(null, call)
        } finally {
            // Closing it here is what keeps the server's session registry from growing by one
            // entry per POST for the lifetime of the pod.
            session.close()
        }
    }

    /**
     * The account behind the request's bearer token, or null when there is nothing valid.
     *
     * An OAuth token is tried first and accepted only if it was issued for *this* resource —
     * the audience check the spec requires of a resource server, without which a token minted
     * for another service could be replayed here.
     */
    private suspend fun RoutingContext.authenticatedUserId(): Long? {
        val header = call.request.header(HttpHeaders.Authorization) ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        val token = header.substring("Bearer ".length).trim().takeIf { it.isNotEmpty() } ?: return null

        oauthService.tokenFor(token)?.let { return it.userId }

        return redisService.getSessionUserId(token)?.also {
            // Keeps a session used only by an MCP client from idling out, as bearer-auth does.
            redisService.touchSession(token)
        }
    }

    /**
     * The 401 that starts the OAuth flow.
     *
     * `resource_metadata` is the point of it: a client given this answer knows where to read
     * what it needs in order to authenticate. Sent for a missing, unknown, expired or
     * wrong-audience token alike — all of them are "authenticate again", and saying which
     * would tell a caller probing for tokens more than it tells a legitimate client.
     */
    private suspend fun RoutingContext.respondUnauthorized() {
        call.response.header(
            HttpHeaders.WWWAuthenticate,
            """Bearer resource_metadata="${oauthService.protectedResourceMetadataUrl}"""",
        )
        call.respondText(
            """{"error":"invalid_token","error_description":"Authorization required. Authenticate """ +
                """with the authorization server named in the WWW-Authenticate header."}""",
            ContentType.Application.Json,
            HttpStatusCode.Unauthorized,
        )
    }

    private fun Route.rejectUnsupportedMethods() {
        get { call.rejectNonPost() }
        delete { call.rejectNonPost() }
    }

    private suspend fun ApplicationCall.rejectNonPost() {
        response.header(HttpHeaders.Allow, HttpMethod.Post.value)
        respondText(
            """{"error":"This MCP endpoint is stateless and serves POST only."}""",
            ContentType.Application.Json,
            HttpStatusCode.MethodNotAllowed,
        )
    }
}
