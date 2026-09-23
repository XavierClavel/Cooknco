package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeStatus
import com.xavierclavel.cooknco.network.dto.CookbookSaveDto
import com.xavierclavel.cooknco.network.dto.CookbookUserInfo
import com.xavierclavel.cooknco.network.dto.CookbookUserSaveDto
import com.xavierclavel.cooknco.network.dto.UserSearchResult
import com.xavierclavel.cooknco.network.isOffline
import kotlinx.coroutines.flow.first

class CookbookRepository(
    private val cookbookApi: CookbookApi,
    private val tokenDataStore: TokenDataStore,
    private val offlineStore: OfflineStore,
) {

    /** The same rule as `RecipeRepository.readThrough`, which documents it. */
    private suspend fun <T> readThrough(
        fetch: suspend () -> T,
        fromStore: suspend () -> T?,
    ): Result<T> {
        val result = OfflineState.observe(runCatching { fetch() })
        val error = result.exceptionOrNull() ?: return result
        if (!error.isOffline) return result
        return fromStore()?.let { Result.success(it) } ?: result
    }
    suspend fun listCookbooks(userId: Long): Result<List<CookbookInfo>> = readThrough(
        fetch = {
            val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
            cookbookApi.listCookbooks(token, userId)
        },
        // Only this cook's own list is ever stored, so somebody else's profile falls through
        // to the failure rather than being answered with the reader's cookbooks.
        fromStore = { offlineStore.readLists()?.cookbooks?.takeIf { offlineOwner() == userId } },
    )

    /** Whose copy the store holds, so a list is never answered for the wrong account. */
    private suspend fun offlineOwner(): Long? = offlineStore.readIndex()?.userId

    suspend fun getCookbook(id: Long): Result<CookbookInfo> = readThrough(
        fetch = {
            val token = tokenDataStore.tokenFlow.first()
            cookbookApi.getCookbook(id, token)
        },
        fromStore = { offlineStore.readCookbook(id)?.cookbook },
    )

    suspend fun createCookbook(dto: CookbookSaveDto): Result<CookbookInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.createCookbook(token, dto)
    }

    suspend fun updateCookbook(id: Long, dto: CookbookSaveDto): Result<CookbookInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.updateCookbook(id, token, dto)
    }

    suspend fun uploadCookbookImage(cookbookId: Long, imageBytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.uploadCookbookImage(token, cookbookId, imageBytes, mimeType)
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

    /**
     * This cook's cookbooks, and whether each already holds the recipe.
     *
     * Answered offline from the stored cookbooks, because the bookmark in the recipe banner is
     * drawn from it — a hollow bookmark on a recipe the cook has filed is wrong in a way an
     * absent one is not. The sheet it opens refuses to change anything offline; see
     * [addRecipeToCookbook].
     */
    suspend fun recipeStatusInCookbooks(recipeId: Long): Result<List<CookbookRecipeStatus>> = readThrough(
        fetch = {
            val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
            cookbookApi.recipeStatusInCookbooks(recipeId, token)
        },
        fromStore = {
            offlineStore.readLists()?.cookbooks?.map { cookbook ->
                CookbookRecipeStatus(
                    id = cookbook.id,
                    title = cookbook.title,
                    hasRecipe = offlineStore.readCookbook(cookbook.id)
                        ?.recipes.orEmpty().any { it.id == recipeId },
                )
            }
        },
    )

    suspend fun addRecipeToCookbook(cookbookId: Long, recipeId: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.addRecipeToCookbook(cookbookId, recipeId, token)
    }

    suspend fun removeRecipeFromCookbook(cookbookId: Long, recipeId: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        cookbookApi.removeRecipeFromCookbook(cookbookId, recipeId, token)
    }

    suspend fun getCookbookRecipes(id: Long): Result<List<CookbookRecipeInfo>> = readThrough(
        fetch = {
            val token = tokenDataStore.tokenFlow.first()
            cookbookApi.getCookbookRecipes(id, token)
        },
        fromStore = { offlineStore.readCookbook(id)?.recipes },
    )

    suspend fun searchUsers(query: String): Result<UserSearchResult> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        cookbookApi.searchUsers(query, token)
    }

    suspend fun searchCookbooks(query: String): Result<List<CookbookInfo>> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        cookbookApi.searchCookbooks(query, token)
    }
}
