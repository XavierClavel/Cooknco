package main.com.xavierclavel.servicetests

import com.xavierclavel.services.CiqualService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import shared.enums.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the bundled Ciqual table, which is a file rather than a table: nothing else would
 * notice a row that no longer parses until somebody tried to link to it.
 */
class CiqualServiceTest {

    private val ciqual = CiqualService()

    @Test
    fun `every food of the bundled edition reads`() {
        val codes = javaClass.getResourceAsStream("/ciqual/ciqual-${CiqualService.EDITION}.tsv")!!
            .bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1)
            .map { it.substringBefore("\t").toInt() }
        assertEquals(3484, codes.size)
        // The first read loads the whole file, and a row that does not parse fails the load
        codes.forEach { assertNotNull(ciqual.find(it), "food $it") }
        assertNull(ciqual.find(999_999))
    }

    @Test
    fun `a value is read the way Anses publishes it`() {
        assertEquals(12.8f, CiqualService.parseValue("12,8"))
        assertEquals(140f, CiqualService.parseValue("140"))
        assertEquals(0f, CiqualService.parseValue("traces"))
        // Below detection, however coarse the method: these are foods that hold none
        assertEquals(0f, CiqualService.parseValue("< 0,5"))
        assertEquals(0f, CiqualService.parseValue("< 700"))
        // Not measured, which is not the same as none
        assertNull(CiqualService.parseValue("-"))
        assertThrows<IllegalStateException> { CiqualService.parseValue("n/a") }
    }

    @Test
    fun `a food comes out in the catalogue's shape`() {
        val egg = assertNotNull(ciqual.find(22000))
        assertEquals(mapOf(Locale.FR to "Oeuf cru", Locale.EN to "Egg, raw"), egg.name)
        assertEquals(mapOf(Locale.FR to "oeufs", Locale.EN to "eggs"), egg.group)
        assertEquals(140, egg.calories)
        assertEquals(12.8f, egg.proteins)
        assertEquals(2.64f, egg.saturatedFat)
        assertEquals(3.66f + 1.65f, egg.unsaturatedFat)
        // mg, as Ciqual states it; salt in g, not sodium
        assertEquals(372f, egg.cholesterol)
        assertEquals(0.31f, egg.salt)
    }

    @Test
    fun `what Ciqual did not measure stays unknown`() {
        val spread = assertNotNull(ciqual.find(1027))
        assertNull(spread.cholesterol)
        assertEquals(0f, spread.sugars, "traces")
        assertEquals(0f, spread.fibers, "< 0,5")

        // Mono-unsaturated is published and poly is not: a sum of half would understate it
        val flakes = assertNotNull(ciqual.find(4016))
        assertNull(flakes.unsaturatedFat)
        assertEquals(3.39f, flakes.saturatedFat)
    }

    @Test
    fun `a generic name finds the average food first`() {
        val results = ciqual.search("tomate", 10)
        assertEquals(20385, results.first().code, "Tomate sans précision, crue (aliment moyen)")
        // Names that start with the word before names that merely hold it
        val leading = results.takeWhile { it.name.getValue(Locale.FR).startsWith("Tomate") }
        assertEquals(10, leading.size)
    }

    @Test
    fun `English, accents and ligatures find the same foods`() {
        assertEquals(20385, ciqual.search("Tomato", 1).single().code)
        assertEquals(ciqual.search("oeuf", 10), ciqual.search("Œuf", 10))
        assertTrue(ciqual.search("Œuf", 10).first().name.getValue(Locale.FR).startsWith("Oeuf"))
        assertEquals(ciqual.search("farine ble t55", 10), ciqual.search("Farine BLÉ T55", 10))
        assertEquals(9436, ciqual.search("farine ble t55", 10).single().code)
    }

    @Test
    fun `every word has to be found`() {
        assertEquals(listOf(20172), ciqual.search("tomate cerise", 10).map { it.code })
        // Not fuzzy: a misspelt food finds nothing rather than a neighbour
        assertTrue(ciqual.search("tomatte", 10).isEmpty())
        assertTrue(ciqual.search("  ", 10).isEmpty())
    }

    @Test
    fun `the number of results is bounded`() {
        assertEquals(CiqualService.MAX_RESULTS, ciqual.search("a", 10_000).size)
        assertEquals(1, ciqual.search("a", 0).size)
    }
}
