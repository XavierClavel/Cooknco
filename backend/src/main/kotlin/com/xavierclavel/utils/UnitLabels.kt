package com.xavierclavel.utils

import shared.enums.AmountUnit
import shared.enums.Locale
import shared.enums.UnitSystem
import shared.infodto.RecipeIngredientInfo
import shared.utils.UnitConversion

/**
 * The unit words an ingredient line needs, per locale, on the ladder it was asked for.
 *
 * All that is left of the PDF sheet's wording: every heading now lives in the layout, which
 * is written per locale. These stay in code because they are part of formatting a value
 * rather than text an operator would reword — and because the clients format amounts the
 * same way, from the same rules.
 *
 * Shared by the two places that render an ingredient as a sentence rather than as fields:
 * [com.xavierclavel.services.ExportService], which prints it, and
 * [com.xavierclavel.services.LinkPreviewService], which puts it in a recipe's `recipeIngredient`
 * for search engines. A second copy would drift, and the one in the structured data is the
 * copy nobody would notice drifting.
 */
class UnitLabels(
    val unitSystem: UnitSystem,
    val teaspoons: String,
    val tablespoons: String,
    val cups: String,
) {
    companion object {
        fun of(locale: Locale, unitSystem: UnitSystem) = when (locale) {
            Locale.EN -> UnitLabels(unitSystem, teaspoons = "tsp", tablespoons = "tbsp", cups = "cups")
            Locale.FR -> UnitLabels(unitSystem, teaspoons = "c. à café", tablespoons = "c. à soupe", cups = "tasses")
        }
    }

    /** `250g flour (sifted)` — an amount of nothing, or of no unit, simply drops out. */
    fun format(ingredient: RecipeIngredientInfo): String = listOfNotNull(
        amountOf(ingredient).takeIf { it.isNotEmpty() },
        ingredient.name,
        ingredient.complement?.takeIf { it.isNotBlank() }?.let { "($it)" },
    ).joinToString(" ")

    /** Just the `250g`, so a layout can put amounts in a column of their own. */
    fun amountOf(ingredient: RecipeIngredientInfo): String =
        ingredient.amount?.takeIf { it > 0f }?.let { formatAmount(it, ingredient.unit) } ?: ""

    /**
     * Mirrors what the clients display (`formatAmount` in the web app): the amount goes
     * on the reader's ladder — which is also what rolls grams and millilitres up to the
     * larger unit — and a whole number keeps no decimals.
     */
    private fun formatAmount(amount: Float, unit: AmountUnit): String {
        val (scaled, scaledUnit) = UnitConversion.displayIn(amount, unit, unitSystem)
        val rounded = "%.2f".format(java.util.Locale.ROOT, scaled).trimEnd('0').trimEnd('.')
        return "$rounded${symbolOf(scaledUnit)}"
    }

    private fun symbolOf(unit: AmountUnit) = when (unit) {
        AmountUnit.NONE, AmountUnit.UNIT -> ""
        AmountUnit.GRAM -> "g"
        AmountUnit.KILOGRAM -> "kg"
        AmountUnit.OUNCE -> "oz"
        AmountUnit.POUND -> "lb"
        AmountUnit.MILLILITERS -> "mL"
        AmountUnit.CENTILITER -> "cL"
        AmountUnit.LITER -> "L"
        AmountUnit.FLUID_OUNCE -> " fl oz"
        AmountUnit.TEASPOON -> " $teaspoons"
        AmountUnit.TABLESPOON -> " $tablespoons"
        AmountUnit.CUP -> " $cups"
    }
}
