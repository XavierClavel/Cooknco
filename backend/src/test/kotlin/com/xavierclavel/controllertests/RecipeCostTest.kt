package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.services.OpenPricesService
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createIngredientRaw
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.getIngredient
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.listAdminIngredients
import main.com.xavierclavel.utils.updateIngredient
import main.com.xavierclavel.utils.updateIngredientRaw
import io.ebean.DB
import org.junit.jupiter.api.Test
import shared.dto.IngredientDTO
import shared.dto.RecipeDTO
import shared.dto.RecipeDTO.RecipeIngredientDTO
import shared.enums.AmountUnit
import shared.infodto.OpenPriceInfo
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * An ingredient priced as an Open Prices category, and what a recipe costs from those prices.
 * The arithmetic of a line is `OpenPricesServiceTest`'s; this holds what reaches the API.
 */
class RecipeCostTest : ApplicationTest() {

    private val openPrices = OpenPricesService()

    /** A category the table prices per [per], found the way the backoffice finds one. */
    private fun pricedPer(per: AmountUnit, query: String): OpenPriceInfo =
        openPrices.search(query, OpenPricesService.MAX_RESULTS).first { it.per == per }

    @Test
    fun `an ingredient is priced as the category it is linked to`() = runTestAsAdmin {
        val carrots = pricedPer(AmountUnit.KILOGRAM, "carottes")
        val ingredient = client.createIngredient(IngredientDTO(openPricesCategory = carrots.category))
        assertEquals(carrots, ingredient.price)
        assertEquals(ingredient, client.getIngredient(ingredient.id))
        assertEquals(carrots, client.listAdminIngredients().items.single { it.id == ingredient.id }.price)

        val unlinked = client.updateIngredient(ingredient.id, IngredientDTO())
        assertNull(unlinked.price)
        assertNull(client.getIngredient(ingredient.id).price)
    }

    @Test
    fun `a category the table does not list is refused`() = runTestAsAdmin {
        client.createIngredientRaw(IngredientDTO(openPricesCategory = "en:not-a-category")).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("unknown_open_prices_category", bodyAsText())
        }
    }

    @Test
    fun `a category priced per litre needs an ingredient that can be measured by volume`() = runTestAsAdmin {
        val milk = pricedPer(AmountUnit.LITER, "lait")
        client.createIngredientRaw(IngredientDTO(openPricesCategory = milk.category)).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("open_price_not_measurable", bodyAsText())
        }
        val measured = client.createIngredient(IngredientDTO(openPricesCategory = milk.category, gramsPerMilliliter = 1.03f))
        assertEquals(milk, measured.price)
    }

    @Test
    fun `a link the table has dropped survives a save, while a new link to an unknown category does not`() = runTestAsAdmin {
        val carrots = pricedPer(AmountUnit.KILOGRAM, "carottes")
        val ingredient = client.createIngredient(IngredientDTO(openPricesCategory = carrots.category))
        val dropped = "en:dropped-by-a-refresh"
        DB.sqlUpdate("update ingredients set open_prices_category = :category where id = :id")
            .setParameter("category", dropped).setParameter("id", ingredient.id).execute()

        // What the backoffice form sends back when the operator edits something else
        val saved = client.updateIngredient(ingredient.id, IngredientDTO(openPricesCategory = dropped, calories = 41))
        assertEquals(dropped, saved.openPricesCategory)
        assertNull(saved.price)
        assertEquals(dropped, client.getIngredient(ingredient.id).openPricesCategory)

        client.updateIngredientRaw(ingredient.id, IngredientDTO(openPricesCategory = "en:not-a-category")).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("unknown_open_prices_category", bodyAsText())
        }
    }

    @Test
    fun `a link that cannot convert is refused on every save, not only the one that made it`() = runTestAsAdmin {
        val milk = pricedPer(AmountUnit.LITER, "lait")
        val ingredient = client.createIngredient(IngredientDTO(openPricesCategory = milk.category, gramsPerMilliliter = 1.03f))
        client.updateIngredientRaw(ingredient.id, IngredientDTO(openPricesCategory = milk.category)).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("open_price_not_measurable", bodyAsText())
        }
        assertEquals(milk, client.getIngredient(ingredient.id).price)
    }

    @Test
    fun `a category priced per kilo prices an ingredient counted by the piece only`() = runTestAsAdmin {
        val carrots = pricedPer(AmountUnit.KILOGRAM, "carottes")
        val gramsPerPiece = 120f
        val counted = client.createIngredient(IngredientDTO(
            openPricesCategory = carrots.category,
            measurableByWeight = false,
            gramsPerUnit = gramsPerPiece,
        ))
        val pieces = 3f
        val recipe = client.createRecipe(RecipeDTO(
            title = "Carottes glacées",
            ingredients = mutableListOf(RecipeIngredientDTO(id = counted.id, amount = pieces, unit = AmountUnit.UNIT)),
        ))
        assertEquals(pieces * gramsPerPiece / 1000 * carrots.price, assertNotNull(client.getRecipe(recipe.id).cost).total, 1e-4f)
    }

    @Test
    fun `a recipe costs its priced lines at the table's prices, and says how many that is`() = runTestAsAdmin {
        val carrots = pricedPer(AmountUnit.KILOGRAM, "carottes")
        val milk = pricedPer(AmountUnit.LITER, "lait")
        val carrot = client.createIngredient(IngredientDTO(openPricesCategory = carrots.category))
        val density = 1.03f
        val milkIngredient = client.createIngredient(IngredientDTO(openPricesCategory = milk.category, gramsPerMilliliter = density))
        val portions = 4
        val recipe = client.createRecipe(RecipeDTO(
            title = "Purée de carottes",
            yield = portions,
            ingredients = mutableListOf(
                RecipeIngredientDTO(id = carrot.id, amount = 800f, unit = AmountUnit.GRAM),
                RecipeIngredientDTO(id = milkIngredient.id, amount = 25f, unit = AmountUnit.CENTILITER),
                RecipeIngredientDTO(customName = "beurre noisette", amount = 30f, unit = AmountUnit.GRAM),
                RecipeIngredientDTO(customName = "sel"),
            ),
        ))

        val cost = assertNotNull(client.getRecipe(recipe.id).cost)
        val expected = 800f / 1000 * carrots.price + 250f / 1000 * milk.price
        assertEquals(expected, cost.total, 1e-4f)
        assertEquals(cost.total / portions, cost.perPortion)
        assertEquals(2, cost.pricedIngredients)
        assertEquals(3, cost.measuredIngredients)
    }

    @Test
    fun `a recipe is repriced when its ingredient is, without being saved again`() = runTestAsAdmin {
        val carrots = pricedPer(AmountUnit.KILOGRAM, "carottes")
        val potatoes = pricedPer(AmountUnit.KILOGRAM, "pommes de terre")
        val vegetable = client.createIngredient(IngredientDTO(openPricesCategory = carrots.category))
        val recipe = client.createRecipe(RecipeDTO(
            title = "Légumes rôtis",
            ingredients = mutableListOf(RecipeIngredientDTO(id = vegetable.id, amount = 1000f, unit = AmountUnit.GRAM)),
        ))
        assertEquals(carrots.price, assertNotNull(client.getRecipe(recipe.id).cost).total, 1e-4f)

        client.updateIngredient(vegetable.id, IngredientDTO(openPricesCategory = potatoes.category))
        val repriced = assertNotNull(client.getRecipe(recipe.id).cost)
        assertEquals(potatoes.price, repriced.total, 1e-4f)
        assertNull(repriced.perPortion)
    }

    @Test
    fun `a recipe with nothing priced has no estimate`() = runTestAsAdmin {
        val unpriced = client.createIngredient(IngredientDTO())
        val recipe = client.createRecipe(RecipeDTO(
            title = "Mystère",
            yield = 2,
            ingredients = mutableListOf(RecipeIngredientDTO(id = unpriced.id, amount = 200f, unit = AmountUnit.GRAM)),
        ))
        assertNull(client.getRecipe(recipe.id).cost)
    }
}
