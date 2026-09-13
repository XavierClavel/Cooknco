package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.network.dto.RecipeInfo

/**
 * What a step's ingredient works out to when the author named no amount, by ingredient
 * position.
 *
 * A blank means "unspecified", not "all of it", and what it is worth depends on what the
 * recipe's *other* steps said about the same ingredient — so it is computed over the whole
 * recipe at once rather than per step.
 *
 * One blank is the remainder: the line's amount less whatever the other steps spelled out.
 * With nothing spelled out that is the whole line, which is why a single step using an
 * ingredient needs no number typed; with 60 g of the 100 g named elsewhere it is 40 g, without
 * anyone doing the subtraction.
 *
 * Two or more blanks share a remainder that nothing says how to divide, so they are worth no
 * number at all. Guessing an even split would be inventing a measurement, and refusing to save
 * it would make "butter in these two steps, roughly" unsayable — a normal thing for a recipe
 * to mean.
 *
 * **Here rather than on the server**, which stores and returns only what was stated. The
 * recipe arrives whole, so every reader can do this arithmetic, and sending a derived number
 * instead would put a field on the wire that must never be written back — a trap a client
 * falls into by doing the obvious thing: read the recipe, put the numbers in the boxes, save.
 */
fun RecipeInfo.blankStepAmounts(): Map<Int, Float?> {
    val used = steps.flatMap { it.ingredients }
    if (used.isEmpty()) return emptyMap()

    return used.groupBy { it.index }
        .mapValues { (position, forIngredient) ->
            if (forIngredient.count { it.amount == null } != 1) return@mapValues null
            val listed = ingredients.getOrNull(position)?.amount ?: return@mapValues null
            val spelledOut = forIngredient.mapNotNull { it.amount }.sum()
            (listed - spelledOut).takeIf { it > 0f }
        }
}
