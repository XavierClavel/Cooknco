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
            .map { withLadders(it) }
            .onSuccess { cached = it }
            .getOrDefault(DEFAULT_UNITS)
    }

    companion object {
        private val mutex = Mutex()
        private var cached: List<UnitInfo>? = null

        /**
         * The served catalogue, with the ladders filled back in when it carries none.
         *
         * A backend that predates unit preferences answers `/unit` with names, families and
         * factors and *no* `system` — and since the app converts off that field alone, a
         * catalogue without it converts nothing: picking imperial changed no amount on any
         * screen, while the packaged copy sitting in front of it until the request returned
         * converted them correctly. Which reads exactly like "it needs a restart to take
         * effect", and then quietly stops.
         *
         * Decided over the whole answer rather than per unit: a server that knows about
         * ladders is taken exactly as it comes, including a unit it deliberately puts on
         * none (a spoon, a countable piece). Only an answer where *nothing* sits on a ladder
         * is read as "this backend has never heard of them", and only the two fields it
         * cannot have an opinion about are filled in — names and factors stay the server's.
         *
         * A unit the packaged copy does not know keeps what it was served: no ladder, so it
         * is never converted, which is the same thing [AppUnits] does with any unit it
         * cannot place.
         */
        internal fun withLadders(served: List<UnitInfo>): List<UnitInfo> {
            if (served.isEmpty()) return DEFAULT_UNITS
            if (served.any { it.system != null }) return served
            val packaged = DEFAULT_UNITS.associateBy { it.name }
            return served.map { unit ->
                val known = packaged[unit.name] ?: return@map unit
                unit.copy(system = known.system, isDisplayUnit = known.isDisplayUnit)
            }
        }

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
