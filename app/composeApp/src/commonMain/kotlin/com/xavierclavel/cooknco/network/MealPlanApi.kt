package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.MealPlanEntryEditDto
import com.xavierclavel.cooknco.network.dto.MealPlanEntrySaveDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * The account's meal plan. Premium: every route answers `403 premium_required` to anyone else,
 * so nothing here is called for an account the app already knows is not.
 */
class MealPlanApi(private val client: HttpClient) {

    private val base = "${ApiClient.BASE_URL}/meal-plan"

    /** Every dish from [from] to [to], both included, as `yyyy-MM-dd`. */
    suspend fun list(token: String, from: String, to: String): List<MealPlanEntry> {
        val response = client.get(base) {
            bearerAuth(token)
            parameter("from", from)
            parameter("to", to)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    /** What this account planned before that was not a recipe, newest first. */
    suspend fun suggestions(token: String, query: String): List<String> {
        val response = client.get("$base/suggestions") {
            bearerAuth(token)
            if (query.isNotBlank()) parameter("query", query)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun create(token: String, dto: MealPlanEntrySaveDto): MealPlanEntry {
        val response = client.post(base) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun update(token: String, id: Long, dto: MealPlanEntryEditDto): MealPlanEntry {
        val response = client.put("$base/$id") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun delete(token: String, id: Long) {
        val response = client.delete("$base/$id") { bearerAuth(token) }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
    }
}
