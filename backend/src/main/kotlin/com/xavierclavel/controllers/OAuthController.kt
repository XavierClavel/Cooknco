package com.xavierclavel.controllers

import com.xavierclavel.plugins.RedisService
import com.xavierclavel.services.InvalidAuthorizationRequest
import com.xavierclavel.services.InvalidClientMetadata
import com.xavierclavel.services.InvalidTokenRequest
import com.xavierclavel.services.OAuthService
import com.xavierclavel.services.RedirectableAuthorizationError
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.OAuthPages
import com.xavierclavel.utils.UserSession
import com.xavierclavel.utils.logger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter
import io.ktor.server.request.queryString
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import shared.dto.AuthorizationServerMetadata
import shared.dto.ClientRegistrationRequest
import shared.dto.ClientRegistrationResponse
import shared.dto.OAuthError
import shared.dto.ProtectedResourceMetadata
import shared.dto.TokenResponse
import shared.utils.URL.MCP_URL
import shared.utils.URL.OAUTH_AUTHORIZATION_SERVER_METADATA
import shared.utils.URL.OAUTH_PROTECTED_RESOURCE_METADATA
import shared.utils.URL.OAUTH_URL
import shared.utils.URL.WELL_KNOWN_URL
import java.time.ZoneOffset

/**
 * Optional fields are omitted rather than sent as null.
 *
 * The application's own `json()` keeps explicit nulls, which is right for the API's DTOs and
 * wrong here: a metadata document advertising `"scopes_supported": null` describes a server
 * that supports no scopes. These responses are therefore encoded here and written as text,
 * which also keeps them clear of the route-scoped serialization `/mcp` installs.
 */
private val oauthJson = Json {
    explicitNulls = false
    encodeDefaults = true
}

private suspend inline fun <reified T> RoutingContext.respondOAuth(status: HttpStatusCode, body: T) =
    call.respondText(oauthJson.encodeToString(body), ContentType.Application.Json, status)

/**
 * A registration is read with this rather than through `call.receive`, and the difference is
 * the whole point: unknown members are ignored.
 *
 * RFC 7591 has a server ignore the metadata it does not understand, and every real client
 * sends some — `software_id`, `software_version`, `logo_uri`, `application_type`. The
 * application-wide `json()` refuses an unknown key, so the parse threw and every one of them
 * was answered `invalid_client_metadata` before a single field had been looked at. Reading the
 * body as text also drops the requirement that the client label it `application/json`, which
 * content negotiation would otherwise turn into the same error.
 *
 * A field this server *does* know keeps its meaning: a redirect URI that is not a URI is still
 * a refusal, from [OAuthService.register] and with a description saying which one.
 */
private val registrationJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    // A null where a list is expected reads as the default, so it reaches the field-level
    // refusal ("redirect_uris is required") instead of the parser's blanket one.
    coerceInputValues = true
}

/**
 * The two discovery documents that let an MCP client find its way in knowing only `/mcp`.
 *
 * Both are unauthenticated, as they must be: a client reads them precisely because it has no
 * credentials yet. Neither says anything a caller could not learn by trying the endpoints.
 *
 * They live under `.well-known` at the root, where the specs put them and where
 * `frontend/nginx.conf` publishes them — RFC 9728 for the resource, RFC 8414 for the server.
 * Each is served at the path-suffixed form as well as the bare one, because a client is free to
 * probe either for a resource whose URL has a path.
 */
object OAuthMetadataController : Controller(WELL_KNOWN_URL) {
    private val oauthService: OAuthService by inject(OAuthService::class.java)

    override fun Route.routes() {
        protectedResourceMetadata()
        authorizationServerMetadata()
    }

    private fun Route.protectedResourceMetadata() {
        val respond: suspend RoutingContext.() -> Unit = {
            respondOAuth(
                HttpStatusCode.OK,
                ProtectedResourceMetadata(
                    resource = oauthService.resourceUri,
                    authorizationServers = listOf(oauthService.issuer),
                    scopesSupported = listOf(OAuthService.SCOPE),
                ),
            )
        }
        get("/$OAUTH_PROTECTED_RESOURCE_METADATA/$MCP_URL") { respond() }
        get("/$OAUTH_PROTECTED_RESOURCE_METADATA") { respond() }
    }

    private fun Route.authorizationServerMetadata() {
        val respond: suspend RoutingContext.() -> Unit = {
            respondOAuth(
                HttpStatusCode.OK,
                AuthorizationServerMetadata(
                    issuer = oauthService.issuer,
                    authorizationEndpoint = oauthService.authorizationEndpoint,
                    tokenEndpoint = oauthService.tokenEndpoint,
                    registrationEndpoint = oauthService.registrationEndpoint,
                    scopesSupported = listOf(OAuthService.SCOPE),
                ),
            )
        }
        get("/$OAUTH_AUTHORIZATION_SERVER_METADATA") { respond() }
        get("/$OAUTH_AUTHORIZATION_SERVER_METADATA/$MCP_URL") { respond() }
    }
}

/**
 * Registration, the consent screen, and the token endpoint.
 *
 * The flow a user sees is two steps: a browser opens [authorize], and either signs in first or
 * is asked straight away whether the client that sent them may act on their account. Everything
 * else — registration, the code exchange, refreshes — happens between the client and this
 * server with no one watching.
 *
 * None of these routes sit behind `authenticate`. `/oauth/register` and `/oauth/token` are
 * called by a client that has no session by definition, and the two browser routes need the
 * *cookie* session and a redirect to the login page when it is missing, which an
 * authentication provider's 401 could not give them.
 */
object OAuthController : Controller(OAUTH_URL) {
    private val oauthService: OAuthService by inject(OAuthService::class.java)
    private val userService: UserService by inject(UserService::class.java)
    private val redisService: RedisService by inject(RedisService::class.java)
    private val configuration: Configuration by inject(Configuration::class.java)

    override fun Route.routes() {
        register()
        authorize()
        decide()
        token()
    }

    /** RFC 7591. Open, and grants nothing on its own: see [OAuthService.register]. */
    private fun Route.register() = post("/register") {
        val request = try {
            registrationJson.decodeFromString<ClientRegistrationRequest>(call.receiveText())
        } catch (e: Exception) {
            logger.info { "Refused a client registration whose body did not parse: ${e.message}" }
            return@post respondOAuth(
                HttpStatusCode.BadRequest,
                OAuthError("invalid_client_metadata", "The registration request is not valid JSON"),
            )
        }

        try {
            val client = oauthService.register(request)
            respondOAuth(
                HttpStatusCode.Created,
                ClientRegistrationResponse(
                    clientId = client.clientId,
                    clientIdIssuedAt = client.registeredAt.toEpochSecond(ZoneOffset.UTC),
                    redirectUris = client.redirectUris,
                    clientName = client.clientName,
                    scope = OAuthService.SCOPE,
                ),
            )
        } catch (e: InvalidClientMetadata) {
            respondOAuth(HttpStatusCode.BadRequest, OAuthError("invalid_client_metadata", e.message))
        }
    }

    /**
     * Where the client sends the user's browser.
     *
     * Signing in is delegated to the app's own login page rather than duplicated here, so that
     * this flow keeps every way in the account already has — a password, or Google — and there
     * is only one page in the product that asks for a password. The user comes back to this
     * exact URL afterwards.
     */
    private fun Route.authorize() = get("/authorize") {
        val userId = sessionUserId()
        if (userId == null) {
            val returnTo = "/$OAUTH_URL/authorize?${call.request.queryString()}"
            return@get call.respondRedirect(
                "${configuration.frontend.url}/login?redirect=${returnTo.encodeURLParameter()}",
            )
        }

        val parameters = call.request.queryParameters
        try {
            val pending = oauthService.beginAuthorization(
                clientId = parameters["client_id"],
                redirectUri = parameters["redirect_uri"],
                responseType = parameters["response_type"],
                codeChallenge = parameters["code_challenge"],
                codeChallengeMethod = parameters["code_challenge_method"],
                state = parameters["state"],
                resource = parameters["resource"],
                scope = parameters["scope"],
                userId = userId,
            )
            call.respondText(
                OAuthPages.consent(
                    clientName = pending.client.clientName,
                    redirectUri = pending.redirectUri,
                    username = userService.getUser(userId).username,
                    requestId = pending.id,
                    decisionUrl = "/$OAUTH_URL/decision",
                ),
                ContentType.Text.Html,
            )
        } catch (e: InvalidAuthorizationRequest) {
            // Nowhere safe to send this, so the user is told instead of the client.
            logger.info { "Refused an MCP authorization request: ${e.message}" }
            call.respondText(
                OAuthPages.error(e.message ?: "This authorization request cannot be honoured."),
                ContentType.Text.Html,
                HttpStatusCode.BadRequest,
            )
        } catch (e: RedirectableAuthorizationError) {
            call.respondRedirect(errorRedirect(e))
        }
    }

    /**
     * Allow or Deny.
     *
     * The decision is identified by an id the consent page was handed, which is unguessable and
     * consumed on use, and it is only honoured for the account the page was rendered for. That
     * is what keeps another site from posting an approval on the user's behalf.
     */
    private fun Route.decide() = post("/decision") {
        val userId = sessionUserId()
            ?: return@post call.respondText(
                OAuthPages.error("Your session has expired. Start the connection again from your client."),
                ContentType.Text.Html,
                HttpStatusCode.Unauthorized,
            )

        val form = call.receiveParameters()
        val requestId = form["request_id"]
            ?: return@post call.respondText(
                OAuthPages.error("This form is incomplete."),
                ContentType.Text.Html,
                HttpStatusCode.BadRequest,
            )

        if (form["decision"] != "allow") {
            val denied = oauthService.takeDenied(requestId, userId)
                ?: return@post call.respondText(
                    OAuthPages.error("This request has expired. Start the connection again from your client."),
                    ContentType.Text.Html,
                    HttpStatusCode.BadRequest,
                )
            return@post call.respondRedirect(
                redirectWith(denied.redirectUri, denied.state) {
                    parameters.append("error", "access_denied")
                    parameters.append("error_description", "The user declined the request")
                },
            )
        }

        val approved = oauthService.approve(requestId, userId)
            ?: return@post call.respondText(
                OAuthPages.error("This request has expired. Start the connection again from your client."),
                ContentType.Text.Html,
                HttpStatusCode.BadRequest,
            )

        logger.info { "User $userId authorized an MCP client" }
        call.respondRedirect(
            redirectWith(approved.redirectUri, approved.state) {
                parameters.append("code", approved.code)
            },
        )
    }

    /**
     * The code and refresh exchanges.
     *
     * Form-encoded and unauthenticated, as OAuth 2.1 has it for a public client: the client id
     * is an identifier rather than a credential, and PKCE is what proves the caller is the one
     * that started the flow.
     */
    private fun Route.token() = post("/token") {
        val form = try {
            call.receiveParameters()
        } catch (e: Exception) {
            return@post respondOAuth(
                HttpStatusCode.BadRequest,
                OAuthError("invalid_request", "The token request must be form-encoded"),
            )
        }

        try {
            val tokens = when (val grantType = form["grant_type"]) {
                "authorization_code" -> oauthService.redeemCode(
                    code = form["code"],
                    clientId = form["client_id"],
                    redirectUri = form["redirect_uri"],
                    codeVerifier = form["code_verifier"],
                    resource = form["resource"],
                )
                "refresh_token" -> oauthService.refresh(
                    refreshToken = form["refresh_token"],
                    clientId = form["client_id"],
                    resource = form["resource"],
                )
                else -> throw InvalidTokenRequest(
                    "unsupported_grant_type",
                    "grant_type must be authorization_code or refresh_token, got ${grantType ?: "nothing"}",
                )
            }
            // No-store, because this response is the credential itself.
            call.response.headers.append("Cache-Control", "no-store")
            respondOAuth(
                HttpStatusCode.OK,
                TokenResponse(
                    accessToken = tokens.accessToken,
                    expiresIn = tokens.expiresIn,
                    refreshToken = tokens.refreshToken,
                    scope = tokens.scope,
                ),
            )
        } catch (e: InvalidTokenRequest) {
            val status = if (e.error == "unsupported_grant_type") HttpStatusCode.BadRequest else HttpStatusCode.BadRequest
            respondOAuth(status, OAuthError(e.error, e.description))
        }
    }

    /**
     * The cookie session's account, or null.
     *
     * Read directly rather than through an authentication provider because these two routes
     * answer a browser: a missing session is a redirect to the login page, not a 401.
     */
    private suspend fun RoutingContext.sessionUserId(): Long? =
        call.sessions.get<UserSession>()?.sessionId?.let { redisService.getSessionUserId(it) }

    /** Appends parameters to a client's registered redirect URI, keeping its own query intact. */
    private fun redirectWith(redirectUri: String, state: String?, block: URLBuilder.() -> Unit): String =
        URLBuilder(redirectUri).apply {
            block()
            state?.let { parameters.append("state", it) }
        }.buildString()

    private fun errorRedirect(e: RedirectableAuthorizationError): String =
        redirectWith(e.redirectUri, e.state) {
            parameters.append("error", e.error)
            parameters.append("error_description", e.description)
        }
}
