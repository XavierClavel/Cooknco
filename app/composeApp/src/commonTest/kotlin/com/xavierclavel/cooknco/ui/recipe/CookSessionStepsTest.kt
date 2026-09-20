package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.data.AppUnitSystem
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.network.dto.RecipeStepInfo
import com.xavierclavel.cooknco.network.dto.RecipeStepIngredientInfo
import com.xavierclavel.cooknco.ui.i18n.EnStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recipe as it is written down for the notification.
 *
 * This is the one moment the ingredients of every step are worked out — the portions, the
 * blanks, the cook's ladder — because what redraws them later is a broadcast receiver with no
 * recipe, no catalogue and no network. An amount that is wrong here is wrong for the whole
 * hour the cook spends following it, and there is nothing downstream to catch it.
 */
class CookSessionStepsTest {

    private val units = UnitRepository.DEFAULT_UNITS

    private fun recipe(
        yield: Int? = 4,
        ingredients: List<Triple<String, Float?, String>> = emptyList(),
        steps: List<RecipeStepInfo> = emptyList(),
    ) = RecipeInfo(
        id = 1L,
        version = 1L,
        title = "Harcha",
        dishClass = "MAIN_DISH",
        owner = RecipeOwner(id = 1L, version = 1L, username = "Aya"),
        yield = yield,
        ingredients = ingredients.mapIndexed { index, (name, amount, unit) ->
            RecipeIngredientInfo(id = index.toLong(), name = name, amount = amount, unit = unit)
        },
        steps = steps,
        creationDate = 0L,
        likesCount = 0,
    )

    private fun steps(recipe: RecipeInfo, servings: Int, system: AppUnitSystem = AppUnitSystem.METRIC) =
        CookModeViewModel.sessionSteps(recipe, servings, system, units, EnStrings)

    @Test
    fun `an amount is scaled to the portions being cooked`() {
        val recipe = recipe(
            ingredients = listOf(Triple("Semolina", 200f, "GRAM")),
            steps = listOf(RecipeStepInfo("Mix", ingredients = listOf(RecipeStepIngredientInfo(0, 200f)))),
        )
        assertEquals(listOf("200 g Semolina"), steps(recipe, servings = 4).first().ingredients)
        // Eight portions of a recipe written for four, in the shade as on the screen.
        assertEquals(listOf("400 g Semolina"), steps(recipe, servings = 8).first().ingredients)
    }

    @Test
    fun `and put on the cook's own ladder, after being scaled`() {
        val recipe = recipe(
            ingredients = listOf(Triple("Semolina", 200f, "GRAM")),
            steps = listOf(RecipeStepInfo("Mix", ingredients = listOf(RecipeStepIngredientInfo(0, 200f)))),
        )
        val line = steps(recipe, servings = 4, system = AppUnitSystem.IMPERIAL).first().ingredients.single()
        assertTrue(line.endsWith("Semolina"), line)
        assertTrue(!line.contains(" g "), "an imperial cook was handed grams: $line")
    }

    @Test
    fun `a blank is resolved over the whole recipe, as it is on the screen`() {
        val recipe = recipe(
            ingredients = listOf(Triple("Butter", 100f, "GRAM")),
            steps = listOf(
                RecipeStepInfo("Rub in", ingredients = listOf(RecipeStepIngredientInfo(0, 60f))),
                RecipeStepInfo("Brush with the rest", ingredients = listOf(RecipeStepIngredientInfo(0))),
            ),
        )
        val written = steps(recipe, servings = 4)
        assertEquals(listOf("60 g Butter"), written[0].ingredients)
        assertEquals(listOf("40 g Butter"), written[1].ingredients)
    }

    @Test
    fun `an ingredient nothing can put a number to keeps its name`() {
        // Two steps sharing a line that says nothing about how it divides — a working
        // ingredient rather than an invented measurement. See blankStepAmounts.
        val recipe = recipe(
            ingredients = listOf(Triple("Butter", 100f, "GRAM")),
            steps = listOf(
                RecipeStepInfo("Rub in", ingredients = listOf(RecipeStepIngredientInfo(0))),
                RecipeStepInfo("Brush", ingredients = listOf(RecipeStepIngredientInfo(0))),
            ),
        )
        assertEquals(listOf("Butter"), steps(recipe, servings = 4).first().ingredients)
    }

    @Test
    fun `a step using an ingredient the recipe no longer has drops it`() {
        val recipe = recipe(
            ingredients = listOf(Triple("Semolina", 200f, "GRAM")),
            steps = listOf(RecipeStepInfo("Mix", ingredients = listOf(RecipeStepIngredientInfo(4, 10f)))),
        )
        assertEquals(emptyList(), steps(recipe, servings = 4).first().ingredients)
    }

    @Test
    fun `a duration is carried, whether it was set or only written`() {
        val recipe = recipe(
            steps = listOf(
                RecipeStepInfo("Rest the dough", durationSeconds = 600),
                RecipeStepInfo("Bake for 25 minutes"),
                RecipeStepInfo("Serve"),
            ),
        )
        val written = steps(recipe, servings = 4)
        assertEquals(600, written[0].durationSeconds)
        // The same rule cook mode's card reads, so the two never disagree about the words.
        assertEquals(25 * 60, written[1].durationSeconds)
        assertEquals(null, written[2].durationSeconds)
    }
}
