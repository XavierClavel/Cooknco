package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.IngredientSort
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.RecipeSort
import com.xavierclavel.cooknco.network.dto.CooklangImportDto
import com.xavierclavel.cooknco.network.dto.IngredientSearchResult
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeSaveDto
import com.xavierclavel.cooknco.network.isOffline
import kotlinx.coroutines.flow.first

class RecipeRepository(
    private val recipeApi: RecipeApi,
    private val tokenDataStore: TokenDataStore,
    private val offlineStore: OfflineStore,
) {

    /**
     * The rule every read below follows: the network is the truth when it answers, and the
     * store is the answer when it does not.
     *
     * Never both, and never the other way round. Two conditions, and neither is negotiable:
     *
     * - **Only when there was no server.** A 404 is the server saying the recipe is gone and a
     *   403 is it saying this cook may not read it; answering either from a copy on the phone
     *   would be showing somebody a recipe a moderator has hidden.
     * - **Only for something we pinned.** Falling back for a search or the feed would present
     *   a week-old list as today's. The store only ever holds the cook's own three
     *   collections, so "is it in the store" *is* "did they pin it".
     *
     * Every call also reports what it found to [OfflineState], which is what the banner reads.
     */
    private suspend fun <T> readThrough(
        fetch: suspend () -> T,
        fromStore: suspend () -> T?,
    ): Result<T> {
        val result = OfflineState.observe(runCatching { fetch() })
        val error = result.exceptionOrNull() ?: return result
        if (!error.isOffline) return result
        return fromStore()?.let { Result.success(it) } ?: result
    }
    /**
     * The feed. **Observed but never answered from the store**, which is the one read here
     * that deliberately has no fallback: this is other people's cooking, and a feed served
     * from a week-old copy is lying about what is new. It still reports what it found, so the
     * banner is right on a tab that is showing an error rather than a stale list.
     */
    suspend fun listRecipes(userId: Long, page: Int): Result<List<RecipeOverview>> =
        OfflineState.observe(runCatching {
            val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
            recipeApi.listRecipes(token, userId, page)
        })

    suspend fun getRecipe(id: Long): Result<RecipeInfo> = readThrough(
        fetch = {
            val token = tokenDataStore.tokenFlow.first()
            recipeApi.getRecipe(id, token).also { fresh ->
                // Written through rather than left to the next sync: the cook is looking at
                // this recipe now, which is the best evidence there is that it is one they
                // will want again — and an edit they have just made is on screen before the
                // sync that would otherwise have carried it. Only when it differs, so opening
                // a recipe twice is not two writes; the notes and the like are kept, since
                // this call knows neither.
                val held = offlineStore.readRecipe(id)
                if (held != null && held.recipe != fresh) {
                    offlineStore.writeRecipe(held.copy(recipe = fresh))
                }
            }
        },
        fromStore = { offlineStore.readRecipe(id)?.recipe },
    )

    suspend fun createRecipe(dto: RecipeSaveDto): Result<RecipeInfo> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.createRecipe(token, dto)
    }

    /**
     * Reads a Cooklang file into the recipe the editor should show. Saves nothing.
     *
     * The language is read here rather than passed in, as the exports' is: it is whatever
     * the app is currently in, and it decides which names the ingredient catalogue is
     * searched under.
     */
    suspend fun importCooklang(source: String): Result<CooklangImportDto> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.importCooklang(token, source, AppLanguage.current.value.code)
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

    suspend fun uploadStepImage(stepId: Long, imageBytes: ByteArray, mimeType: String): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.uploadStepImage(token, stepId, imageBytes, mimeType)
    }

    suspend fun deleteStepImage(stepId: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.deleteStepImage(token, stepId)
    }

    suspend fun isLiked(recipeId: Long): Result<Boolean> = readThrough(
        fetch = {
            val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
            recipeApi.isLiked(recipeId, token)
        },
        fromStore = { offlineStore.readRecipe(recipeId)?.isLiked },
    )

    suspend fun addLike(recipeId: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.addLike(recipeId, token)
    }

    suspend fun removeLike(recipeId: Long): Result<Unit> = runCatching {
        val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
        recipeApi.removeLike(recipeId, token)
    }

    /**
     * The cook's own notes on a recipe.
     *
     * Doubly nullable, and the two nulls mean different things: the outer one is "we could not
     * find out", the inner one is "there are none". The store answers the inner kind, so a
     * recipe pinned with no notes reads as having none rather than as unreachable — and a
     * recipe that is not pinned at all falls through to the failure.
     */
    suspend fun getNotes(recipeId: Long): Result<String?> {
        val result = OfflineState.observe(runCatching {
            val token = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")
            recipeApi.getNotes(recipeId, token)
        })
        val error = result.exceptionOrNull() ?: return result
        if (!error.isOffline) return result
        // Written out rather than put through [readThrough], because the two nulls here mean
        // different things and that helper has only one of them: "there are no notes" is a
        // perfectly good answer, "we hold no copy of this recipe" is not. The recipe is what
        // tells them apart, so it is the recipe that is looked up.
        val held = offlineStore.readRecipe(recipeId) ?: return result
        return Result.success(held.notes)
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
