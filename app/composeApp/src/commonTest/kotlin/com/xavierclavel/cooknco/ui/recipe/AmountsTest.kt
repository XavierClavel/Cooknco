package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.data.AppUnitSystem
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.ui.i18n.EnStrings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a line of a recipe reads, for a cook who does not measure the way its author did.
 *
 * The app repeats `shared.utils.UnitConversion` rather than importing it — it has its own
 * Gradle build and its own copy of the catalogue — so the same cases are checked on this
 * side. What is only tested here is the order the screen does things in: scale to the
 * portions being cooked *first*, put the result on the ladder *second*.
 */
class AmountsTest {

    private val units = UnitRepository.DEFAULT_UNITS
    private val s = EnStrings

    private fun assertConverts(
        amount: Float,
        unit: String,
        system: AppUnitSystem,
        expectedAmount: Float,
        expectedUnit: String,
    ) {
        val (converted, convertedUnit) = convertToPreferred(amount, unit, system, units)
        assertEquals(expectedUnit, convertedUnit, "$amount $unit read in $system")
        assertTrue(
            abs(converted - expectedAmount) < 0.01f,
            "$amount $unit read in $system: expected $expectedAmount, got $converted",
        )
    }

    @Test
    fun `an imperial cook weighs in pounds and ounces`() {
        assertConverts(800f, "GRAM", AppUnitSystem.IMPERIAL, 1.7637f, "POUND")
        assertConverts(100f, "GRAM", AppUnitSystem.IMPERIAL, 3.5274f, "OUNCE")
    }

    @Test
    fun `an imperial cook measures in cups and fluid ounces`() {
        assertConverts(250f, "MILLILITERS", AppUnitSystem.IMPERIAL, 1.0417f, "CUP")
        assertConverts(100f, "MILLILITERS", AppUnitSystem.IMPERIAL, 3.3814f, "FLUID_OUNCE")
    }

    @Test
    fun `a metric cook reads an imperial recipe back in grams and millilitres`() {
        assertConverts(1f, "CUP", AppUnitSystem.METRIC, 240f, "MILLILITERS")
        assertConverts(1f, "POUND", AppUnitSystem.METRIC, 453.59f, "GRAM")
    }

    @Test
    fun `an amount already on the cook's ladder still rolls up`() {
        assertConverts(1_500f, "GRAM", AppUnitSystem.METRIC, 1.5f, "KILOGRAM")
        assertConverts(25f, "CENTILITER", AppUnitSystem.METRIC, 250f, "MILLILITERS")
    }

    @Test
    fun `spoons and countable pieces are left alone on both ladders`() {
        for (system in AppUnitSystem.entries) {
            assertConverts(2f, "TABLESPOON", system, 2f, "TABLESPOON")
            assertConverts(2f, "UNIT", system, 2f, "UNIT")
            assertConverts(1f, "NONE", system, 1f, "NONE")
        }
    }

    @Test
    fun `a unit the packaged catalogue does not know is left as written`() {
        // A server that has gained a unit this build predates: nothing in an existing
        // recipe uses it, and printing the number as written beats printing nothing
        assertConverts(3f, "FURLONG", AppUnitSystem.IMPERIAL, 3f, "FURLONG")
    }

    @Test
    fun `a line is scaled to the portions cooked, then put on the ladder`() {
        // 200 g for two, cooked for four, is 400 g - and 400 g is still ounces, not pounds
        assertEquals("14.11 oz", label(200f, "GRAM", selectedYield = 4, recipeYield = 2, AppUnitSystem.IMPERIAL))
        // The same line cooked for eight crosses the pound, which it could not do if the
        // ladder had seen 200 g and the scaling had come after
        assertEquals("1.76 lb", label(200f, "GRAM", selectedYield = 8, recipeYield = 2, AppUnitSystem.IMPERIAL))
    }

    @Test
    fun `a countable ingredient prints a number and no unit`() {
        assertEquals("2", label(2f, "UNIT", 1, 1, AppUnitSystem.METRIC))
        assertEquals("2", label(2f, "UNIT", 1, 1, AppUnitSystem.IMPERIAL))
    }

    @Test
    fun `a line with no amount at all reads as nothing`() {
        assertEquals("", label(null, "NONE", 1, 1, AppUnitSystem.IMPERIAL))
        // A unit with no amount keeps printing its unit alone, as it did before there was
        // anything to convert. The server refuses to store that pair
        // (`RecipeIngredientService`), so this is only here to say the rewrite did not
        // quietly change what an old row would draw as.
        assertEquals("g", label(null, "GRAM", 1, 1, AppUnitSystem.METRIC))
    }

    @Test
    fun `a whole number keeps no decimals`() {
        assertEquals("250 mL", label(250f, "MILLILITERS", 1, 1, AppUnitSystem.METRIC))
        assertEquals("1.5 kg", label(1_500f, "GRAM", 1, 1, AppUnitSystem.METRIC))
    }

    // ------------------------------------------------- what a picker opens on

    @Test
    fun `the catalogue's defaults open the picker on the cook's own units`() {
        assertEquals("OUNCE", preferredUnitFor("GRAM", AppUnitSystem.IMPERIAL, units))
        assertEquals("FLUID_OUNCE", preferredUnitFor("MILLILITERS", AppUnitSystem.IMPERIAL, units))
        assertEquals("TABLESPOON", preferredUnitFor("TABLESPOON", AppUnitSystem.IMPERIAL, units))
        assertEquals("UNIT", preferredUnitFor("UNIT", AppUnitSystem.IMPERIAL, units))
    }

    @Test
    fun `the rung an operator picked within a family survives`() {
        assertEquals("POUND", preferredUnitFor("KILOGRAM", AppUnitSystem.IMPERIAL, units))
        assertEquals("GRAM", preferredUnitFor("OUNCE", AppUnitSystem.METRIC, units))
        // A centilitre is shown in millilitres, so it ranks with them rather than above
        assertEquals("FLUID_OUNCE", preferredUnitFor("CENTILITER", AppUnitSystem.IMPERIAL, units))
    }

    @Test
    fun `a unit already on the cook's ladder is left as the operator declared it`() {
        assertEquals("GRAM", preferredUnitFor("GRAM", AppUnitSystem.METRIC, units))
        assertEquals("CENTILITER", preferredUnitFor("CENTILITER", AppUnitSystem.METRIC, units))
    }

    @Test
    fun `a unit the packaged catalogue does not know opens the picker as declared`() {
        assertEquals("FURLONG", preferredUnitFor("FURLONG", AppUnitSystem.IMPERIAL, units))
    }

    private fun label(
        amount: Float?,
        unit: String,
        selectedYield: Int,
        recipeYield: Int,
        system: AppUnitSystem,
    ) = amountLabel(amount, unit, selectedYield, recipeYield, system, units, s)
}
