package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import shared.dto.OfflineSyncDTO
import shared.enums.Locale
import shared.infodto.OfflineSyncInfo
import shared.infodto.RecipeInfo
import shared.utils.URL.OFFLINE_URL
import kotlin.test.assertEquals

suspend fun HttpClient.syncOfflineRaw(held: List<OfflineSyncDTO.HeldRecipe> = emptyList()) =
    this.post("$OFFLINE_URL/sync") {
        parameter("locale", Locale.EN)
        contentType(ContentType.Application.Json)
        setBody(OfflineSyncDTO(held))
    }

suspend fun HttpClient.syncOffline(held: List<OfflineSyncDTO.HeldRecipe> = emptyList()): OfflineSyncInfo =
    this.syncOfflineRaw(held).let {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        Json.decodeFromString(it.bodyAsText())
    }

/** What a phone holding exactly [recipes] says it holds. */
fun heldOf(recipes: Collection<RecipeInfo>): List<OfflineSyncDTO.HeldRecipe> =
    recipes.map { OfflineSyncDTO.HeldRecipe(it.id, it.revision) }

/**
 * Syncs the way the app does: asks again while there is more, counting what each answer
 * brought as held, and returns every answer.
 */
suspend fun HttpClient.syncOfflineFully(
    held: List<OfflineSyncDTO.HeldRecipe> = emptyList(),
): List<OfflineSyncInfo> {
    val holding = held.associateBy { it.id }.toMutableMap()
    val answers = mutableListOf<OfflineSyncInfo>()
    do {
        val answer = syncOffline(holding.values.toList())
        answers += answer
        heldOf(answer.recipes).forEach { holding[it.id] = it }
    } while (answer.hasMore && answer.recipes.isNotEmpty())
    return answers
}
