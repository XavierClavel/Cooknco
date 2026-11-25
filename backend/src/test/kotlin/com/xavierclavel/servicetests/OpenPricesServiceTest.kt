package main.com.xavierclavel.servicetests

import com.xavierclavel.models.Ingredient
import com.xavierclavel.models.jointables.RecipeIngredient
import com.xavierclavel.services.OpenPricesService
import com.xavierclavel.utils.searchWordsOf
import org.junit.jupiter.api.Test
import shared.enums.AmountUnit
import shared.infodto.OpenPriceInfo
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the bundled Open Prices table, and pricing recipe lines with it. The table is
 * refreshed from live data, so nothing here names a price: every expectation is worked out from
 * what the table says.
 */
class OpenPricesServiceTest {

    private val openPrices = OpenPricesService()

    private val categories = javaClass.getResourceAsStream(OpenPricesService.RESOURCE)!!
        .bufferedReader().readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .drop(1)
        .map { it.substringBefore("\t") }

    /** A category of the table priced per [per]. */
    private fun pricedPer(per: AmountUnit): OpenPriceInfo =
        categories.asSequence().mapNotNull { openPrices.find(it) }.first { it.per == per }

    private fun line(ingredient: Ingredient?, amount: Float?, unit: AmountUnit, customName: String? = null) =
        RecipeIngredient(ingredient = ingredient, amount = amount, unit = unit, customName = customName)

    @Test
    fun `every category of the bundled table reads`() {
        assertEquals(categories.size, categories.toSet().size)
        // The first read loads the whole file, and a row that does not parse fails the load
        categories.forEach { category ->
            val price = assertNotNull(openPrices.find(category), category)
            assertTrue(price.price > 0f, category)
            assertTrue(price.prices >= 5, category)
            assertTrue(price.per in setOf(AmountUnit.KILOGRAM, AmountUnit.LITER, AmountUnit.UNIT), category)
        }
        assertNull(openPrices.find("en:not-a-category"))
    }

    @Test
    fun `a search leads with the names that start with what was asked, then with the most prices`() {
        val query = "lait"
        val results = openPrices.search(query, OpenPricesService.MAX_RESULTS)
        assertTrue(results.isNotEmpty())
        val leads = results.map { price -> price.name.values.any { searchWordsOf(it).first().startsWith(query) } }
        assertEquals(leads.sortedDescending(), leads)
        val leading = results.filterIndexed { i, _ -> leads[i] }
        assertEquals(leading.sortedByDescending { it.prices }.map { it.prices }, leading.map { it.prices })
    }

    @Test
    fun `a line is priced the way its category is, through the ingredient's conversions`() {
        val perKilo = pricedPer(AmountUnit.KILOGRAM)
        val perLitre = pricedPer(AmountUnit.LITER)
        val perPiece = pricedPer(AmountUnit.UNIT)
        val gramsPerPiece = 60f
        val density = 0.92f

        fun cost(line: RecipeIngredient) = assertNotNull(openPrices.estimate(listOf(line), null)).total

        val byWeight = Ingredient(openPricesCategory = perKilo.category)
        assertEquals(250f / 1000 * perKilo.price, cost(line(byWeight, 250f, AmountUnit.GRAM)), 1e-4f)

        val spooned = Ingredient(openPricesCategory = perKilo.category, gramsPerMilliliter = density)
        assertEquals(2 * 15 * density / 1000 * perKilo.price, cost(line(spooned, 2f, AmountUnit.TABLESPOON)), 1e-4f)

        val counted = Ingredient(openPricesCategory = perKilo.category, gramsPerUnit = gramsPerPiece)
        assertEquals(3 * gramsPerPiece / 1000 * perKilo.price, cost(line(counted, 3f, AmountUnit.UNIT)), 1e-4f)

        val boughtByThePiece = Ingredient(openPricesCategory = perPiece.category, gramsPerUnit = gramsPerPiece)
        assertEquals(120f / gramsPerPiece * perPiece.price, cost(line(boughtByThePiece, 120f, AmountUnit.GRAM)), 1e-4f)

        val poured = Ingredient(openPricesCategory = perLitre.category, gramsPerMilliliter = 1f)
        assertEquals(20f * 10 / 1000 * perLitre.price, cost(line(poured, 20f, AmountUnit.CENTILITER)), 1e-4f)
    }

    @Test
    fun `what cannot be priced is left out of the total, and counted`() {
        val perKilo = pricedPer(AmountUnit.KILOGRAM)
        val perLitre = pricedPer(AmountUnit.LITER)
        val priced = line(Ingredient(openPricesCategory = perKilo.category), 400f, AmountUnit.GRAM)
        val estimate = assertNotNull(openPrices.estimate(listOf(
            priced,
            line(null, 1f, AmountUnit.TABLESPOON, customName = "fond de veau"),
            line(Ingredient(), 100f, AmountUnit.GRAM),
            // Priced per litre, measured in grams, and no density to go between the two
            line(Ingredient(openPricesCategory = perLitre.category), 100f, AmountUnit.GRAM),
            // "To taste": no amount, so not a line the estimate is short of
            line(Ingredient(openPricesCategory = perKilo.category), null, AmountUnit.NONE),
        ), 4))
        assertEquals(400f / 1000 * perKilo.price, estimate.total, 1e-4f)
        assertEquals(1, estimate.pricedIngredients)
        assertEquals(4, estimate.measuredIngredients)
    }

    @Test
    fun `a portion is the total over the yield, and there is none without one`() {
        val perKilo = pricedPer(AmountUnit.KILOGRAM)
        val lines = listOf(line(Ingredient(openPricesCategory = perKilo.category), 1000f, AmountUnit.GRAM))
        val forFour = assertNotNull(openPrices.estimate(lines, 4))
        assertEquals(forFour.total / 4, forFour.perPortion)
        assertNull(assertNotNull(openPrices.estimate(lines, null)).perPortion)
    }

    @Test
    fun `a recipe with nothing priced has no estimate rather than a free one`() {
        assertNull(openPrices.estimate(listOf(line(Ingredient(), 100f, AmountUnit.GRAM)), 2))
        assertNull(openPrices.estimate(emptyList(), 2))
    }
}
