package com.xavierclavel.plugins

import shared.enums.UserRole
import shared.infodto.UserInfo
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent

@Serializable
data class SessionData(
    val userId: Long,
    val role: UserRole,
) {
    companion object {
        fun from(user: UserInfo) = SessionData(user.id, user.role)
    }
}

/**
 * An authorization request a user has approved, waiting to be exchanged for tokens.
 *
 * [codeChallenge] is the client's PKCE challenge and [redirectUri] the one it asked for: both
 * are checked again at the token endpoint, because a code is worthless to whoever intercepts it
 * without the verifier that produced the challenge.
 */
@Serializable
data class AuthorizationCodeData(
    val clientId: String,
    val userId: Long,
    val redirectUri: String,
    val codeChallenge: String,
    val resource: String,
    val scope: String,
)

/** An access or refresh token, and what it is allowed to speak for. */
@Serializable
data class OAuthTokenData(
    val clientId: String,
    val userId: Long,
    /** Canonical URI of the resource this token was issued for; checked on every MCP request. */
    val resource: String,
    val scope: String,
)

/**
 * An authorization request that has been validated but not yet decided on — what the consent
 * page is showing, and what its Allow button posts back a reference to.
 *
 * Held server-side and keyed by an unguessable id so that the decision cannot be forged from
 * another page: the id is unknown to anyone but the browser that was shown the form, and the
 * [userId] it was issued to must still match the session that posts it.
 */
@Serializable
data class PendingAuthorizationData(
    val clientId: String,
    val userId: Long,
    val redirectUri: String,
    val codeChallenge: String,
    val resource: String,
    val scope: String,
    val state: String?,
)

class RedisService(redisUrl: String): KoinComponent {
    companion object {
        /** Idle timeout: a session survives this long without activity. */
        const val SESSION_TTL = 30L * 24 * 60 * 60

        /** Slide the TTL only once this much of it has been consumed, to avoid a Redis write per request. */
        const val REFRESH_THRESHOLD = 24L * 60 * 60

        /**
         * How long an authorization code is worth anything. Seconds, deliberately: it is
         * redeemed by the client the moment its callback fires, and OAuth 2.1 asks for a
         * lifetime short enough that an intercepted code has expired before it can be used.
         */
        const val AUTHORIZATION_CODE_TTL = 60L

        /** How long the consent page's decision can sit unanswered. */
        const val PENDING_AUTHORIZATION_TTL = 10L * 60

        /**
         * Access tokens are short-lived on purpose: a leaked one stops working within the hour,
         * and the client refreshes without the user seeing anything.
         */
        const val ACCESS_TOKEN_TTL = 60L * 60

        /**
         * The refresh token is what makes the setup a one-time thing, so it lasts as long as a
         * web session would — and, like one, only while it is being used: each refresh issues a
         * new token with a full TTL and destroys the old one.
         */
        const val REFRESH_TOKEN_TTL = 30L * 24 * 60 * 60
    }

    private val client = RedisClient.create(redisUrl)
    private val connection = client.connect()

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val redis: RedisCoroutinesCommands<String, String> = connection.coroutines()

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun createSession(sessionId: String, user: UserInfo) {
        val json = SessionData.from(user)
        redis.setex("session:$sessionId", SESSION_TTL, Json.encodeToString(json))
        // Reverse index, so moderation can revoke every session an account holds
        redis.sadd(userSessionsKey(user.id), sessionId)
        redis.expire(userSessionsKey(user.id), SESSION_TTL)
    }

    /**
     * Slides the session expiry back to [SESSION_TTL] so that an active user is never logged out.
     * Returns true when the TTL was actually extended, so the caller knows to re-emit the cookie.
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun touchSession(sessionId: String): Boolean {
        val key = "session:$sessionId"
        val ttl = redis.ttl(key) ?: return false
        if (ttl <= 0 || ttl > SESSION_TTL - REFRESH_THRESHOLD) return false
        redis.expire(key, SESSION_TTL)
        getSession(sessionId)?.let { redis.expire(userSessionsKey(it.userId), SESSION_TTL) }
        return true
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun deleteSession(sessionId: String) {
        val userId = getSession(sessionId)?.userId
        redis.del("session:$sessionId")
        userId?.let { redis.srem(userSessionsKey(it), sessionId) }
    }

    /**
     * Logs an account out everywhere. Called when a moderator suspends, bans or deletes an
     * account, so the decision takes effect on the next request rather than when the
     * session would have expired.
     *
     * @return how many sessions were revoked
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun deleteAllSessionsOfUser(userId: Long): Int {
        val key = userSessionsKey(userId)
        val sessionIds = redis.smembers(key).toList()
        sessionIds.forEach { redis.del("session:$it") }
        redis.del(key)
        return sessionIds.size
    }

    private fun userSessionsKey(userId: Long) = "user-sessions:$userId"

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun hasSession(sessionId: String): Boolean =
        getSessionUserId(sessionId) != null

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getSessionUserId(sessionId: String): Long? =
        getSession(sessionId)?.userId

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getSession(sessionId: String): SessionData? {
        val json = redis.get("session:${sessionId}") ?: return null
        return Json.decodeFromString<SessionData>(json)
    }

    suspend fun isUserAdmin(sessionId: String): Boolean =
        getSession(sessionId)?.role == UserRole.ADMIN


    suspend fun getAdminSession(sessionId: String): SessionData? =
        getSession(sessionId)?.takeIf { it.role == UserRole.ADMIN }

    // ------------------------------------------------------------------ OAuth 2.1

    /**
     * Codes and tokens live here rather than in Postgres, and are opaque strings rather than
     * signed JWTs. Both follow from what Redis already gives the sessions next to them:
     * expiry the store enforces, and revocation that is a delete. A JWT would need a signing
     * key in the cluster's config and could not be taken back before it expired.
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun createPendingAuthorization(id: String, data: PendingAuthorizationData) {
        redis.setex("oauth-pending:$id", PENDING_AUTHORIZATION_TTL, Json.encodeToString(data))
    }

    /** Reads the pending request and forgets it, so an Allow cannot be replayed. */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun takePendingAuthorization(id: String): PendingAuthorizationData? {
        val key = "oauth-pending:$id"
        val json = redis.get(key) ?: return null
        redis.del(key)
        return Json.decodeFromString<PendingAuthorizationData>(json)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun createAuthorizationCode(code: String, data: AuthorizationCodeData) {
        redis.setex("oauth-code:$code", AUTHORIZATION_CODE_TTL, Json.encodeToString(data))
    }

    /**
     * Reads a code and destroys it in the same breath, whether or not the caller goes on to
     * accept it: a code is single-use, and a second presentation of one must fail even when the
     * first attempt failed its PKCE check.
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun takeAuthorizationCode(code: String): AuthorizationCodeData? {
        val key = "oauth-code:$code"
        val json = redis.get(key) ?: return null
        redis.del(key)
        return Json.decodeFromString<AuthorizationCodeData>(json)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun createAccessToken(token: String, data: OAuthTokenData) {
        redis.setex("oauth-access:$token", ACCESS_TOKEN_TTL, Json.encodeToString(data))
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getAccessToken(token: String): OAuthTokenData? {
        val json = redis.get("oauth-access:$token") ?: return null
        return Json.decodeFromString<OAuthTokenData>(json)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun createRefreshToken(token: String, data: OAuthTokenData) {
        redis.setex("oauth-refresh:$token", REFRESH_TOKEN_TTL, Json.encodeToString(data))
    }

    /**
     * Reads a refresh token and destroys it, which is the rotation OAuth 2.1 requires of a
     * public client: the token that comes back is new, and presenting the old one again fails.
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun takeRefreshToken(token: String): OAuthTokenData? {
        val key = "oauth-refresh:$token"
        val json = redis.get(key) ?: return null
        redis.del(key)
        return Json.decodeFromString<OAuthTokenData>(json)
    }

}
