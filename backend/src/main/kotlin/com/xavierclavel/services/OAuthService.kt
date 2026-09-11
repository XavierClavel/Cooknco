package com.xavierclavel.services

import com.xavierclavel.models.OAuthClient
import com.xavierclavel.models.query.QOAuthClient
import com.xavierclavel.plugins.AnsweredDecisionData
import com.xavierclavel.plugins.AuthorizationCodeData
import com.xavierclavel.plugins.OAuthTokenData
import com.xavierclavel.plugins.PendingAuthorizationData
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.DbTransaction.insertAndGet
import com.xavierclavel.utils.logger
import io.ktor.http.Url
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.dto.ClientRegistrationRequest
import shared.utils.URL.MCP_URL
import shared.utils.URL.OAUTH_AUTHORIZATION_SERVER_METADATA
import shared.utils.URL.OAUTH_PROTECTED_RESOURCE_METADATA
import shared.utils.URL.OAUTH_URL
import shared.utils.URL.WELL_KNOWN_URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64

/**
 * The OAuth 2.1 authorization server behind `/mcp`.
 *
 * It exists so that connecting a client is one command and a browser login instead of a token
 * pasted into a config file. The client discovers this server from the MCP endpoint's own
 * metadata, registers itself, sends the user here to approve it, and exchanges the resulting
 * code for tokens it then refreshes on its own.
 *
 * Three rules run through everything below, each of them a requirement of the MCP
 * authorization spec rather than a preference:
 *
 * - **PKCE, S256 only.** A public client has no secret, so the only thing tying a code to the
 *   process that asked for it is the verifier behind [PendingAuthorizationData.codeChallenge].
 *   `plain` is not accepted; OAuth 2.1 removed it.
 * - **Exact redirect URIs.** Matched in full against what the client registered, never by
 *   prefix. An unregistered URI is refused *at* the authorization endpoint rather than
 *   redirected to, since redirecting is the attack.
 * - **Audience binding.** Every token records the resource it was issued for, and the MCP
 *   endpoint accepts only tokens issued for itself ([tokenFor]). Without that check a token
 *   minted for some other service could be replayed here.
 */
class OAuthService : KoinComponent {
    private val redisService: RedisService by inject()
    private val configuration: Configuration by inject()

    /** Tokens, codes and client ids all come from here. 256 bits, url-safe, no padding. */
    private val random = SecureRandom()

    companion object {
        /** The only scope there is: everything the tools can reach, as the signed-in user. */
        const val SCOPE = "mcp"

        private const val TOKEN_BYTES = 32
    }

    /**
     * The public origin, which is what every URL a client is handed has to be built from.
     *
     * `frontend.url` rather than `backend.url`: nginx serves the app and the API on one origin,
     * and it is `backend.url` that carries the `/api/v1` suffix (see `Configuration`), which
     * these paths must not inherit. The issuer must equal the origin the client fetched the
     * metadata from, or the client rejects the document.
     */
    private val origin: String get() = configuration.frontend.url.trimEnd('/')

    /**
     * The canonical URI of the MCP endpoint: the `resource` a client names in its requests and
     * the audience its tokens are bound to. No trailing slash, per the spec's guidance.
     */
    val resourceUri: String get() = "$origin/$MCP_URL"

    val issuer: String get() = origin
    val authorizationEndpoint: String get() = "$origin/$OAUTH_URL/authorize"
    val tokenEndpoint: String get() = "$origin/$OAUTH_URL/token"
    val registrationEndpoint: String get() = "$origin/$OAUTH_URL/register"
    val authorizationServerMetadataUrl: String get() = "$origin/$WELL_KNOWN_URL/$OAUTH_AUTHORIZATION_SERVER_METADATA"

    /**
     * Where a 401 from the MCP endpoint points, and the path RFC 9728 defines for a resource
     * with a path of its own: the resource's path is appended to the well-known prefix, so
     * `/mcp` is described at `/.well-known/oauth-protected-resource/mcp`.
     */
    val protectedResourceMetadataUrl: String get() = "$origin/$WELL_KNOWN_URL/$OAUTH_PROTECTED_RESOURCE_METADATA/$MCP_URL"

    // ------------------------------------------------------------------- clients

    /**
     * Registers a client, or hands back the one that already matches.
     *
     * Registration is open, as it has to be for a client to connect knowing only the MCP URL.
     * That is safe because a registration grants nothing: no data is reachable until a user
     * approves this client on the consent page. Returning the existing row for an identical
     * request keeps a client that re-registers on every launch from filling the table.
     */
    fun register(request: ClientRegistrationRequest): OAuthClient {
        val redirectUris = request.redirectUris.map { it.trim() }.filter { it.isNotEmpty() }
        if (redirectUris.isEmpty()) {
            throw InvalidClientMetadata("redirect_uris is required and must hold at least one URI")
        }
        redirectUris.forEach { validateRedirectUri(it) }

        val clientName = request.clientName?.take(255)?.ifBlank { null } ?: "Unnamed client"

        existingClient(clientName, redirectUris)?.let { return it }

        val client = OAuthClient(
            clientId = newToken(),
            clientName = clientName,
            redirectUris = redirectUris,
        ).insertAndGet()
        logger.info { "Registered MCP client ${client.clientId} ($clientName) for ${redirectUris.joinToString()}" }
        return client
    }

    private fun existingClient(clientName: String, redirectUris: List<String>): OAuthClient? =
        QOAuthClient()
            .clientName.eq(clientName)
            .findList()
            .firstOrNull { it.redirectUris.toSet() == redirectUris.toSet() }

    fun findClient(clientId: String): OAuthClient? =
        QOAuthClient().clientId.eq(clientId).findOne()

    /**
     * A redirect URI this server is willing to send a code to.
     *
     * Loopback or HTTPS, as OAuth 2.1 requires: everything else is either eavesdroppable or
     * not a browser destination at all. A fragment is refused because the fragment is where
     * the client's own state goes, and no credentials may be put in the URI.
     */
    private fun validateRedirectUri(uri: String) {
        val parsed = runCatching { Url(uri) }.getOrNull()
            ?: throw InvalidClientMetadata("redirect_uri is not a valid URI: $uri")
        if (parsed.fragment.isNotEmpty()) {
            throw InvalidClientMetadata("redirect_uri must not contain a fragment: $uri")
        }
        if (parsed.user != null || parsed.password != null) {
            throw InvalidClientMetadata("redirect_uri must not contain credentials: $uri")
        }
        val isLoopback = parsed.host == "localhost" || parsed.host == "127.0.0.1" || parsed.host == "[::1]"
        val isHttps = parsed.protocol.name == "https"
        // A native client registers a private-use scheme (cooknco://…) or a loopback port; a
        // web client must be on https. Plain http anywhere else would put the code on the wire.
        val isPrivateUseScheme = parsed.protocol.name !in setOf("http", "https") && parsed.protocol.name.contains('.')
        if (!isHttps && !isLoopback && !isPrivateUseScheme) {
            throw InvalidClientMetadata(
                "redirect_uri must use https, a loopback address, or a reverse-domain private-use scheme: $uri"
            )
        }
    }

    // ------------------------------------------------------- the authorization step

    /**
     * Checks an authorization request and holds it until the user decides.
     *
     * Everything a client got wrong that cannot be safely reported *through* the redirect —
     * an unknown client, an unregistered redirect URI — throws [InvalidAuthorizationRequest]
     * and is shown to the user instead. Anything else is the client's error to handle and is
     * reported by redirecting, which is why those are checked after the URI is trusted.
     */
    suspend fun beginAuthorization(
        clientId: String?,
        redirectUri: String?,
        responseType: String?,
        codeChallenge: String?,
        codeChallengeMethod: String?,
        state: String?,
        resource: String?,
        scope: String?,
        userId: Long,
    ): PendingAuthorization {
        if (clientId.isNullOrBlank()) throw InvalidAuthorizationRequest("client_id is required")
        val client = findClient(clientId)
            ?: throw InvalidAuthorizationRequest("Unknown client_id. The client must register first.")

        // Exact match, and refused here rather than redirected to: sending an error to an
        // unregistered URI would be the open redirect the check exists to prevent.
        if (redirectUri.isNullOrBlank()) throw InvalidAuthorizationRequest("redirect_uri is required")
        if (redirectUri !in client.redirectUris) {
            throw InvalidAuthorizationRequest("redirect_uri is not registered for this client: $redirectUri")
        }

        // From here the URI is trusted, so a client's own mistakes go back to it as an error.
        if (responseType != "code") {
            throw RedirectableAuthorizationError(redirectUri, state, "unsupported_response_type", "response_type must be code")
        }
        if (codeChallenge.isNullOrBlank()) {
            throw RedirectableAuthorizationError(redirectUri, state, "invalid_request", "code_challenge is required (PKCE)")
        }
        if (codeChallengeMethod != "S256") {
            throw RedirectableAuthorizationError(redirectUri, state, "invalid_request", "code_challenge_method must be S256")
        }
        if (scope != null && scope.split(" ").any { it.isNotBlank() && it != SCOPE }) {
            throw RedirectableAuthorizationError(redirectUri, state, "invalid_scope", "The only scope is '$SCOPE'")
        }
        // A client naming a resource other than this endpoint is asking the wrong server for a
        // token; issuing one anyway is what audience binding exists to stop.
        if (resource != null && !isOwnResource(resource)) {
            throw RedirectableAuthorizationError(
                redirectUri, state, "invalid_target", "This server issues tokens for $resourceUri only",
            )
        }

        val id = newToken()
        redisService.createPendingAuthorization(
            id,
            PendingAuthorizationData(
                clientId = client.clientId,
                userId = userId,
                redirectUri = redirectUri,
                codeChallenge = codeChallenge,
                // Recorded as *our* canonical URI, not as the client sent it: the token's
                // audience must be what the MCP endpoint compares against.
                resource = resourceUri,
                scope = SCOPE,
                state = state,
            ),
        )
        return PendingAuthorization(id = id, client = client, redirectUri = redirectUri, state = state)
    }

    /**
     * Turns an approved request into a code.
     *
     * [sessionUserId] is the account that just clicked Allow, and it must be the one the form
     * was issued to: a stale page left open in another account's browser must not be able to
     * grant access to this one.
     */
    suspend fun approve(pendingId: String, sessionUserId: Long): ApprovedAuthorization? {
        val pending = redisService.takePendingAuthorization(pendingId) ?: return null
        if (pending.userId != sessionUserId) {
            logger.warn { "Consent for client ${pending.clientId} was posted by a different account; refused" }
            return null
        }
        redisService.rememberDecision(pendingId, AnsweredDecisionData(pending.userId, approved = true))
        val code = newToken()
        redisService.createAuthorizationCode(
            code,
            AuthorizationCodeData(
                clientId = pending.clientId,
                userId = pending.userId,
                redirectUri = pending.redirectUri,
                codeChallenge = pending.codeChallenge,
                resource = pending.resource,
                scope = pending.scope,
            ),
        )
        return ApprovedAuthorization(code = code, redirectUri = pending.redirectUri, state = pending.state)
    }

    /** Consumes a pending request that was declined, to know where the refusal is reported. */
    suspend fun takeDenied(pendingId: String, sessionUserId: Long): PendingAuthorizationData? {
        val pending = redisService.takePendingAuthorization(pendingId) ?: return null
        if (pending.userId != sessionUserId) return null
        redisService.rememberDecision(pendingId, AnsweredDecisionData(pending.userId, approved = false))
        return pending
    }

    /**
     * What this account already answered a consent form with, when the form is no longer pending.
     *
     * A form that is gone was either answered or left too long, and only this tells the two
     * apart — which matters to the person holding the page, because a client that opens a
     * second authorization for the same connection leaves the first window behind, and pressing
     * Allow on it must not read as "nothing was granted".
     */
    suspend fun answerTo(pendingId: String, sessionUserId: Long): Answer? =
        redisService.getDecision(pendingId)
            ?.takeIf { it.userId == sessionUserId }
            ?.let { if (it.approved) Answer.APPROVED else Answer.DECLINED }

    // ------------------------------------------------------------ the token step

    /**
     * Exchanges a code for tokens.
     *
     * The code is consumed on read, so a replay fails even if this call goes on to reject it.
     * The client id and redirect URI must be the ones the code was issued to, and the verifier
     * must hash to the challenge that came with the authorization request.
     */
    suspend fun redeemCode(
        code: String?,
        clientId: String?,
        redirectUri: String?,
        codeVerifier: String?,
        resource: String?,
    ): Tokens {
        if (code.isNullOrBlank()) throw InvalidTokenRequest("invalid_request", "code is required")
        if (clientId.isNullOrBlank()) throw InvalidTokenRequest("invalid_request", "client_id is required")
        if (codeVerifier.isNullOrBlank()) throw InvalidTokenRequest("invalid_request", "code_verifier is required")

        val data = redisService.takeAuthorizationCode(code)
            ?: throw InvalidTokenRequest("invalid_grant", "The code is unknown, already used, or expired")

        if (data.clientId != clientId) {
            throw InvalidTokenRequest("invalid_grant", "The code was issued to another client")
        }
        if (redirectUri != null && redirectUri != data.redirectUri) {
            throw InvalidTokenRequest("invalid_grant", "redirect_uri does not match the authorization request")
        }
        if (!verifyPkce(codeVerifier, data.codeChallenge)) {
            throw InvalidTokenRequest("invalid_grant", "code_verifier does not match the code_challenge")
        }
        if (resource != null && !isOwnResource(resource)) {
            throw InvalidTokenRequest("invalid_target", "This server issues tokens for $resourceUri only")
        }

        touch(clientId)
        return issueTokens(OAuthTokenData(clientId = clientId, userId = data.userId, resource = data.resource, scope = data.scope))
    }

    /**
     * Rotates a refresh token, which is what keeps a client connected without the user
     * returning to the browser.
     *
     * The presented token is destroyed whether or not this succeeds, so a stolen token is worth
     * one use at most, and a client that used it legitimately is the only one holding the new one.
     */
    suspend fun refresh(refreshToken: String?, clientId: String?, resource: String?): Tokens {
        if (refreshToken.isNullOrBlank()) throw InvalidTokenRequest("invalid_request", "refresh_token is required")

        val data = redisService.takeRefreshToken(refreshToken)
            ?: throw InvalidTokenRequest("invalid_grant", "The refresh token is unknown, already used, or expired")

        if (clientId != null && data.clientId != clientId) {
            throw InvalidTokenRequest("invalid_grant", "The refresh token was issued to another client")
        }
        if (resource != null && !isOwnResource(resource)) {
            throw InvalidTokenRequest("invalid_target", "This server issues tokens for $resourceUri only")
        }

        touch(data.clientId)
        return issueTokens(data)
    }

    private suspend fun issueTokens(data: OAuthTokenData): Tokens {
        val accessToken = newToken()
        val refreshToken = newToken()
        redisService.createAccessToken(accessToken, data)
        redisService.createRefreshToken(refreshToken, data)
        return Tokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = RedisService.ACCESS_TOKEN_TTL,
            scope = data.scope,
        )
    }

    /**
     * The token behind an MCP request, if it is one this endpoint may accept.
     *
     * This is the audience check the spec requires of a resource server: a token issued for
     * anything but [resourceUri] is refused here even though this same server minted it.
     */
    suspend fun tokenFor(accessToken: String): OAuthTokenData? =
        redisService.getAccessToken(accessToken)?.takeIf { it.resource == resourceUri }

    // ------------------------------------------------------------------- helpers

    /**
     * Whether a `resource` names this MCP endpoint.
     *
     * A trailing slash and the case of the scheme and host are all forms the spec asks servers
     * to accept, so they are normalised away rather than refused.
     */
    fun isOwnResource(resource: String): Boolean {
        val normalized = resource.trim().trimEnd('/')
        val expected = resourceUri
        if (normalized.equals(expected, ignoreCase = true)) return true
        // Only scheme and host are case-insensitive; the path is not, so compare them apart.
        val given = runCatching { Url(normalized) }.getOrNull() ?: return false
        val own = Url(expected)
        return given.protocol.name.equals(own.protocol.name, ignoreCase = true) &&
            given.host.equals(own.host, ignoreCase = true) &&
            given.port == own.port &&
            given.encodedPath.trimEnd('/') == own.encodedPath.trimEnd('/')
    }

    /** S256: the verifier's SHA-256, base64url without padding, must equal the challenge. */
    private fun verifyPkce(codeVerifier: String, codeChallenge: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        // Constant time: this comparison decides whether a code is redeemable.
        return MessageDigest.isEqual(computed.toByteArray(Charsets.US_ASCII), codeChallenge.toByteArray(Charsets.US_ASCII))
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun touch(clientId: String) {
        findClient(clientId)?.apply {
            lastUsedAt = LocalDateTime.now()
            update()
        }
    }

    /** What a consent form that is no longer pending was answered with. */
    enum class Answer { APPROVED, DECLINED }

    data class PendingAuthorization(
        val id: String,
        val client: OAuthClient,
        val redirectUri: String,
        val state: String?,
    )

    data class ApprovedAuthorization(val code: String, val redirectUri: String, val state: String?)

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val scope: String,
    )
}

/** A registration this server will not accept (RFC 7591's `invalid_client_metadata`). */
class InvalidClientMetadata(message: String) : Exception(message)

/**
 * An authorization request broken in a way that must be shown to the user rather than sent to
 * the client: there is no URI that can be trusted with it.
 */
class InvalidAuthorizationRequest(message: String) : Exception(message)

/** An authorization request whose error belongs back at the client's own redirect URI. */
class RedirectableAuthorizationError(
    val redirectUri: String,
    val state: String?,
    val error: String,
    val description: String,
) : Exception(description)

/** An error the token endpoint reports as an OAuth error object. */
class InvalidTokenRequest(val error: String, val description: String) : Exception(description)
