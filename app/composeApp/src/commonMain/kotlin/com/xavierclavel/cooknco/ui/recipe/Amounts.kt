package com.xavierclavel.cooknco.ui.recipe

import kotlin.math.roundToInt

/**
 * An amount written for the recipe's own yield, shown for the number of portions the cook
 * picked.
 *
 * Every amount in the product is stored against the recipe's yield and scaled at the point it
 * is read — the ingredient list does it, and a step's share of an ingredient has to do it the
 * same way, or a recipe cooked for twelve would list twice the butter and then tell each step
 * to use half of it.
 *
 * Rounded to two decimals and trimmed, because 66.66666 grams is not a measurement anybody
 * takes, and left as a whole number when it is one.
 */
fun scaleAmount(amount: Float?, selectedYield: Int, recipeYield: Int): String {
    if (amount == null) return ""
    if (recipeYield <= 0) return ""
    val scaled = amount * selectedYield.toFloat() / recipeYield.toFloat()
    return if (scaled == scaled.roundToInt().toFloat()) scaled.roundToInt().toString()
    else ((scaled * 100).roundToInt() / 100f).toString().trimEnd('0').trimEnd('.')
}

/**
 * An amount as a cook would type it: whole when it is whole, two decimals at most otherwise.
 *
 * Used to put a stored amount back into a text field. [scaleAmount] ends with the same rule -
 * this is that rule on its own, for the editor, which shows amounts as written rather than
 * scaled.
 */
fun formatAmount(amount: Float): String =
    if (amount == amount.roundToInt().toFloat()) amount.roundToInt().toString()
    else ((amount * 100).roundToInt() / 100f).toString().trimEnd('0').trimEnd('.')
