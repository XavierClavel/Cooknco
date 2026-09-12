package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.IngredientSort
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.IngredientSearchResult
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeSaveDto
import kotlinx.coroutines.flow.first

class RecipeRepository(
    private val recipeApi: RecipeApi,
    private val tokenDataStore: TokenDataStore,
) {
    suspend fun listRecipes(userId: Long, page: Int): Result<List<RecipeOverview>> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.listRecipes(token, userId, page)
    }

    suspend fun getRecipe(id: Long): Result<RecipeInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        recipeApi.getRecipe(id, token)
    }

    suspend fun createRecipe(dto: RecipeSaveDto): Result<RecipeInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.createRecipe(token, dto)
    }

    suspend fun updateRecipe(id: Long, dto: RecipeSaveDto): Result<RecipeInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.updateRecipe(id, token, dto)
    }

    suspend fun deleteRecipe(id: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.deleteRecipe(id, token)
    }

    suspend fun uploadRecipeImage(recipeId: Long, imageBytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.uploadRecipeImage(token, recipeId, imageBytes, mimeType)
    }

    suspend fun isLiked(recipeId: Long): Result<Boolean> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.isLiked(recipeId, token)
    }

    suspend fun addLike(recipeId: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.addLike(recipeId, token)
    }

    suspend fun removeLike(recipeId: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.removeLike(recipeId, token)
    }

    suspend fun getNotes(recipeId: Long): Result<String?> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.getNotes(recipeId, token)
    }

    suspend fun saveNotes(recipeId: Long, notes: String, isCreate: Boolean): Result<String> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.saveNotes(recipeId, token, notes, isCreate)
    }

    suspend fun getIngredient(id: Long): Result<IngredientSummary> = runCatching {
        recipeApi.getIngredient(id, tokenDataStore.tokenFlow.first())
    }

    suspend fun recipesWithIngredient(
        ingredientId: Long,
        ownerId: Long? = null,
        sort: RecipeSort = RecipeSort.RECENT,
    ): Result<List<RecipeOverview>> = runCatching {
        recipeApi.recipesWithIngredient(
            ingredientId = ingredientId,
            token = tokenDataStore.tokenFlow.first(),
            ownerId = ownerId,
            sort = sort,
        )
    }

    suspend fun searchIngredients(
        query: String,
        sort: IngredientSort = IngredientSort.BEST_MATCH,
    ): Result<IngredientSearchResult> = runCatching {
        val token = tokenDataStore.tokenFlow.first()
        recipeApi.searchIngredients(query, token, sort)
    }
}
