package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import shared.dto.MealPlanEntryDTO
import shared.dto.MealPlanEntryEditDTO
import shared.infodto.MealPlanEntryInfo
import shared.utils.URL.MEAL_PLAN_URL
import kotlin.test.assertEquals

suspend fun HttpClient.planMealRaw(dto: MealPlanEntryDTO) =
    this.post(MEAL_PLAN_URL) {
        contentType(ContentType.Application.Json)
        setBody(dto)
    }

suspend fun HttpClient.planMeal(dto: MealPlanEntryDTO): MealPlanEntryInfo =
    this.planMealRaw(dto).let {
        assertEquals(HttpStatusCode.Created, it.status, it.bodyAsText())
        Json.decodeFromString(it.bodyAsText())
    }

suspend fun HttpClient.listMealPlanRaw(from: String?, to: String?) =
    this.get(MEAL_PLAN_URL) {
        from?.let { parameter("from", it) }
        to?.let { parameter("to", it) }
    }

suspend fun HttpClient.listMealPlan(from: String, to: String): List<MealPlanEntryInfo> =
    this.listMealPlanRaw(from, to).let {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        Json.decodeFromString(it.bodyAsText())
    }

suspend fun HttpClient.editMealPlanEntryRaw(id: Long, dto: MealPlanEntryEditDTO) =
    this.put("$MEAL_PLAN_URL/$id") {
        contentType(ContentType.Application.Json)
        setBody(dto)
    }

suspend fun HttpClient.editMealPlanEntry(id: Long, dto: MealPlanEntryEditDTO): MealPlanEntryInfo =
    this.editMealPlanEntryRaw(id, dto).let {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        Json.decodeFromString(it.bodyAsText())
    }

suspend fun HttpClient.removeMealPlanEntryRaw(id: Long) = this.delete("$MEAL_PLAN_URL/$id")

suspend fun HttpClient.mealPlanSuggestions(query: String? = null): List<String> =
    this.get("$MEAL_PLAN_URL/suggestions") { query?.let { parameter("query", it) } }.let {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        Json.decodeFromString(it.bodyAsText())
    }
