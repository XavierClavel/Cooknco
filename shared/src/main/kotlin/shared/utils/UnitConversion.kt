package shared.utils

import shared.enums.AmountUnit
import shared.enums.UnitSystem

/**
 * Rewrites a written amount into the units its reader cooks in.
 *
 * This is display only, and runs at the very end: a recipe is stored in the unit its author
 * chose, scaled to the yield the reader picked, and only then put on a ladder. Doing it in
 * that order is what keeps a 200 g recipe cooked for four reading "1.76 lb" rather than
 * "4 × 7.05 oz" — the ladder has to see the number the cook will actually measure.
 *
 * The clients repeat this rule rather than importing it — the web app in TypeScript, the
 * mobile app off its own copy of the catalogue — which is why the two facts it needs
 * ([AmountUnit.system], [AmountUnit.isDisplayUnit]) travel with the catalogue in
 * [shared.infodto.UnitInfo] instead of being a table each client restates.
 */
object UnitConversion {

    /**
     * The amount and unit to print, for a reader reading in [system].
     *
     * Unchanged whenever there is nothing to say: an amount of nothing, a countable piece, a
     * spoon — anything whose unit belongs to no ladder ([AmountUnit.system] null). Otherwise
     * the amount goes through its base unit and comes back on the largest display unit of
     * [system] it reaches, or the smallest one when it reaches none — 20 g is "0.71 oz"
     * rather than nothing at all.
     *
     * A unit already on the reader's own ladder still goes through this, which is what rolls
     * 1500 g up to 1.5 kg and 25 cL back down to 250 mL.
     */
    fun displayIn(amount: Float, unit: AmountUnit, system: UnitSystem): Pair<Float, AmountUnit> {
        if (unit.system == null || amount <= 0f) return amount to unit
        val ladder = ladderOf(unit, system)
        val base = unit.toBase(amount)
        val target = ladder.lastOrNull { base >= it.factorToBase } ?: ladder.firstOrNull() ?: return amount to unit
        return base / target.factorToBase to target
    }

    /**
     * The same unit expressed on [system]'s ladder — what an editor should preselect.
     *
     * The other half of the feature: [displayIn] is for an amount somebody already wrote,
     * this is for the empty field they are about to write one in. An imperial cook adding
     * flour should meet ounces, not grams and a unit picker to correct every time.
     *
     * There is no amount here, so the rung cannot be chosen by magnitude the way [displayIn]
     * chooses it. It goes by rank instead — the nth display unit of a family on one ladder
     * becomes the nth on the other — which is what keeps the operator's own signal: an
     * ingredient they declared in kilograms rather than grams is one bought in quantity, and
     * it comes out in pounds rather than ounces.
     *
     * A unit already on the reader's ladder is left exactly as declared, including one that
     * is not a display unit: an operator who wrote centilitres meant centilitres, and unlike
     * a printed amount there is nothing here to normalise.
     *
     * Rank is honest about magnitude only where the two ladders span the same range, and the
     * volume ones do not — metric climbs a thousandfold to the litre where imperial climbs
     * eight to the cup. So a default declared in cups reads back as litres rather than the
     * millilitres a metric cook would rather type. It is the one pairing that comes out
     * coarse, and no ingredient in the catalogue declares it: the defaults an operator
     * actually sets are grams, millilitres, spoons and pieces (see the type defaults in the
     * backoffice), all four of which map exactly.
     */
    fun preferredFor(unit: AmountUnit, system: UnitSystem): AmountUnit {
        val own = unit.system ?: return unit
        if (own == system) return unit
        val from = ladderOf(unit, own)
        val to = ladderOf(unit, system)
        if (from.isEmpty() || to.isEmpty()) return unit
        // A unit that is not itself a display unit takes the rung it would be *shown* on,
        // so centilitres rank with millilitres rather than above them
        val rank = from.indexOfLast { unit.factorToBase >= it.factorToBase }.coerceAtLeast(0)
        return to.getOrNull(rank) ?: to.last()
    }

    /** The display units of [system] that measure the same thing as [unit], smallest first. */
    private fun ladderOf(unit: AmountUnit, system: UnitSystem): List<AmountUnit> =
        AmountUnit.entries
            .filter { it.type == unit.type && it.system == system && it.isDisplayUnit }
            .sortedBy { it.factorToBase }
}
