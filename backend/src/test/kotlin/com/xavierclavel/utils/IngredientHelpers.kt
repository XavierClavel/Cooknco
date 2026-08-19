package main.com.xavierclavel.utils

import shared.dto.IngredientDTO
import shared.dto.SearchResult
import shared.enums.IngredientType
import shared.infodto.IngredientInfo
import shared.utils.URL.INGREDIENT_URL
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

val ingredientDTO = IngredientDTO(type = IngredientType.VEGETABLE, calories = 1)

suspend fun HttpClient.createIngredient(ingredient: IngredientDTO = ingredientDTO) : IngredientInfo {
    this.post(INGREDIENT_URL){
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(ingredient)
    }.apply{
        assertEquals(HttpStatusCode.Created, status)
        val response = Json.decodeFromString<IngredientInfo>(bodyAsText())
        assertTrue(response.compareToDTO(ingredient))
        return response
    }

}

suspend fun HttpClient.searchIngredients(query: String): SearchResult<IngredientInfo> {
    this.get(INGREDIENT_URL) {
        url {
            parameters.append("query", query)
            parameters.append("locale", "en")
        }
    }.apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString(SearchResult.serializer(IngredientInfo.serializer()), bodyAsText())
    }
}

suspend fun HttpClient.getIngredient(id: Long): IngredientInfo {
    this.get("$INGREDIENT_URL/$id").apply {
        assertEquals(HttpStatusCode.OK, status)
        val response = Json.decodeFromString<IngredientInfo>(bodyAsText())
        return response
    }
}

suspend fun HttpClient.updateIngredient(id: Long, ingredient: IngredientDTO): IngredientInfo {
    this.put("$INGREDIENT_URL/$id"){
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(ingredient)
    }.apply{
        assertEquals(HttpStatusCode.OK, status)
        val response = Json.decodeFromString<IngredientInfo>(bodyAsText())
        println(response)
        println(ingredient)
        assertTrue(response.compareToDTO(ingredient))
        return response
    }
}

suspend fun HttpClient.deleteIngredient(id: Long) {
    this.delete("$INGREDIENT_URL/$id").apply{
        assertEquals(HttpStatusCode.OK, status)
    }
    this.assertIngredientDoesNotExist(id)
}

suspend fun HttpClient.assertIngredientExists(id: Long) {
    this.get("$INGREDIENT_URL/$id").apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}

suspend fun HttpClient.assertIngredientDoesNotExist(id: Long) {
    this.get("$INGREDIENT_URL/$id").apply {
        assertEquals(HttpStatusCode.NotFound, status)
    }
}