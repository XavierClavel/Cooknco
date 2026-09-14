package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.UnitInfo
import com.xavierclavel.cooknco.ui.recipe.convertToPreferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That a backend older than this app cannot take the ladders away.
 *
 * `/unit` gained `system` and `isDisplayUnit` with unit preferences, and the app converts
 * off those two fields alone. A server that predates them answers with neither, and the app
 * used to let that answer replace the packaged catalogue — after which picking imperial
 * changed no amount anywhere, for as long as the app was running.
 */
class UnitCatalogueTest {

    /** `/unit` as a backend that predates unit preferences answers it. */
    private val servedWithoutLadders = UnitRepository.DEFAULT_UNITS.map {
        UnitInfo(name = it.name, type = it.type, factorToBase = it.factorToBase)
    }

    @Test
    fun `a catalogue served with no ladders keeps the packaged ones`() {
        val catalogue = UnitRepository.withLadders(servedWithoutLadders)
        val gram = catalogue.first { it.name == "GRAM" }
        assertEquals("METRIC", gram.system)
        assertTrue(gram.isDisplayUnit)
        assertEquals("IMPERIAL", catalogue.first { it.name == "POUND" }.system)
    }

    @Test
    fun `a unit on no ladder stays on none`() {
        // TABLESPOON is a tablespoon to everybody: the packaged copy puts it on neither
        // ladder, so filling the gap must not invent one for it.
        val catalogue = UnitRepository.withLadders(servedWithoutLadders)
        assertEquals(null, catalogue.first { it.name == "TABLESPOON" }.system)
    }

    @Test
    fun `a server that knows about ladders is taken exactly as it comes`() {
        // Including where it disagrees with the packaged copy — that answer is the newer one.
        val served = listOf(
            UnitInfo("GRAM", "WEIGHT", 1f, "METRIC", isDisplayUnit = true),
            UnitInfo("CUP", "VOLUME", 240f, null, isDisplayUnit = false),
        )
        assertEquals(served, UnitRepository.withLadders(served))
    }

    @Test
    fun `an empty answer leaves the packaged catalogue alone`() {
        assertEquals(UnitRepository.DEFAULT_UNITS, UnitRepository.withLadders(emptyList()))
    }

    @Test
    fun `a unit the app has never heard of is left as served`() {
        val served = servedWithoutLadders + UnitInfo("PINCH", "AMOUNT", 1f)
        val pinch = UnitRepository.withLadders(served).first { it.name == "PINCH" }
        assertEquals(null, pinch.system)
    }

    @Test
    fun `amounts convert on a catalogue that arrived without ladders`() {
        // The symptom this all exists for: 1000 g read by an imperial cook.
        val catalogue = UnitRepository.withLadders(servedWithoutLadders)
        val (amount, unit) = convertToPreferred(1000f, "GRAM", AppUnitSystem.IMPERIAL, catalogue)
        assertEquals("POUND", unit)
        assertNotNull(amount)
        assertTrue(amount > 2.2f && amount < 2.21f, "1000 g is 2.2046 lb, not $amount")
    }
}
