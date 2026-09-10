package com.xavierclavel.controllers

import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.mcp.CookncoMcpServer
import com.xavierclavel.utils.Controller
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.modelcontextprotocol.kotlin.sdk.server.StreamableHttpServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import shared.utils.URL.MCP_URL

/**
 * Cook&co as a Model Context Protocol server, over
 * [Streamable HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http).
 *
 * An MCP client is registered against this endpoint with a session token, and gets the tools in
 * [CookncoMcpServer] — the same reads and writes the app makes, acting as that token's account:
 *
 * ```
 * claude mcp add --transport http cooknco https://cooknco.eu/mcp \
 *   --header "Authorization: Bearer $(curl -su mail:password https://cooknco.eu/api/v1/auth/login | jq -r .token)"
 * ```
 *
 * **Bearer only, no cookie.** Every other authenticated route accepts `auth-session` too, and
 * that would be wrong here: the app's CORS is `anyHost()` with `allowCredentials`, so a
 * cookie-authenticated `/mcp` would let any page a logged-in user visits drive the write tools
 * from their browser. An MCP client sends a token header and never a cookie, so taking the
 * cookie away costs nothing and closes that door.
 *
 * **Stateless.** One `Server` per POST, no session id, no SSE stream: the tools here answer
 * from the database and stream nothing back, so there is no reason to keep a transport alive
 * between calls — or to hold per-session state in a pod that may not serve the next request.
 * The client re-initialises per call, which the spec allows and which costs one extra
 * round trip on a protocol whose calls are already request/response.
 */
object McpController : Controller(MCP_URL) {

    override fun Route.routes() {
        // MCP needs its own JSON settings (`explicitNulls = false`, `encodeDefaults = true`,
        // discriminator off) or JSON-RPC replies serialize wrong — with an explicit `"error": null`
        // beside a result, which strict clients reject. Installed on this route rather than on the
        // application, whose `json()` every other endpoint's responses are shaped by.
        install(ContentNegotiation) { json(McpJson) }

        authenticate("bearer-auth") {
            handleMcpRequest()
        }

        // Outside the auth block: a client probing with GET or DELETE is told the endpoint is
        // POST-only, rather than being told its token is missing.
        rejectUnsupportedMethods()
    }

    private fun Route.handleMcpRequest() = post {
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

        val session = CookncoMcpServer.forUser(getMcpUserId()).createSession(transport)
        try {
            transport.handleRequest(null, call)
        } finally {
            // Closing it here is what keeps the server's session registry from growing by one
            // entry per POST for the lifetime of the pod.
            session.close()
        }
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

    /**
     * The account the calling token belongs to.
     *
     * `bearer-auth` puts the user id in the principal's name (see `configureAuthentication`),
     * unlike the session providers, which carry a session id.
     */
    private fun RoutingContext.getMcpUserId(): Long =
        call.principal<UserIdPrincipal>()?.name?.toLongOrNull()
            ?: throw UnauthorizedException(UnauthorizedCause.SESSION_NOT_FOUND)
}
