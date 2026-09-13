package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.network.dto.RecipeIngredientInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.network.dto.RecipeStepInfo
import com.xavierclavel.cooknco.network.dto.RecipeStepIngredientInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a step's ingredient works out to when nobody typed a number.
 *
 * Worth testing here rather than through the API, which is where it used to live: the rule is
 * arithmetic over a recipe that is already in hand, so it needs no server, no database and no
 * container to check — and the four cases are the whole of it.
 */
class StepAmountsTest {

    private fun recipe(
        ingredients: List<Pair<String, Float?>>,
        steps: List<List<RecipeStepIngredientInfo>>,
    ) = RecipeInfo(
        id = 1L,
        version = 1L,
        title = "Harcha",
        dishClass = "MAIN_DISH",
        owner = RecipeOwner(id = 1L, version = 1L, username = "Aya"),
        ingredients = ingredients.mapIndexed { index, (name, amount) ->
            RecipeIngredientInfo(id = index.toLong(), name = name, amount = amount, unit = "GRAM")
        },
        steps = steps.mapIndexed { index, used -> RecipeStepInfo("step $index", ingredients = used) },
        creationDate = 0L,
        likesCount = 0,
    )

    private fun used(index: Int, amount: Float? = null) = RecipeStepIngredientInfo(index, amount)

    @Test
    fun `one blank and nothing else named is the whole line`() {
        val recipe = recipe(listOf("butter" to 100f), listOf(listOf(used(0))))
        assertEquals(mapOf(0 to 100f), recipe.blankStepAmounts())
    }

    @Test
    fun `one blank beside an amount is the remainder`() {
        val recipe = recipe(listOf("butter" to 100f), listOf(listOf(used(0, 60f)), listOf(used(0))))
        assertEquals(mapOf(0 to 40f), recipe.blankStepAmounts())
    }

    @Test
    fun `two blanks share a remainder nothing divides, so neither gets a number`() {
        val recipe = recipe(listOf("butter" to 100f), listOf(listOf(used(0)), listOf(used(0))))
        assertEquals(mapOf(0 to null), recipe.blankStepAmounts())
    }

    @Test
    fun `two blanks beside an amount still get nothing`() {
        val recipe = recipe(
            listOf("butter" to 100f),
            listOf(listOf(used(0, 30f)), listOf(used(0)), listOf(used(0))),
        )
        assertEquals(mapOf(0 to null), recipe.blankStepAmounts())
    }

    @Test
    fun `an ingredient with no amount of its own has nothing to work out from`() {
        val recipe = recipe(listOf("salt" to null), listOf(listOf(used(0))))
        assertEquals(mapOf(0 to null), recipe.blankStepAmounts())
    }

    @Test
    fun `a blank that would come out negative is worth nothing rather than a minus`() {
        // The server refuses to store this, but a recipe saved before a line was shortened
        // elsewhere should still draw rather than show "-20 g".
        val recipe = recipe(listOf("butter" to 100f), listOf(listOf(used(0, 120f)), listOf(used(0))))
        assertEquals(mapOf(0 to null), recipe.blankStepAmounts())
    }

    @Test
    fun `ingredients are worked out one at a time`() {
        val recipe = recipe(
            listOf("butter" to 100f, "flour" to 200f),
            listOf(listOf(used(0, 60f), used(1)), listOf(used(0))),
        )
        assertEquals(mapOf(0 to 40f, 1 to 200f), recipe.blankStepAmounts())
    }

    @Test
    fun `a step naming a position the recipe does not have is left alone`() {
        val recipe = recipe(listOf("butter" to 100f), listOf(listOf(used(7))))
        assertEquals(mapOf(7 to null), recipe.blankStepAmounts())
    }
}
