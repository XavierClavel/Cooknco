package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.AuthApi
import com.xavierclavel.cooknco.network.dto.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore,
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

    suspend fun logout(): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        if (token != null) {
            authApi.logout(token)
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
