package com.xavierclavel.plugins

import shared.enums.UserRole
import shared.infodto.UserInfo
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
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

class RedisService(redisUrl: String): KoinComponent {
    companion object {
        /** Idle timeout: a session survives this long without activity. */
        const val SESSION_TTL = 30L * 24 * 60 * 60

        /** Slide the TTL only once this much of it has been consumed, to avoid a Redis write per request. */
        const val REFRESH_THRESHOLD = 24L * 60 * 60
    }

    private val client = RedisClient.create(redisUrl)
    private val connection = client.connect()

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val redis: RedisCoroutinesCommands<String, String> = connection.coroutines()

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun createSession(sessionId: String, user: UserInfo) {
        val json = SessionData.from(user)
        redis.setex("session:$sessionId", SESSION_TTL, Json.encodeToString(json))
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
        return true
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun deleteSession(sessionId: String) {
        redis.del("session:$sessionId")
    }

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

}
