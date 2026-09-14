package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.UnitInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UnitRepository(
    private val recipeApi: RecipeApi,
) {
    suspend fun getUnits(): List<UnitInfo> = mutex.withLock {
        cached ?: runCatching { recipeApi.listUnits() }
            .onSuccess { cached = it }
            .getOrDefault(DEFAULT_UNITS)
    }

    companion object {
        private val mutex = Mutex()
        private var cached: List<UnitInfo>? = null

        /**
         * The catalog the editor works with until the server answers, and whenever it cannot.
         *
         * Also what amounts are converted with — see [AppUnits] — so it carries the ladder
         * each unit sits on and whether a conversion may land on it. Kept in step with
         * `shared.enums.AmountUnit`, which is where these values are decided.
         */
        val DEFAULT_UNITS = listOf(
            UnitInfo("NONE", "NONE", 0f),
            UnitInfo("UNIT", "AMOUNT", 1f),
            UnitInfo("GRAM", "WEIGHT", 1f, "METRIC", isDisplayUnit = true),
            UnitInfo("KILOGRAM", "WEIGHT", 1_000f, "METRIC", isDisplayUnit = true),
            UnitInfo("OUNCE", "WEIGHT", 28.349523f, "IMPERIAL", isDisplayUnit = true),
            UnitInfo("POUND", "WEIGHT", 453.59237f, "IMPERIAL", isDisplayUnit = true),
            UnitInfo("MILLILITERS", "VOLUME", 1f, "METRIC", isDisplayUnit = true),
            UnitInfo("CENTILITER", "VOLUME", 10f, "METRIC"),
            UnitInfo("LITER", "VOLUME", 1_000f, "METRIC", isDisplayUnit = true),
            UnitInfo("FLUID_OUNCE", "VOLUME", 29.57353f, "IMPERIAL", isDisplayUnit = true),
            UnitInfo("TEASPOON", "VOLUME", 5f),
            UnitInfo("TABLESPOON", "VOLUME", 15f),
            UnitInfo("CUP", "VOLUME", 240f, "IMPERIAL", isDisplayUnit = true),
        )
    }
}
