package main.com.xavierclavel.utils

import com.xavierclavel.utils.logger
import shared.dto.RecipeDTO
import shared.enums.DishClass
import shared.enums.Sort
import shared.infodto.RecipeInfo
import shared.overviewdto.RecipeOverview
import shared.utils.URL.RECIPE_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertTrue

val recipeDTO = RecipeDTO(
    title = "My recipe",
    description = "My description",
    steps = mutableListOf(
        "cut",
        "cook"
    )
)

suspend fun HttpClient.createRecipeRaw(recipe: RecipeDTO = recipeDTO) =
    this.post(RECIPE_URL){
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(recipe)
    }

suspend fun HttpClient.createRecipe(recipe: RecipeDTO = recipeDTO) : RecipeInfo {
    logger.info { "Creating recipe ${recipe.title}" }
    this.createRecipeRaw(recipe).apply{
        assertEquals(HttpStatusCode.Created, status)
        val a = bodyAsText()
        logger.info {a}
        val response = Json.decodeFromString<RecipeInfo>(bodyAsText())
        assertTrue(response.compareToDTO(recipe))
        return response
    }

}

suspend fun HttpClient.getRecipeRaw(recipeId: Long) =
    this.get("$RECIPE_URL/$recipeId?locale=en")

suspend fun HttpClient.getRecipe(recipeId: Long): RecipeInfo {
    this.getRecipeRaw(recipeId).apply {
        assertEquals(HttpStatusCode.OK, status)
        val response = Json.decodeFromString<RecipeInfo>(bodyAsText())
        return response
    }
}

suspend fun HttpClient.listRecipes(
    user: Long? = null,
    sort: Sort? = null,
    cookbook: Long? = null,
    likedBy: Long? = null,
    followedBy: Long? = null,
    cookbookUser: Long? = null,
    search: String? = null,
    ingredient: Set<Long> = setOf(),
    dishClasses: Set<DishClass> = setOf(),
): List<RecipeOverview> {
    listRecipesRaw(
        user = user,
        sort = sort,
        cookbook = cookbook,
        likedBy = likedBy,
        followedBy = followedBy,
        cookbookUser = cookbookUser,
        search = search,
        ingredient = ingredient,
        dishClasses = dishClasses,
    ).apply {
        assertEquals(HttpStatusCode.OK, status)
        val response = Json.decodeFromString<List<RecipeOverview>>(bodyAsText())
        return response
    }
}

suspend fun HttpClient.listRecipesRaw(
    user: Long? = null,
    sort: Sort? = null,
    cookbook: Long? = null,
    likedBy: Long? = null,
    followedBy: Long? = null,
    cookbookUser: Long? = null,
    search: String? = null,
    ingredient: Set<Long> = setOf(),
    dishClasses: Set<DishClass> = setOf(),
) =
    this.get(RECIPE_URL) {
        url {
            user?.let { parameters.append("user", it.toString()) }
            cookbook?.let {parameters.append("cookbook", it.toString())}
            likedBy?.let { parameters.append("likedBy", it.toString()) }
            followedBy?.let { parameters.append("followedBy", it.toString()) }
            sort?.let { parameters.append("sort", it.toString()) }
            cookbookUser?.let { parameters.append("cookbookUser", it.toString()) }
            search?.let { parameters.append("search", it.toString()) }
            ingredient.takeIf { it.isNotEmpty() }?.let { parameters.append("ingredient", it.joinToString(","))}
            dishClasses.takeIf { it.isNotEmpty() }?.let { parameters.append("dishClasses", it.joinToString(",")) }
            parameters.append("locale", "en")
        }
    }

suspend fun HttpClient.updateRecipeRaw(recipeId: Long, recipe: RecipeDTO) =
    this.put("$RECIPE_URL/$recipeId"){
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(recipe)
    }

suspend fun HttpClient.updateRecipe(recipeId: Long, recipe: RecipeDTO): RecipeInfo {
    logger.info {"update"}
    this.updateRecipeRaw(recipeId, recipe).apply{
        assertEquals(HttpStatusCode.OK, status)
        val response = Json.decodeFromString<RecipeInfo>(bodyAsText())
        assertTrue(response.compareToDTO(recipe))
        return response
    }
}

suspend fun HttpClient.deleteRecipe(recipeId: Long) {
    this.delete("$RECIPE_URL/$recipeId").apply{
        assertEquals(HttpStatusCode.OK, status)
    }
}

suspend fun HttpClient.assertRecipeExists(recipeId: Long) {
    this.getRecipeRaw(recipeId).apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}

suspend fun HttpClient.assertRecipeDoesNotExist(recipeId: Long) {
    this.getRecipeRaw(recipeId).apply {
        assertEquals(HttpStatusCode.NotFound, status)
    }
}