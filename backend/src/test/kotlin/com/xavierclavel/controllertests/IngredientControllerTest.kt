package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import shared.dto.IngredientDTO
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.Locale
import shared.enums.MeasurementType
import shared.utils.URL.INGREDIENT_URL
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import io.ktor.client.request.put
import junit.framework.TestCase.assertTrue
import main.com.xavierclavel.utils.absorbCustomIngredient
import main.com.xavierclavel.utils.absorbCustomIngredientRaw
import main.com.xavierclavel.utils.assertIngredientDoesNotExist
import main.com.xavierclavel.utils.assertIngredientExists
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createIngredientRaw
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.deleteIngredient
import main.com.xavierclavel.utils.getCustomIngredientUsage
import main.com.xavierclavel.utils.getIngredient
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.searchIngredients
import main.com.xavierclavel.utils.updateIngredient
import kotlin.test.assertFalse

class IngredientControllerTest : ApplicationTest() {
    @Test
    fun `create ingredient`() = runTestAsAdmin {
        val ingredientDTO = IngredientDTO(
            name = mapOf(Locale.EN to "my ingredient", Locale.FR to "mon ingredient"),
            type = IngredientType.DAIRY,
            calories = 10,
        )
        val response = client.createIngredient(ingredientDTO)
        client.assertIngredientExists(response.id)
    }

    @Test
    fun `search ingredients by name`() = runTestAsAdmin {
        val tomato = client.createIngredient(IngredientDTO(name = mapOf(Locale.EN to "tomato"), type = IngredientType.VEGETABLE))
        val tomatillo = client.createIngredient(IngredientDTO(name = mapOf(Locale.EN to "tomatillo"), type = IngredientType.VEGETABLE))
        client.createIngredient(IngredientDTO(name = mapOf(Locale.EN to "zucchini"), type = IngredientType.VEGETABLE))

        // exact match ranks before fuzzy match, unrelated ingredient is excluded
        val result = client.searchIngredients("tomato")
        assertEquals(listOf(tomato.id, tomatillo.id), result.items.map { it.id })
    }

    @Test
    fun `get ingredient`() = runTestAsAdmin {
        val ingredientDTO = IngredientDTO(
            type = IngredientType.DAIRY,
            calories = 10,
        )
        val ingredientInfo = client.createIngredient(ingredientDTO)
        val result = client.getIngredient(ingredientInfo.id)
        assertTrue(result.compareToDTO(ingredientDTO))
    }

    @Test
    fun `update ingredient`() = runTestAsAdmin {
        val ingredientDTO = IngredientDTO(
            name = mapOf(Locale.EN to "my ingredient", Locale.FR to "mon ingredient"),
            type = IngredientType.DAIRY,
            calories = 10,
        )
        val response = client.createIngredient(ingredientDTO)
        val ingredientDTO2 = IngredientDTO(
            name = mapOf(Locale.EN to "my better ingredient", Locale.FR to "mon ingredient"),
            type = IngredientType.VEGETABLE,
            calories = 10,
        )
        client.updateIngredient(response.id, ingredientDTO2)
        assertFalse(client.getIngredient(response.id).compareToDTO(ingredientDTO))
        println(client.getIngredient(response.id))
        println(ingredientDTO2)
        assertTrue(client.getIngredient(response.id).compareToDTO(ingredientDTO2))
    }

    @Test
    fun `updating unexisting ingredient returns NotFound`() = runTestAsAdmin {
        val ingredientDTO = IngredientDTO(
            type = IngredientType.DAIRY,
            calories = 10,
        )
        client.put("$INGREDIENT_URL/-1"){
            contentType(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(ingredientDTO)
        }.apply{
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun `delete ingredient`() = runTestAsAdmin {
        val ingredientDTO = IngredientDTO(
            type = IngredientType.DAIRY,
            calories = 10,
        )
        val ingredientInfo = client.createIngredient(ingredientDTO)
        client.assertIngredientExists(ingredientInfo.id)
        client.deleteIngredient(ingredientInfo.id)
        client.assertIngredientDoesNotExist(ingredientInfo.id)
    }

    @Test
    fun `allowed types are derived from the conversion factors`() = runTestAsAdmin {
        val weightOnly = client.createIngredient(IngredientDTO(type = IngredientType.MEAT))
        assertEquals(setOf(MeasurementType.NONE, MeasurementType.WEIGHT), weightOnly.allowedTypes)

        val countable = client.createIngredient(IngredientDTO(
            type = IngredientType.FRUIT,
            gramsPerUnit = 120f,
        ))
        assertEquals(
            setOf(MeasurementType.NONE, MeasurementType.WEIGHT, MeasurementType.AMOUNT),
            countable.allowedTypes,
        )

        val liquid = client.createIngredient(IngredientDTO(
            type = IngredientType.BEVERAGE_INGREDIENT,
            gramsPerMilliliter = 1.03f,
        ))
        assertEquals(
            setOf(MeasurementType.NONE, MeasurementType.WEIGHT, MeasurementType.VOLUME),
            liquid.allowedTypes,
        )

        val toTaste = client.createIngredient(IngredientDTO(
            type = IngredientType.CONDIMENT,
            measurableByWeight = false,
        ))
        assertEquals(setOf(MeasurementType.NONE), toTaste.allowedTypes)
    }

    @Test
    fun `a non-positive conversion factor is rejected`() = runTestAsAdmin {
        client.createIngredientRaw(IngredientDTO(gramsPerUnit = 0f)).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        client.createIngredientRaw(IngredientDTO(gramsPerMilliliter = -1f)).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `a default unit the ingredient cannot use is rejected`() = runTestAsAdmin {
        // No gramsPerMilliliter, so volume units don't apply.
        client.createIngredientRaw(IngredientDTO(defaultUnit = AmountUnit.CUP)).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }

        val ok = client.createIngredient(IngredientDTO(
            gramsPerMilliliter = 1f,
            defaultUnit = AmountUnit.CUP,
        ))
        assertEquals(AmountUnit.CUP, ok.defaultUnit)
    }

    @Test
    fun `custom ingredient usage is aggregated ignoring case and accents`() = runTestAsAdmin {
        client.createRecipe(RecipeDTO(
            title = "recipe A",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "Yuzu", unit = AmountUnit.GRAM, amount = 5f),
                RecipeDTO.RecipeIngredientDTO(customName = "sumac", unit = AmountUnit.GRAM, amount = 5f),
            ),
        ))
        client.createRecipe(RecipeDTO(
            title = "recipe B",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "yuzú", unit = AmountUnit.GRAM, amount = 5f),
            ),
        ))

        val usage = client.getCustomIngredientUsage()
        assertEquals(2, usage.count)
        assertEquals(3, usage.items.sumOf { it.uses })
        // Grouped case- and accent-insensitively, so both yuzu spellings count as one candidate.
        assertEquals(2, usage.items.first().uses)
    }

    @Test
    fun `absorbing a custom ingredient re-points existing recipes`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(
            title = "my recipe",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "Yuzu", unit = AmountUnit.GRAM, amount = 5f),
            ),
        ))
        val other = client.createRecipe(RecipeDTO(
            title = "other recipe",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "yuzú", unit = AmountUnit.GRAM, amount = 8f),
            ),
        ))

        val ingredient = client.createIngredient(IngredientDTO(
            name = mapOf(Locale.EN to "yuzu"),
            type = IngredientType.FRUIT,
            calories = 53,
        ))

        assertEquals(2, client.absorbCustomIngredient(ingredient.id, "yuzu").convertedRows)

        listOf(recipe.id, other.id).forEach { recipeId ->
            val row = client.getRecipe(recipeId).ingredients.single()
            assertEquals(ingredient.id, row.id)
            assertFalse(row.isCustom)
            assertEquals("yuzu", row.name)
        }
        assertEquals(0, client.getCustomIngredientUsage().count)
    }

    @Test
    fun `absorbing leaves rows whose unit the ingredient does not allow as free text`() = runTestAsAdmin {
        // A free-text row accepts any unit, so absorbing blindly would create rows the write path
        // rejects — the owner could no longer save their own recipe.
        val countable = client.createRecipe(RecipeDTO(
            title = "counted",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "kombu", unit = AmountUnit.UNIT, amount = 2f),
            ),
        ))
        val weighed = client.createRecipe(RecipeDTO(
            title = "weighed",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "kombu", unit = AmountUnit.GRAM, amount = 20f),
            ),
        ))

        // Weighable, but with no grams-per-piece, so UNIT is not one of its measurements
        val ingredient = client.createIngredient(IngredientDTO(
            name = mapOf(Locale.EN to "kombu"),
            type = IngredientType.VEGETABLE,
        ))

        client.absorbCustomIngredient(ingredient.id, "kombu").apply {
            assertEquals(1, convertedRows)
            assertEquals(1, skippedRows)
        }
        assertEquals(ingredient.id, client.getRecipe(weighed.id).ingredients.single().id)
        assertTrue(client.getRecipe(countable.id).ingredients.single().isCustom)

        // The row is still offered as a candidate, so giving the ingredient the missing
        // conversion and absorbing again picks it up rather than losing the amount.
        assertEquals(1, client.getCustomIngredientUsage().items.single().uses)
        client.updateIngredient(ingredient.id, IngredientDTO(
            name = mapOf(Locale.EN to "kombu"),
            type = IngredientType.VEGETABLE,
            gramsPerUnit = 3f,
        ))
        client.absorbCustomIngredient(ingredient.id, "kombu").apply {
            assertEquals(1, convertedRows)
            assertEquals(0, skippedRows)
        }
        assertEquals(ingredient.id, client.getRecipe(countable.id).ingredients.single().id)
        assertEquals(0, client.getCustomIngredientUsage().count)
    }

    @Test
    fun `absorbing into an unknown ingredient returns NotFound`() = runTestAsAdmin {
        client.absorbCustomIngredientRaw(-1, "yuzu").apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }
}