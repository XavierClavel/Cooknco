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

        /** The catalog the editor works with until the server answers, and whenever it cannot. */
        val DEFAULT_UNITS = listOf(
            UnitInfo("NONE", "NONE", 0f),
            UnitInfo("UNIT", "AMOUNT", 1f),
            UnitInfo("GRAM", "WEIGHT", 1f),
            UnitInfo("KILOGRAM", "WEIGHT", 1_000f),
            UnitInfo("POUND", "WEIGHT", 453.59237f),
            UnitInfo("MILLILITERS", "VOLUME", 1f),
            UnitInfo("CENTILITER", "VOLUME", 10f),
            UnitInfo("LITER", "VOLUME", 1_000f),
            UnitInfo("TEASPOON", "VOLUME", 5f),
            UnitInfo("TABLESPOON", "VOLUME", 15f),
            UnitInfo("CUP", "VOLUME", 240f),
        )
    }
}
