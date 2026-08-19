package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import shared.dto.IngredientDTO
import shared.enums.IngredientType
import shared.enums.Locale
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
import main.com.xavierclavel.utils.assertIngredientDoesNotExist
import main.com.xavierclavel.utils.assertIngredientExists
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.deleteIngredient
import main.com.xavierclavel.utils.getIngredient
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
}