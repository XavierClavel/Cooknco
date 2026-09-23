package main.com.xavierclavel.utils

import com.xavierclavel.utils.logger
import shared.dto.CookbookDTO
import shared.infodto.CookbookInfo
import shared.infodto.CookbookRecipeInfo
import shared.infodto.CookbookUserInfo
import shared.overviewdto.CookbookRecipeOverview
import shared.utils.URL.COOKBOOK_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals

val sampleCookbookDto = CookbookDTO(
    title="sample cookbook",
    description="sample cookbook description",
)

suspend fun HttpClient.createCookbook(cookbookDto: CookbookDTO = sampleCookbookDto) : CookbookInfo {
    this.post(COOKBOOK_URL){
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(cookbookDto)
    }.apply {
        assertEquals(HttpStatusCode.Created, status)
        return Json.decodeFromString<CookbookInfo>(bodyAsText())
    }
}

suspend fun HttpClient.getCookbookRaw(id: Long) =
    this.get("$COOKBOOK_URL/$id")

suspend fun HttpClient.getCookbook(id: Long): CookbookInfo {
    this.getCookbookRaw(id)
        .apply {
            assertEquals(HttpStatusCode.OK, status)
            return Json.decodeFromString<CookbookInfo>(bodyAsText())
        }
}

suspend fun HttpClient.listCookbooks(user: Long? = null): Set<CookbookInfo> {
    this.get(COOKBOOK_URL) {
        url.apply { if (user != null) parameter("user", user) }
    }
        .apply {
            assertEquals(HttpStatusCode.OK, status)
            return Json.decodeFromString<Set<CookbookInfo>>(bodyAsText())
        }
}

suspend fun HttpClient.getCookbookUsers(id: Long): Set<CookbookUserInfo> {
    this.get("$COOKBOOK_URL/$id/users")
        .apply {
            assertEquals(HttpStatusCode.OK, status)
            return Json.decodeFromString<Set<CookbookUserInfo>>(bodyAsText())
        }
}

suspend fun HttpClient.getCookbookRecipes(id: Long): Set<CookbookRecipeInfo> {
    this.get("$COOKBOOK_URL/$id/recipes")
        .apply {
            logger.info {this}
            assertEquals(HttpStatusCode.OK, status)
            return Json.decodeFromString<Set<CookbookRecipeInfo>>(bodyAsText())
        }
}




suspend fun HttpClient.assertCookbookExists(id:Long) {
    this.getCookbook(id)
}

suspend fun HttpClient.assertCookbookDoesNotExist(id:Long) {
    this.get("$COOKBOOK_URL/$id")
        .apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
}

suspend fun HttpClient.deleteCookbook(id: Long) {
    this.deleteCookbookRaw(id)
        .apply {
            assertEquals(HttpStatusCode.OK, status)
        }
}

suspend fun HttpClient.deleteCookbookRaw(id: Long) = this.delete("$COOKBOOK_URL/$id")

suspend fun HttpClient.addCookbookUser(cookbookId: Long, userId: Long, isAdmin: Boolean) {
    this.addCookbookUserRaw(cookbookId, userId, isAdmin)
        .apply {
            assertEquals(HttpStatusCode.OK, status)
        }
}

suspend fun HttpClient.addCookbookUserRaw(cookbookId: Long, userId: Long, isAdmin: Boolean) =
    this.post("$COOKBOOK_URL/$cookbookId/user/$userId?isAdmin=$isAdmin")

suspend fun HttpClient.deleteCookbookUser(cookbookId: Long, userId: Long) {
    this.deleteCookbookUserRaw(cookbookId, userId)
        .apply {
            assertEquals(HttpStatusCode.OK, status)
        }
}

suspend fun HttpClient.deleteCookbookUserRaw(cookbookId: Long, userId: Long) =
    this.delete("$COOKBOOK_URL/$cookbookId/user/$userId")

suspend fun HttpClient.addCookbookRecipe(cookbookId: Long, recipeId: Long) =
    this.post("$COOKBOOK_URL/$cookbookId/recipe/$recipeId")


suspend fun HttpClient.deleteCookbookRecipe(cookbookId: Long, recipeId: Long) =
    this.delete("$COOKBOOK_URL/$cookbookId/recipe/$recipeId")


suspend fun HttpClient.getRecipeUserCookbooks(userId: Long, recipeId: Long) : Set<CookbookRecipeOverview> {
    this.get("$COOKBOOK_URL/recipeStatus") {
        parameter("user", userId)
        parameter("recipe", recipeId)
    }.apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString<Set<CookbookRecipeOverview>>(bodyAsText())
    }
}

suspend fun HttpClient.leaveCookbook(cookbookId: Long) {
    this.delete("$COOKBOOK_URL/$cookbookId/leave").apply { assertEquals(HttpStatusCode.OK, status) }
}