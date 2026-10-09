package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.MealPlanApi
import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.MealPlanEntryEditDto
import com.xavierclavel.cooknco.network.dto.MealPlanEntrySaveDto
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The meal plan. Online only: nothing of it is kept on the phone, unlike the recipes the plan
 * points at. A plan is edited from more than one place — this tab, a recipe's sheet, the website
 * — and an offline copy would be one more that disagrees.
 */
class MealPlanRepository(
    private val api: MealPlanApi,
    private val tokenDataStore: TokenDataStore,
) {
    private suspend fun token(): String = tokenDataStore.tokenFlow.first() ?: error("Not authenticated")

    /** The week starting on [start], seven days. Reported to [OfflineState] so the banner is right. */
    suspend fun week(start: LocalDate): Result<List<MealPlanEntry>> = OfflineState.observe(runCatching {
        api.list(token(), start.toString(), start.plus(6, DateTimeUnit.DAY).toString())
    })

    suspend fun suggestions(query: String): Result<List<String>> = runCatching {
        api.suggestions(token(), query)
    }

    /** Null [servings] plans it for as many as the recipe makes. */
    suspend fun planRecipe(recipeId: Long, date: LocalDate, slot: MealSlot, servings: Int?): Result<MealPlanEntry> = runCatching {
        api.create(token(), MealPlanEntrySaveDto(date = date.toString(), slot = slot.name, recipeId = recipeId, servings = servings))
    }

    suspend fun planDish(title: String, date: LocalDate, slot: MealSlot, servings: Int?): Result<MealPlanEntry> = runCatching {
        api.create(token(), MealPlanEntrySaveDto(date = date.toString(), slot = slot.name, title = title, servings = servings))
    }

    /** [title] is only applied to a dish that is not a recipe; null leaves it as it is. */
    suspend fun update(id: Long, date: LocalDate, slot: MealSlot, servings: Int?, title: String?): Result<MealPlanEntry> = runCatching {
        api.update(token(), id, MealPlanEntryEditDto(date = date.toString(), slot = slot.name, servings = servings, title = title))
    }

    suspend fun remove(id: Long): Result<Unit> = runCatching { api.delete(token(), id) }
}
