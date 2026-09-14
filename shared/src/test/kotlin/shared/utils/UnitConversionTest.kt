package shared.utils

import shared.enums.AmountUnit
import shared.enums.MeasurementType
import shared.enums.UnitSystem
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a reader sees when a recipe was not written in their units.
 *
 * Worth testing here rather than through the API: the rule is arithmetic over a catalogue
 * that is already in hand, so it needs no server, no database and no container — and the
 * three clients that repeat it (the sheet, the web app, the mobile app) all repeat *this*,
 * so a break here is a break in all of them.
 */
class UnitConversionTest {

    private fun assertDisplays(
        amount: Float,
        unit: AmountUnit,
        system: UnitSystem,
        expectedAmount: Float,
        expectedUnit: AmountUnit,
    ) {
        val (converted, convertedUnit) = UnitConversion.displayIn(amount, unit, system)
        assertEquals(expectedUnit, convertedUnit, "$amount $unit read in $system")
        assertTrue(
            abs(converted - expectedAmount) < 0.01f,
            "$amount $unit read in $system: expected $expectedAmount, got $converted",
        )
    }

    @Test
    fun `an imperial reader weighs in pounds and ounces`() {
        assertDisplays(800f, AmountUnit.GRAM, UnitSystem.IMPERIAL, 1.7637f, AmountUnit.POUND)
        assertDisplays(100f, AmountUnit.GRAM, UnitSystem.IMPERIAL, 3.5274f, AmountUnit.OUNCE)
        assertDisplays(2f, AmountUnit.KILOGRAM, UnitSystem.IMPERIAL, 4.4092f, AmountUnit.POUND)
    }

    @Test
    fun `an imperial reader measures in cups and fluid ounces`() {
        assertDisplays(250f, AmountUnit.MILLILITERS, UnitSystem.IMPERIAL, 1.0417f, AmountUnit.CUP)
        assertDisplays(100f, AmountUnit.MILLILITERS, UnitSystem.IMPERIAL, 3.3814f, AmountUnit.FLUID_OUNCE)
        assertDisplays(25f, AmountUnit.CENTILITER, UnitSystem.IMPERIAL, 1.0417f, AmountUnit.CUP)
    }

    @Test
    fun `a metric reader reads an imperial recipe back in grams and millilitres`() {
        assertDisplays(1f, AmountUnit.CUP, UnitSystem.METRIC, 240f, AmountUnit.MILLILITERS)
        assertDisplays(1f, AmountUnit.POUND, UnitSystem.METRIC, 453.59f, AmountUnit.GRAM)
        assertDisplays(8f, AmountUnit.OUNCE, UnitSystem.METRIC, 226.8f, AmountUnit.GRAM)
    }

    @Test
    fun `an amount already on the reader's ladder still rolls up to the larger unit`() {
        assertDisplays(1_500f, AmountUnit.GRAM, UnitSystem.METRIC, 1.5f, AmountUnit.KILOGRAM)
        assertDisplays(1_000f, AmountUnit.MILLILITERS, UnitSystem.METRIC, 1f, AmountUnit.LITER)
        assertDisplays(999f, AmountUnit.GRAM, UnitSystem.METRIC, 999f, AmountUnit.GRAM)
        assertDisplays(32f, AmountUnit.OUNCE, UnitSystem.IMPERIAL, 2f, AmountUnit.POUND)
    }

    @Test
    fun `centilitres are written but never read back - a metric volume is millilitres or litres`() {
        assertDisplays(25f, AmountUnit.CENTILITER, UnitSystem.METRIC, 250f, AmountUnit.MILLILITERS)
        assertDisplays(150f, AmountUnit.CENTILITER, UnitSystem.METRIC, 1.5f, AmountUnit.LITER)
    }

    @Test
    fun `a spoon is a spoon to everybody`() {
        for (system in UnitSystem.entries) {
            assertDisplays(2f, AmountUnit.TABLESPOON, system, 2f, AmountUnit.TABLESPOON)
            assertDisplays(1f, AmountUnit.TEASPOON, system, 1f, AmountUnit.TEASPOON)
        }
    }

    @Test
    fun `a countable piece and an amount of nothing are left alone`() {
        for (system in UnitSystem.entries) {
            assertDisplays(2f, AmountUnit.UNIT, system, 2f, AmountUnit.UNIT)
            assertDisplays(0f, AmountUnit.NONE, system, 0f, AmountUnit.NONE)
        }
    }

    @Test
    fun `an amount too small for any unit on the ladder takes the smallest, not nothing`() {
        // 20 g is under an ounce, and the sheet still has to print something measurable
        assertDisplays(20f, AmountUnit.GRAM, UnitSystem.IMPERIAL, 0.7055f, AmountUnit.OUNCE)
        assertDisplays(5f, AmountUnit.MILLILITERS, UnitSystem.IMPERIAL, 0.169f, AmountUnit.FLUID_OUNCE)
    }

    @Test
    fun `no amount is written in a unit its reader has nowhere to land on`() {
        // Every unit that belongs to a ladder must have somewhere to go on *both* ladders,
        // or converting it would silently leave it as written for half the product
        AmountUnit.entries.filter { it.system != null }.forEach { unit ->
            UnitSystem.entries.forEach { system ->
                val landing = AmountUnit.entries.filter {
                    it.type == unit.type && it.system == system && it.isDisplayUnit
                }
                assertTrue(landing.isNotEmpty(), "${unit.name} has no $system unit to be read in")
            }
        }
    }

    // ------------------------------------------------- what a picker opens on

    @Test
    fun `the catalogue's own defaults all map exactly`() {
        // The four an operator actually sets, per the backoffice type defaults
        assertEquals(AmountUnit.OUNCE, UnitConversion.preferredFor(AmountUnit.GRAM, UnitSystem.IMPERIAL))
        assertEquals(AmountUnit.FLUID_OUNCE, UnitConversion.preferredFor(AmountUnit.MILLILITERS, UnitSystem.IMPERIAL))
        assertEquals(AmountUnit.TABLESPOON, UnitConversion.preferredFor(AmountUnit.TABLESPOON, UnitSystem.IMPERIAL))
        assertEquals(AmountUnit.UNIT, UnitConversion.preferredFor(AmountUnit.UNIT, UnitSystem.IMPERIAL))
    }

    @Test
    fun `the rung an operator picked within a family survives the mapping`() {
        // An ingredient declared in kilograms is one bought in quantity, and stays so
        assertEquals(AmountUnit.POUND, UnitConversion.preferredFor(AmountUnit.KILOGRAM, UnitSystem.IMPERIAL))
        assertEquals(AmountUnit.GRAM, UnitConversion.preferredFor(AmountUnit.OUNCE, UnitSystem.METRIC))
        assertEquals(AmountUnit.KILOGRAM, UnitConversion.preferredFor(AmountUnit.POUND, UnitSystem.METRIC))
        assertEquals(AmountUnit.MILLILITERS, UnitConversion.preferredFor(AmountUnit.FLUID_OUNCE, UnitSystem.METRIC))
    }

    @Test
    fun `a unit already on the cook's ladder is left exactly as declared`() {
        assertEquals(AmountUnit.GRAM, UnitConversion.preferredFor(AmountUnit.GRAM, UnitSystem.METRIC))
        // Including one that is never converted *into*: an operator who wrote centilitres
        // meant centilitres, and a picker has nothing to normalise the way a printed
        // amount does
        assertEquals(AmountUnit.CENTILITER, UnitConversion.preferredFor(AmountUnit.CENTILITER, UnitSystem.METRIC))
    }

    @Test
    fun `a unit on neither ladder is never mapped, in either direction`() {
        for (system in UnitSystem.entries) {
            assertEquals(AmountUnit.TEASPOON, UnitConversion.preferredFor(AmountUnit.TEASPOON, system))
            assertEquals(AmountUnit.UNIT, UnitConversion.preferredFor(AmountUnit.UNIT, system))
            assertEquals(AmountUnit.NONE, UnitConversion.preferredFor(AmountUnit.NONE, system))
        }
    }

    @Test
    fun `a non-display unit ranks with the rung it would be shown on`() {
        // 1 cL is 10 mL, which is shown in millilitres - so it ranks with them and comes
        // out as the fluid ounce rather than skipping a rung to the cup
        assertEquals(AmountUnit.FLUID_OUNCE, UnitConversion.preferredFor(AmountUnit.CENTILITER, UnitSystem.IMPERIAL))
    }

    @Test
    fun `every unit maps onto both ladders and stays in its own family`() {
        AmountUnit.entries.forEach { unit ->
            UnitSystem.entries.forEach { system ->
                val mapped = UnitConversion.preferredFor(unit, system)
                assertEquals(unit.type, mapped.type, "${unit.name} left its family for $system")
                if (unit.system != null) {
                    assertEquals(
                        system,
                        mapped.system,
                        "${unit.name} did not reach the $system ladder",
                    )
                }
            }
        }
    }

    @Test
    fun `the editor default follows the cook, and falls back to their own weight unit`() {
        val weightOnly = setOf(MeasurementType.NONE, MeasurementType.WEIGHT)
        assertEquals(
            AmountUnit.GRAM,
            UnitCapabilities.defaultUnitFor(null, weightOnly, UnitSystem.METRIC),
        )
        assertEquals(
            AmountUnit.OUNCE,
            UnitCapabilities.defaultUnitFor(null, weightOnly, UnitSystem.IMPERIAL),
            "the hardcoded gram fallback has to follow the cook too, or nothing changes",
        )
        // A declared unit the ingredient no longer supports is dropped before mapping
        assertEquals(
            AmountUnit.OUNCE,
            UnitCapabilities.defaultUnitFor(AmountUnit.LITER, weightOnly, UnitSystem.IMPERIAL),
        )
        // And a volume-only ingredient falls through to the volume rung, not to weight
        val volumeOnly = setOf(MeasurementType.NONE, MeasurementType.VOLUME)
        assertEquals(
            AmountUnit.FLUID_OUNCE,
            UnitCapabilities.defaultUnitFor(null, volumeOnly, UnitSystem.IMPERIAL),
        )
    }

    @Test
    fun `a display unit is always on the ladder it displays for`() {
        AmountUnit.entries.filter { it.isDisplayUnit }.forEach {
            assertTrue(it.system != null, "${it.name} is a display unit of no system")
            assertTrue(it.type != MeasurementType.NONE, "${it.name} displays nothing measurable")
        }
    }
}
