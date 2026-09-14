package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import shared.enums.AmountUnit
import shared.enums.MeasurementType
import shared.enums.UnitSystem
import shared.infodto.UnitInfo
import shared.utils.URL.UNIT_URL
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitControllerTest : ApplicationTest() {

    @Test
    fun `list units`() = runTest {
        client.get(UNIT_URL).apply {
            assertEquals(HttpStatusCode.OK, status)
            val units = Json.decodeFromString<List<UnitInfo>>(bodyAsText())

            // Clients build their whole picker from this, so every unit must be present.
            assertEquals(AmountUnit.entries.size, units.size)
            assertEquals(AmountUnit.entries.toSet(), units.map { it.name }.toSet())
        }
    }

    @Test
    fun `every unit but NONE converts to a base unit`() {
        AmountUnit.entries.filter { it != AmountUnit.NONE }.forEach {
            assertTrue(it.factorToBase > 0f, "${it.name} has no conversion factor")
            assertTrue(it.type != MeasurementType.NONE, "${it.name} has no measurement type")
        }
        assertEquals(0f, AmountUnit.NONE.factorToBase)
    }

    /**
     * Clients convert amounts onto the reader's ladder from this and nothing else, so the
     * two fields that say which ladder a unit is on have to reach them — a unit served
     * without them reads as belonging to neither and is silently never converted.
     */
    @Test
    fun `the catalog says which ladder each unit is on`() = runTest {
        client.get(UNIT_URL).apply {
            assertEquals(HttpStatusCode.OK, status)
            val units = Json.decodeFromString<List<UnitInfo>>(bodyAsText()).associateBy { it.name }

            assertEquals(UnitSystem.METRIC, units.getValue(AmountUnit.GRAM).system)
            assertEquals(UnitSystem.IMPERIAL, units.getValue(AmountUnit.POUND).system)
            assertTrue(units.getValue(AmountUnit.GRAM).isDisplayUnit)
            // Written in, never read back in: the product has always rolled mL up to L
            assertEquals(UnitSystem.METRIC, units.getValue(AmountUnit.CENTILITER).system)
            assertFalse(units.getValue(AmountUnit.CENTILITER).isDisplayUnit)
            // On neither ladder, so never converted in either direction
            assertNull(units.getValue(AmountUnit.TABLESPOON).system)
            assertNull(units.getValue(AmountUnit.UNIT).system)
        }
    }

    @Test
    fun `conversion factors are expressed in grams and millilitres`() {
        assertEquals(1000f, AmountUnit.KILOGRAM.toBase(1f))
        assertEquals(1000f, AmountUnit.LITER.toBase(1f))
        assertEquals(150f, AmountUnit.MILLILITERS.toBase(150f))
        assertEquals(30f, AmountUnit.TABLESPOON.toBase(2f))
    }
}
