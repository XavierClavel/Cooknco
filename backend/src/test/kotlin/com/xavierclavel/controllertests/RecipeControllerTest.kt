package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.utils.logger
import shared.dto.IngredientDTO
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.MeasurementType
import shared.enums.Sort
import shared.infodto.RecipeInfo
import shared.infodto.RecipeIngredientInfo
import shared.utils.URL.RECIPE_URL
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.xavierclavel.services.RecipeService
import org.koin.core.component.inject
import io.ebean.DB
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import io.ktor.client.request.put
import main.com.xavierclavel.utils.assertRecipeDoesNotExist
import main.com.xavierclavel.utils.assertRecipeExists
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createLike
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.createRecipeRaw
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.deleteLike
import main.com.xavierclavel.utils.deleteRecipe
import main.com.xavierclavel.utils.getMe
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.listRecipes
import main.com.xavierclavel.utils.updateRecipe
import main.com.xavierclavel.utils.updateRecipeRaw
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertFalse

class RecipeControllerTest : ApplicationTest() {
    private val recipeService: RecipeService by inject()


    val recipeDTO = RecipeDTO(
        title = "My recipe",
        description = "My description",
        steps = mutableListOf(
            "cut",
            "cook"
        )
    )

    @Test
    fun `create recipe`() = runTestAsAdmin {
        val response = client.createRecipe(recipeDTO)
        client.assertRecipeExists(response.id)
    }

    @Test
    fun `create recipe with ingredients`() = runTestAsAdmin {
        val ingredient1 = client.createIngredient()
        val ingredient2 = client.createIngredient()

        val recipeIngredient1 = RecipeDTO.RecipeIngredientDTO(
            id = ingredient1.id,
            unit = AmountUnit.GRAM,
            amount = 1f,
        )

        val recipeIngredient2 = RecipeDTO.RecipeIngredientDTO(
            id = ingredient2.id,
            unit = AmountUnit.GRAM,
            amount = 1f,
        )
        val recipeDto = RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(recipeIngredient1, recipeIngredient2)
        )
        val recipe = client.createRecipe(recipeDto)
        val response = client.getRecipe(recipe.id)
        client.assertRecipeExists(response.id)
        assertEquals(2, response.ingredients.size)
    }

    @Test
    fun `update recipe ingredients`() = runTestAsAdmin {
        val user = client.getMe()

        val ingredient1 = client.createIngredient()
        val ingredient2 = client.createIngredient()
        val ingredient3 = client.createIngredient()

        val recipeIngredient1 = RecipeDTO.RecipeIngredientDTO(
            id = ingredient1.id,
            unit = AmountUnit.GRAM,
            amount = 1f,
        )

        val recipeIngredient2 = RecipeDTO.RecipeIngredientDTO(
            id = ingredient2.id,
            unit = AmountUnit.GRAM,
            amount = 1f,
        )
        val recipeIngredient2bis = RecipeDTO.RecipeIngredientDTO(
            id = ingredient2.id,
            unit = AmountUnit.UNIT,
            amount = 2f,
        )

        val recipeIngredient3 = RecipeDTO.RecipeIngredientDTO(
            id = ingredient3.id,
            unit = AmountUnit.GRAM,
            amount = 1f,
        )

        // The default ingredient fixture declares both conversions, so every family is allowed.
        val allowedTypes = setOf(
            MeasurementType.NONE,
            MeasurementType.AMOUNT,
            MeasurementType.WEIGHT,
            MeasurementType.VOLUME,
        )

        val expected = setOf(
            RecipeIngredientInfo(
                id = ingredient2.id,
                name = "Unknown",
                unit = AmountUnit.UNIT,
                amount = 2f,
                complement = null,
                type = IngredientType.VEGETABLE,
                allowedTypes = allowedTypes,
            ),
            RecipeIngredientInfo(
                id = ingredient3.id,
                name = "Unknown",
                unit = AmountUnit.GRAM,
                amount = 1f,
                complement = null,
                type = IngredientType.VEGETABLE,
                allowedTypes = allowedTypes,
            ),

        )

        val recipeDto = RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(recipeIngredient1, recipeIngredient2)
        )
        val recipe = client.createRecipe(recipeDto)

        val recipeDTO2 = RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(recipeIngredient2bis, recipeIngredient3)
        )

        val response = client.updateRecipe(recipe.id, recipeDTO2)
        client.assertRecipeExists(response.id)
        assertEquals(2, response.ingredients.size)
        assertEquals(expected, response.ingredients.toSet())
    }

    @Test
    fun `create recipe with custom ingredients`() = runTestAsAdmin {
        val customIngredient1 = RecipeDTO.RecipeIngredientDTO(
            customName = "custom ingredient 1",
            unit = AmountUnit.GRAM,
            amount = 1f,
        )

        val customIngredient2 = RecipeDTO.RecipeIngredientDTO(
            customName = "custom ingredient 2",
            unit = AmountUnit.GRAM,
            amount = 1f,
        )
        val recipeDto = RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(customIngredient1, customIngredient2)
        )
        val recipe = client.createRecipe(recipeDto)
        val response = client.getRecipe(recipe.id)
        client.assertRecipeExists(response.id)
        assertEquals(2, response.ingredients.size)
        assertTrue(response.ingredients.all { it.isCustom })
        assertEquals(
            listOf("custom ingredient 1", "custom ingredient 2"),
            response.ingredients.map { it.name },
        )
    }

    @Test
    fun `update recipe custom ingredients`() = runTestAsAdmin {
        val customIngredient1 = RecipeDTO.RecipeIngredientDTO(
            customName = "custom ingredient 1",
            unit = AmountUnit.GRAM,
            amount = 1f,
        )

        val customIngredient2 = RecipeDTO.RecipeIngredientDTO(
            customName = "custom ingredient 2",
            unit = AmountUnit.GRAM,
            amount = 1f,
        )
        val customIngredient2bis = RecipeDTO.RecipeIngredientDTO(
            customName = "custom ingredient 2",
            unit = AmountUnit.UNIT,
            amount = 5f,
        )

        val customIngredient3 = RecipeDTO.RecipeIngredientDTO(
            customName = "custom ingredient 3",
            unit = AmountUnit.GRAM,
            amount = 1f,
        )

        val recipeDto1 = RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(customIngredient1, customIngredient2)
        )
        val recipeDto2 = RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(customIngredient2bis, customIngredient3)
        )

        val recipe = client.createRecipe(recipeDto1)
        val response = client.getRecipe(recipe.id)
        assertEquals(
            setOf("custom ingredient 1" to AmountUnit.GRAM, "custom ingredient 2" to AmountUnit.GRAM),
            response.ingredients.map { it.name to it.unit }.toSet(),
        )

        client.updateRecipe(recipe.id, recipeDto2)
        val response2 = client.getRecipe(recipe.id)
        assertEquals(
            setOf("custom ingredient 2" to AmountUnit.UNIT, "custom ingredient 3" to AmountUnit.GRAM),
            response2.ingredients.map { it.name to it.unit }.toSet(),
        )
    }

    @Test
    fun `rename a custom ingredient`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(
                customName = "flour",
                unit = AmountUnit.GRAM,
                amount = 250f,
            ))
        ))

        client.updateRecipe(recipe.id, RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(
                customName = "wheat flour",
                unit = AmountUnit.GRAM,
                amount = 250f,
            ))
        ))

        val response = client.getRecipe(recipe.id)
        assertEquals(1, response.ingredients.size)
        assertEquals("wheat flour", response.ingredients.single().name)
        assertEquals(250f, response.ingredients.single().amount)
    }

    @Test
    fun `custom ingredients sharing a name are both kept`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "sugar", unit = AmountUnit.GRAM, amount = 100f),
                RecipeDTO.RecipeIngredientDTO(customName = "sugar", unit = AmountUnit.TABLESPOON, amount = 2f),
            )
        ))

        val response = client.getRecipe(recipe.id)
        assertEquals(2, response.ingredients.size)
        assertEquals(
            listOf(AmountUnit.GRAM, AmountUnit.TABLESPOON),
            response.ingredients.map { it.unit },
        )
    }

    @Test
    fun `mix referenced and custom ingredients`() = runTestAsAdmin {
        val ingredient = client.createIngredient()

        val recipe = client.createRecipe(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(id = ingredient.id, unit = AmountUnit.GRAM, amount = 10f),
                RecipeDTO.RecipeIngredientDTO(customName = "yuzu zest", unit = AmountUnit.TEASPOON, amount = 1f),
            )
        ))

        val response = client.getRecipe(recipe.id)
        assertEquals(2, response.ingredients.size)
        assertEquals(listOf(false, true), response.ingredients.map { it.isCustom })
        assertEquals(ingredient.id, response.ingredients.first().id)
        assertEquals("yuzu zest", response.ingredients.last().name)
    }

    @Test
    fun `ingredient order is persisted`() = runTestAsAdmin {
        val ingredient1 = client.createIngredient()
        val ingredient2 = client.createIngredient()

        val rows = mutableListOf(
            RecipeDTO.RecipeIngredientDTO(id = ingredient1.id, unit = AmountUnit.GRAM, amount = 1f),
            RecipeDTO.RecipeIngredientDTO(customName = "salt", unit = AmountUnit.TEASPOON, amount = 1f),
            RecipeDTO.RecipeIngredientDTO(id = ingredient2.id, unit = AmountUnit.GRAM, amount = 2f),
        )

        val recipe = client.createRecipe(RecipeDTO(title = "My recipe", ingredients = rows))
        assertEquals(
            listOf(ingredient1.id, null, ingredient2.id),
            client.getRecipe(recipe.id).ingredients.map { it.id },
        )

        client.updateRecipe(recipe.id, RecipeDTO(
            title = "My recipe",
            ingredients = rows.reversed().toMutableList(),
        ))
        assertEquals(
            listOf(ingredient2.id, null, ingredient1.id),
            client.getRecipe(recipe.id).ingredients.map { it.id },
        )
    }

    @Test
    fun `same ingredient can appear twice in a recipe`() = runTestAsAdmin {
        val ingredient = client.createIngredient()

        val recipe = client.createRecipe(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(id = ingredient.id, unit = AmountUnit.GRAM, amount = 100f, complement = "for the dough"),
                RecipeDTO.RecipeIngredientDTO(id = ingredient.id, unit = AmountUnit.GRAM, amount = 20f, complement = "for dusting"),
            )
        ))

        val response = client.getRecipe(recipe.id)
        assertEquals(2, response.ingredients.size)
        assertEquals(listOf(100f, 20f), response.ingredients.map { it.amount })
        assertEquals(listOf("for the dough", "for dusting"), response.ingredients.map { it.complement })
    }

    @Test
    fun `an ingredient row must be either referenced or custom`() = runTestAsAdmin {
        val ingredient = client.createIngredient()

        // Neither.
        client.createRecipeRaw(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(unit = AmountUnit.GRAM, amount = 1f)),
        )).apply { assertEquals(HttpStatusCode.BadRequest, status) }

        // Both.
        client.createRecipeRaw(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(
                id = ingredient.id,
                customName = "flour",
                unit = AmountUnit.GRAM,
                amount = 1f,
            )),
        )).apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    @Test
    fun `a unit the ingredient does not allow is rejected`() = runTestAsAdmin {
        // Weight only: no gramsPerUnit and no gramsPerMilliliter.
        val ingredient = client.createIngredient(IngredientDTO(type = IngredientType.MEAT))

        client.createRecipeRaw(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(
                id = ingredient.id,
                unit = AmountUnit.CUP,
                amount = 1f,
            )),
        )).apply { assertEquals(HttpStatusCode.BadRequest, status) }

        // A custom row has no capability data, so any unit is fine.
        client.createRecipe(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(
                customName = "coconut milk",
                unit = AmountUnit.CUP,
                amount = 1f,
            )),
        ))
    }

    @Test
    fun `amount and unit must agree`() = runTestAsAdmin {
        val ingredient = client.createIngredient()

        fun row(unit: AmountUnit, amount: Float?) = RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(id = ingredient.id, unit = unit, amount = amount)),
        )

        // An amount without a unit means nothing, and vice versa.
        client.createRecipeRaw(row(AmountUnit.NONE, 5f)).apply { assertEquals(HttpStatusCode.BadRequest, status) }
        client.createRecipeRaw(row(AmountUnit.GRAM, null)).apply { assertEquals(HttpStatusCode.BadRequest, status) }
        client.createRecipeRaw(row(AmountUnit.GRAM, 0f)).apply { assertEquals(HttpStatusCode.BadRequest, status) }

        client.createRecipe(row(AmountUnit.NONE, null))
        client.createRecipe(row(AmountUnit.GRAM, 5f))
    }

    @Test
    fun `a rejected update leaves the stored ingredients untouched`() = runTestAsAdmin {
        val ingredient = client.createIngredient()
        val recipe = client.createRecipe(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(
                id = ingredient.id,
                unit = AmountUnit.GRAM,
                amount = 100f,
            )),
        ))

        // The second row is invalid, so the whole update must be refused.
        client.updateRecipeRaw(recipe.id, RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(id = ingredient.id, unit = AmountUnit.GRAM, amount = 250f),
                RecipeDTO.RecipeIngredientDTO(unit = AmountUnit.GRAM, amount = 1f),
            ),
        )).apply { assertEquals(HttpStatusCode.BadRequest, status) }

        val stored = client.getRecipe(recipe.id).ingredients.single()
        assertEquals(100f, stored.amount)
    }

    @Test
    fun `a rejected creation does not leave an orphaned recipe`() = runTestAsAdmin {
        val user = client.getMe()
        val before = client.listRecipes(user = user.id).size

        client.createRecipeRaw(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(unit = AmountUnit.GRAM, amount = 1f)),
        )).apply { assertEquals(HttpStatusCode.BadRequest, status) }

        assertEquals(before, client.listRecipes(user = user.id).size)
    }

    @Test
    fun `referencing an unknown ingredient returns NotFound`() = runTestAsAdmin {
        client.createRecipeRaw(RecipeDTO(
            title = "My recipe",
            ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(id = -1, unit = AmountUnit.GRAM, amount = 1f)),
        )).apply { assertEquals(HttpStatusCode.NotFound, status) }
    }

    @Test
    fun `create recipe with steps`() = runTestAsAdmin {
        val steps = setOf(
            "step1",
            "step2",
            )
        val recipeDto = RecipeDTO(
            title = "My recipe",
            steps = steps.toMutableList()
        )
        val response = client.createRecipe(recipeDto)
        client.assertRecipeExists(response.id)
        assertEquals(2, response.steps.size)
        assertEquals(steps, response.steps.toSet())
    }

    @Test
    fun `update recipe steps`() = runTestAsAdmin {
        val steps1 = setOf(
            "step1",
            "step2",
        )
        val recipeDto = RecipeDTO(
            title = "My recipe",
            steps = steps1.toMutableList()
        )

        val steps2 = setOf(
            "step2",
            "step3",
            "step4",
        )
        val recipeDto2 = RecipeDTO(
            title = "My recipe",
            steps = steps2.toMutableList()
        )

        val response = client.createRecipe(recipeDto)
        assertEquals(steps1, response.steps.toSet())

        val response2 = client.updateRecipe(response.id, recipeDto2)
        assertEquals(steps2, response2.steps.toSet())
    }



    @Test
    fun `get recipe`() = runTestAsAdmin {
        val recipeInfo = client.createRecipe(recipeDTO)
        val result = client.getRecipe(recipeInfo.id)
        assertTrue(result.compareToDTO(recipeDTO))
    }

    @Test
    fun `update recipe`() = runTestAsAdmin {
        val response = client.createRecipe(recipeDTO)
        val recipeDTO2 = RecipeDTO(
            title = "My better recipe",
            description = "My new description",
            steps = mutableListOf(
                "slice",
                "cool",
            )
        )
        client.updateRecipe(response.id, recipeDTO2)
        assertFalse(client.getRecipe(response.id).compareToDTO(recipeDTO))
        assertTrue(client.getRecipe(response.id).compareToDTO(recipeDTO2))
    }

    @Test
    fun `updating unexisting recipe returns Unauthorized`() = runTestAsAdmin {
        client.put("$RECIPE_URL/-1"){
            contentType(ContentType.Application.Json)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(recipeDTO)
        }.apply{
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun `delete recipe`() = runTestAsAdmin {
        val recipeInfo = client.createRecipe(recipeDTO)
        client.assertRecipeExists(recipeInfo.id)
        client.deleteRecipe(recipeInfo.id)
        client.assertRecipeDoesNotExist(recipeInfo.id)
    }

    /**
     * Deleting hides the recipe; it does not destroy it. The row and everything hanging off
     * it stay, which is what makes the deletion undoable — and the reason the flag exists at
     * all, after `DELETE /like/{id}` spent a release erasing recipes nobody meant to delete.
     */
    @Test
    fun `a deleted recipe keeps its row and its ingredients`() = runTestAsAdmin {
        val recipe = client.createRecipe(recipeDTO)
        val ingredientsBefore = countRows("recipe_ingredient", recipe.id)

        client.deleteRecipe(recipe.id)

        client.assertRecipeDoesNotExist(recipe.id)
        assertEquals(1, countRows("recipes", recipe.id), "the recipe row should still be there")
        assertEquals(ingredientsBefore, countRows("recipe_ingredient", recipe.id), "its ingredients should still be there")
    }

    /** And putting the flag back brings the recipe back, whole. */
    @Test
    fun `a deleted recipe can be restored`() = runTestAsAdmin {
        val recipe = client.createRecipe(recipeDTO)
        client.deleteRecipe(recipe.id)
        client.assertRecipeDoesNotExist(recipe.id)

        assertEquals(true, recipeService.restore(recipe.id), "restore should report having restored it")

        val restored = client.getRecipe(recipe.id)
        assertEquals(recipe.title, restored.title)
        assertEquals(recipe.ingredients.size, restored.ingredients.size)
    }

    /** Counts rows a soft delete must not have removed — raw SQL, since Ebean hides them. */
    private fun countRows(table: String, recipeId: Long): Int {
        val column = if (table == "recipes") "id" else "recipe_id"
        return DB.sqlQuery("select count(*) as c from $table where $column = :id")
            .setParameter("id", recipeId)
            .findOne()
            ?.getInteger("c") ?: 0
    }

    @Test
    fun `list recipes by owner`() = runTestAsAdmin {
        val admin = userService.getUserByUsername("admin")!!
        val user = client.createUser()
        val result = client.listRecipes(admin.id)
        assertEquals(result.count(), 0)
        client.createRecipe(recipeDTO)
        val result2 = client.listRecipes(admin.id)
        assertEquals(result2.count(), 1)
        val result3 = client.listRecipes(user.id)
        assertEquals(result3.count(), 0)
    }

    @Test
    fun `list recipes`() = runTestAsAdmin {
        val user = client.getMe()
        val result = client.listRecipes(user = user.id)
        assertEquals(0, result.count())
        client.createRecipe(recipeDTO)
        val result2 = client.listRecipes(user = user.id)
        assertEquals(1, result2.count())
    }

    @Test
    fun `sort recipes by likes`() = runTestAsAdmin {
        val user = client.getMe()

        val recipe1 = client.createRecipe()
        val recipe2 = client.createRecipe()
        client.createLike(recipe1.id)

        val result1 = client.listRecipes(user = user.id, Sort.LIKES_DESCENDING)
        assertEquals(2, result1.count())
        assertEquals(recipe1.id, result1[0].id)

        val result2 = client.listRecipes(user = user.id, Sort.LIKES_ASCENDING)
        assertEquals(2, result2.count())
        assertEquals(recipe2.id, result2[0].id)
    }

    @Test
    fun `sort recipes by date`() = runTestAsAdmin {
        val user = client.getMe()

        val recipe1 = client.createRecipe()
        val recipe2 = client.createRecipe()
        val recipe3 = client.createRecipe()

        val dateOrderAscending = setOf(recipe1.id, recipe2.id, recipe3.id)
        val dateOrderDescending = setOf(recipe3.id, recipe2.id, recipe1.id)

        val result1 = client.listRecipes(user=user.id, Sort.DATE_DESCENDING)
        assertEquals(dateOrderDescending, result1.map {it.id}.toSet())

        val result2 = client.listRecipes(user=user.id, Sort.DATE_ASCENDING)
        assertEquals(dateOrderAscending, result2.map {it.id}.toSet())
    }

    @Test
    fun `recipes with no links are deleted`() = runTestAsUser {
        val recipe = client.createRecipe(recipeDTO)
        assertDoesNotThrow {
            client.getRecipe(recipe.id)
        }
        client.deleteRecipe(recipe.id)
        client.assertRecipeDoesNotExist(recipe.id)
    }

    @Test
    fun `recipes with links are not deleted`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 {
            recipe = client.createRecipe()
            val a = client.getRecipe(recipe.id)
            logger.info {a}
        }
        recipe!!
        runAsUser2 {
            client.createLike(recipe.id)
        }
        runAsUser1 {
            client.deleteRecipe(recipe.id)
        }
        runAsUser2 {
            client.assertRecipeExists(recipe.id)
        }

    }

    @Test
    fun `recipes with links are deleted once links are removed`() = runTestAsAdmin {
        var recipe: RecipeInfo? = null
        runAsUser1 {
            recipe = client.createRecipe()
        }
        recipe!!
        runAsUser2 {
            client.createLike(recipe.id)
        }
        runAsUser1 {
            client.deleteRecipe(recipe.id)
        }
        runAsUser2 {
            client.assertRecipeExists(recipe.id)
            client.deleteLike(recipe.id)
            client.assertRecipeDoesNotExist(recipe.id)
        }
    }

    @Test
    fun `recipes tagged for deletion are not shown to recipe owner`() = runTestAsAdmin {
        var recipe: RecipeInfo? = null
        runAsUser1 {
            recipe = client.createRecipe()
        }
        recipe!!
        runAsUser2 {
            client.createLike(recipe.id)
        }
        runAsUser1 {
            client.deleteRecipe(recipe.id)
            client.assertRecipeDoesNotExist(recipe.id)
        }
        runAsUser2 {
            client.assertRecipeExists(recipe.id)
        }
    }

}