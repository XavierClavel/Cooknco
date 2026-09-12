package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.IngredientSearchResult
import com.xavierclavel.cooknco.network.dto.IngredientSummary
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeSaveDto
import com.xavierclavel.cooknco.network.dto.UnitInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

enum class RecipeSort(val value: String, val label: String) {
    RECENT("DATE_DESCENDING", "Recent"),
    BEST_MATCH("BEST_MATCH", "Best match"),
    MOST_LIKED("LIKES_DESCENDING", "Most liked"),
}

class RecipeApi(private val client: HttpClient) {

    private val base = ApiClient.BASE_URL

    suspend fun listRecipes(
        token: String,
        userId: Long,
        page: Int,
        size: Int = 20,
    ): List<RecipeOverview> {
        val response = client.get("$base/recipe") {
            bearerAuth(token)
            parameter("user", userId)
            parameter("followedBy", userId)
            parameter("sort", "DATE_DESCENDING")
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun searchRecipes(
        token: String?,
        query: String = "",
        sort: RecipeSort = RecipeSort.RECENT,
        page: Int = 0,
        size: Int = 20,
    ): List<RecipeOverview> {
        val response = client.get("$base/recipe") {
            if (token != null) bearerAuth(token)
            if (query.isNotBlank()) parameter("search", query)
            parameter("sort", sort.value)
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun getRecipe(id: Long, token: String? = null): RecipeInfo {
        val response = client.get("$base/recipe/$id") {
            if (token != null) bearerAuth(token)
            parameter("locale", ApiClient.locale)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun createRecipe(token: String, dto: RecipeSaveDto): RecipeInfo {
        val response = client.post("$base/recipe") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun updateRecipe(id: Long, token: String, dto: RecipeSaveDto): RecipeInfo {
        val response = client.put("$base/recipe/$id") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    /** Images live outside the api: POST {IMAGE_URL}/recipes/{id}, answering with an empty body. */
    suspend fun uploadRecipeImage(token: String, recipeId: Long, imageBytes: ByteArray, mimeType: String) {
        val response = client.post("${ApiClient.IMAGE_URL}/recipes/$recipeId") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(formData {
                append("file", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=recipe.webp")
                })
            }))
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
    }

    suspend fun deleteRecipe(id: Long, token: String) {
        val response = client.delete("$base/recipe/$id") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    suspend fun isLiked(recipeId: Long, token: String): Boolean {
        val response = client.get("$base/like/$recipeId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun addLike(recipeId: Long, token: String) {
        val response = client.post("$base/like/$recipeId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    suspend fun removeLike(recipeId: Long, token: String) {
        val response = client.delete("$base/like/$recipeId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    suspend fun getNotes(recipeId: Long, token: String): String? {
        val response = client.get("$base/recipe-notes/$recipeId") {
            bearerAuth(token)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun saveNotes(recipeId: Long, token: String, notes: String, isCreate: Boolean): String {
        val response = if (isCreate) {
            client.post("$base/recipe-notes/$recipeId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(notes)
            }
        } else {
            client.put("$base/recipe-notes/$recipeId") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(notes)
            }
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    /**
     * One ingredient, for the page that shows it. `GET /ingredient/{id}` answers the full
     * `IngredientInfo`; [IngredientSummary] takes the part the app has a use for.
     */
    suspend fun getIngredient(id: Long, token: String? = null): IngredientSummary {
        val response = client.get("$base/ingredient/$id") {
            if (token != null) bearerAuth(token)
            parameter("locale", ApiClient.locale)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    /**
     * Recipes built on an ingredient — `RecipeFilter.ingredient`.
     *
     * [ownerId] narrows it to one cook's, which is what separates "in your recipes" from
     * "popular with this" on the ingredient page; the two differ only by that and the sort.
     */
    suspend fun recipesWithIngredient(
        ingredientId: Long,
        token: String?,
        ownerId: Long? = null,
        sort: RecipeSort = RecipeSort.RECENT,
        size: Int = 20,
    ): List<RecipeOverview> {
        val response = client.get("$base/recipe") {
            if (token != null) bearerAuth(token)
            parameter("ingredient", ingredientId)
            if (ownerId != null) parameter("user", ownerId)
            parameter("sort", sort.value)
            parameter("page", 0)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    /** Served as `text/plain` — see [decodeJsonText]. */
    suspend fun searchIngredients(query: String, token: String? = null): IngredientSearchResult {
        val response = client.get("$base/ingredient") {
            if (token != null) bearerAuth(token)
            parameter("query", query)
            parameter("page", 0)
            parameter("size", 20)
            parameter("locale", ApiClient.locale)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.decodeJsonText()
    }

    suspend fun listUnits(): List<UnitInfo> {
        val response = client.get("$base/unit")
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }
}
