package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.dto.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore,
    private val pushRepository: PushRepository,
) {
    val tokenFlow: Flow<String?> = tokenDataStore.tokenFlow

    suspend fun login(email: String, password: String): Result<UserInfo> = runCatching {
        val session = authApi.login(email, password)
        tokenDataStore.saveToken(session.token)
        authApi.whoami(session.token)
    }

    suspend fun signup(username: String, email: String, password: String): Result<Unit> = runCatching {
        authApi.signup(username, email, password)
        Unit
    }

    /**
     * Ends the session on this device, and on the server if it can be reached.
     *
     * The server call is best effort on purpose: its session expires on its own, but the
     * token on disk does not. Letting a failed request skip [TokenDataStore.clearToken]
     * would leave the user on the login screen and signed straight back in at the next
     * launch — a sign-out that only looked like one.
     */
    suspend fun logout(): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        if (token != null) {
            // Before the session goes: it is an authenticated call, and an account left
            // registered would keep pushing to a handset somebody else may now be holding.
            pushRepository.unregisterCurrentDevice()
            runCatching { authApi.logout(token) }
        }
        tokenDataStore.clearToken()
    }

    suspend fun getCurrentUser(): UserInfo? {
        val token = tokenDataStore.tokenFlow.first() ?: return null
        return runCatching { authApi.whoami(token) }
            .onFailure { tokenDataStore.clearToken() }
            .getOrNull()
    }

    suspend fun saveOAuthToken(token: String): Result<UserInfo> = runCatching {
        tokenDataStore.saveToken(token)
        authApi.whoami(token)
    }
}
