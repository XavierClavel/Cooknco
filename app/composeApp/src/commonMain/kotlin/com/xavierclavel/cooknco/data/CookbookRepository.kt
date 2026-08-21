package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.CookbookSaveDto
import com.xavierclavel.cooknco.network.dto.CookbookUserInfo
import com.xavierclavel.cooknco.network.dto.CookbookUserSaveDto
import com.xavierclavel.cooknco.network.dto.UserSearchResult
import kotlinx.coroutines.flow.first

class CookbookRepository(
    private val cookbookApi: CookbookApi,
    private val tokenDataStore: TokenDataStore,
) {
    suspend fun listCookbooks(userId: Long): Result<List<CookbookInfo>> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.listCookbooks(token, userId)
    }

    suspend fun getCookbook(id: Long): Result<CookbookInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        cookbookApi.getCookbook(id, token)
    }

    suspend fun createCookbook(dto: CookbookSaveDto): Result<CookbookInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.createCookbook(token, dto)
    }

    suspend fun updateCookbook(id: Long, dto: CookbookSaveDto): Result<CookbookInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.updateCookbook(id, token, dto)
    }

    suspend fun deleteCookbook(id: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.deleteCookbook(id, token)
    }

    suspend fun isAdminOfCookbook(id: Long): Result<Boolean> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.isAdminOfCookbook(id, token)
    }

    suspend fun leaveCookbook(id: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.leaveCookbook(id, token)
    }

    suspend fun getCookbookUsers(id: Long): Result<List<CookbookUserInfo>> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.getCookbookUsers(id, token)
    }

    suspend fun setCookbookUsers(id: Long, users: List<CookbookUserSaveDto>): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.setCookbookUsers(id, token, users)
    }

    suspend fun getCookbookRecipes(id: Long): Result<List<CookbookRecipeInfo>> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        cookbookApi.getCookbookRecipes(id, token)
    }

    suspend fun searchUsers(query: String): Result<UserSearchResult> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        cookbookApi.searchUsers(query, token)
    }
}
