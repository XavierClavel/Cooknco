package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.data.AppUnitSystem
import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.ui.i18n.Strings
import kotlin.math.roundToInt

/**
 * An amount written for the recipe's own yield, worked out for the number of portions the
 * cook picked.
 *
 * Every amount in the product is stored against the recipe's yield and scaled at the point it
 * is read — the ingredient list does it, and a step's share of an ingredient has to do it the
 * same way, or a recipe cooked for twelve would list twice the butter and then tell each step
 * to use half of it.
 *
 * A number rather than a string, unlike the rest of this file: what it is scaled *to* has a
 * unit to be chosen before it can be written down — see [amountLabel].
 */
fun scaledAmount(amount: Float?, selectedYield: Int, recipeYield: Int): Float? {
    if (amount == null) return null
    if (recipeYield <= 0) return null
    return amount * selectedYield.toFloat() / recipeYield.toFloat()
}

/**
 * An amount as a cook would type it: whole when it is whole, two decimals at most otherwise.
 *
 * Used to put a stored amount back into a text field, and to write down every amount a
 * recipe screen shows: 66.66666 grams is not a measurement anybody takes, and a whole number
 * is left whole.
 */
fun formatAmount(amount: Float): String =
    if (amount == amount.roundToInt().toFloat()) amount.roundToInt().toString()
    else ((amount * 100).roundToInt() / 100f).toString().trimEnd('0').trimEnd('.')

/**
 * The amount and unit to print, for a cook reading in [system].
 *
 * The same rule as `shared.utils.UnitConversion`, off the same catalogue: a unit belonging to
 * no ladder is left alone — a countable piece has no metric form, and a tablespoon is a
 * tablespoon to everybody — and everything else goes through its base unit and lands on the
 * largest display unit of [system] it reaches, or the smallest when it reaches none, so 20 g
 * reads "0.71 oz" rather than nothing at all.
 *
 * Landing on the largest unit reached is also what rolls 1500 g up to 1.5 kg, which is why
 * there is no separate roll-up rule anywhere in the app.
 */
fun convertToPreferred(
    amount: Float,
    unit: String,
    system: AppUnitSystem,
    units: List<UnitInfo>,
): Pair<Float, String> {
    val from = units.firstOrNull { it.name == unit }
    if (from?.system == null || amount <= 0f) return amount to unit
    val ladder = units
        .filter { it.type == from.type && it.system == system.code && it.isDisplayUnit }
        .sortedBy { it.factorToBase }
    val base = amount * from.factorToBase
    val target = ladder.lastOrNull { base >= it.factorToBase } ?: ladder.firstOrNull() ?: return amount to unit
    return base / target.factorToBase to target.name
}

/**
 * A whole line of a recipe's amount — "120 g", "1.76 lb", "2" — scaled to the portions being
 * cooked and put on the reader's ladder, in that order.
 *
 * The order is the point: the ladder has to see the number the cook will actually measure, or
 * a 200 g recipe cooked for four would read "4 × 7.05 oz" instead of "1.76 lb".
 *
 * A countable ingredient prints "2", not "2 Unit": the catalogue has to name that unit
 * because the editor's picker needs a row to show for it, and a line of ingredients does not.
 */
fun amountLabel(
    amount: Float?,
    unit: String,
    selectedYield: Int,
    recipeYield: Int,
    system: AppUnitSystem,
    units: List<UnitInfo>,
    s: Strings,
): String {
    val scaled = scaledAmount(amount, selectedYield, recipeYield)
    val (converted, convertedUnit) = scaled?.let { convertToPreferred(it, unit, system, units) } ?: (null to unit)
    val amountStr = converted?.let { formatAmount(it) }.orEmpty()
    val unitStr = if (convertedUnit == "NONE" || convertedUnit == "UNIT") "" else s.unitName(convertedUnit)
    return listOf(amountStr, unitStr).filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * The same unit on the cook's ladder, for a picker that has no amount in it yet.
 *
 * Mirrors `shared.utils.UnitConversion.preferredFor`: by rank rather than by magnitude,
 * since there is no number to size yet — the nth display unit of a family on one ladder
 * becomes the nth on the other, so grams meet an imperial cook as ounces and kilograms as
 * pounds. A unit already on their ladder, or on neither, is left exactly as declared.
 */
fun preferredUnitFor(unit: String, system: AppUnitSystem, units: List<UnitInfo>): String {
    val from = units.firstOrNull { it.name == unit } ?: return unit
    if (from.system == null || from.system == system.code) return unit
    fun ladder(system: String) = units
        .filter { it.type == from.type && it.system == system && it.isDisplayUnit }
        .sortedBy { it.factorToBase }
    val own = ladder(from.system)
    val target = ladder(system.code)
    if (own.isEmpty() || target.isEmpty()) return unit
    // A unit that is not itself a display unit takes the rung it would be shown on, so
    // centilitres rank with millilitres rather than above them
    val rank = own.indexOfLast { from.factorToBase >= it.factorToBase }.coerceAtLeast(0)
    return (target.getOrNull(rank) ?: target.last()).name
}
