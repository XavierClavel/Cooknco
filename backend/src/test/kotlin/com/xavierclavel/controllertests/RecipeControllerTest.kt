package main.com.xavierclavel.controllertests

import com.xavierclavel.utils.stepsOf
import com.xavierclavel.utils.texts
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
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
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
        steps = stepsOf(
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
            steps = stepsOf(steps)
        )
        val response = client.createRecipe(recipeDto)
        client.assertRecipeExists(response.id)
        assertEquals(2, response.steps.size)
        assertEquals(steps, response.steps.texts().toSet())
    }

    /**
     * The two things the steps gained when they stopped being an element collection: an
     * order that is stored rather than inferred from the order rows happened to be written
     * in, and a duration per step.
     *
     * Asserted as lists, not sets. Every other test here compares sets, which is what let the
     * old element collection's lack of a sort column go unnoticed for as long as it did.
     */
    @Test
    fun `steps keep their order and their timers across a save`() = runTestAsAdmin {
        val written = mutableListOf(
            RecipeDTO.RecipeStepDTO("Melt the butter"),
            RecipeDTO.RecipeStepDTO("Rest the dough", durationSeconds = 1800),
            RecipeDTO.RecipeStepDTO("Bake each side", durationSeconds = 300),
        )

        val created = client.createRecipe(RecipeDTO(title = "My recipe", steps = written))
        assertEquals(written.map { it.text }, created.steps.texts())
        assertEquals(listOf(null, 1800, 300), created.steps.map { it.durationSeconds })

        // Reordered and re-timed: the reply has to follow the list it was given, not the
        // order the rows were first written in.
        val rewritten = mutableListOf(
            RecipeDTO.RecipeStepDTO("Bake each side", durationSeconds = 600),
            RecipeDTO.RecipeStepDTO("Melt the butter"),
            RecipeDTO.RecipeStepDTO("Rest the dough", durationSeconds = 1800),
        )
        val updated = client.updateRecipe(created.id, RecipeDTO(title = "My recipe", steps = rewritten))
        assertEquals(rewritten.map { it.text }, updated.steps.texts())
        assertEquals(listOf(600, null, 1800), updated.steps.map { it.durationSeconds })

        // Read back rather than trusted from the write's reply: the order has to be on disk.
        val reloaded = client.getRecipe(created.id)
        assertEquals(rewritten.map { it.text }, reloaded.steps.texts())
        assertEquals(listOf(600, null, 1800), reloaded.steps.map { it.durationSeconds })
    }

    private fun butter(amount: Float? = 100f) =
        RecipeDTO.RecipeIngredientDTO(customName = "butter", unit = AmountUnit.GRAM, amount = amount)

    private fun used(index: Int, amount: Float? = null) =
        RecipeDTO.RecipeStepIngredientDTO(index = index, amount = amount)

    /**
     * A step names the ingredients it uses by position, and how much of each it takes.
     *
     * The butter is split across two steps, which is the case the whole shape exists for: a
     * link that can carry a number, rather than a set that can only say "this one too".
     */
    @Test
    fun `a step keeps the ingredients and amounts it was given`() = runTestAsAdmin {
        val created = client.createRecipe(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(
                    butter(),
                    RecipeDTO.RecipeIngredientDTO(customName = "flour", unit = AmountUnit.GRAM, amount = 200f),
                    RecipeDTO.RecipeIngredientDTO(customName = "salt", unit = AmountUnit.NONE),
                ),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO("Melt most of the butter", ingredients = listOf(used(0, 60f))),
                    RecipeDTO.RecipeStepDTO("Add the flour and salt", ingredients = listOf(used(1), used(2))),
                    RecipeDTO.RecipeStepDTO("Brush with the rest", ingredients = listOf(used(0, 40f))),
                    RecipeDTO.RecipeStepDTO("Bake"),
                ),
            )
        )

        // What the author said. The flour and the salt were named without a number, and come
        // back without one - a blank has to survive the round trip, or an editor prefilled
        // from it would write the worked-out value back and freeze it.
        val expected = listOf(
            listOf(0 to 60f),
            listOf(1 to null, 2 to null),
            listOf(0 to 40f),
            emptyList(),
        )
        fun RecipeInfo.shape() = steps.map { step -> step.ingredients.map { it.index to it.amount } }
        assertEquals(expected, created.shape())
        // Read back rather than trusted from the write's reply: the links have to be on disk.
        assertEquals(expected, client.getRecipe(created.id).shape())
    }

    /**
     * A step keeps its row across an edit, so anything hung off it by id survives one.
     *
     * Steps used to be deleted and re-inserted on every save, inherited from when they were an
     * `@ElementCollection` of strings and had no identity to keep. Editing a recipe's title
     * gave every step a new id.
     */
    @Test
    fun `a step keeps its id when the recipe is edited`() = runTestAsAdmin {
        val created = client.createRecipe(
            RecipeDTO(
                title = "My recipe",
                steps = stepsOf("melt the butter", "add the flour", "bake"),
            )
        )
        val ids = created.steps.map { it.id }
        assertTrue(ids.all { it != null }, "a saved step has an id")

        // Only the title changes; the steps come back exactly as they were sent.
        val retitled = client.updateRecipe(
            created.id,
            RecipeDTO(title = "A better name", steps = created.steps.toMutableList()),
        )
        assertEquals(ids, retitled.steps.map { it.id }, "an edit elsewhere must not rewrite the steps")

        // Rewording a step keeps its row too.
        val reworded = client.updateRecipe(
            created.id,
            RecipeDTO(
                title = "A better name",
                steps = retitled.steps
                    .mapIndexed { index, step -> if (index == 1) step.copy(text = "fold the flour in") else step }
                    .toMutableList(),
            ),
        )
        assertEquals(ids, reworded.steps.map { it.id })
        assertEquals(listOf("melt the butter", "fold the flour in", "bake"), reworded.steps.texts())
    }

    /** A step the client stops naming goes; one it names without an id is new. */
    @Test
    fun `steps are inserted, kept and deleted by what the client names`() = runTestAsAdmin {
        val created = client.createRecipe(
            RecipeDTO(title = "My recipe", steps = stepsOf("first", "second", "third")),
        )
        val (first, second, third) = created.steps

        val updated = client.updateRecipe(
            created.id,
            RecipeDTO(
                title = "My recipe",
                steps = mutableListOf(
                    // Reordered, one dropped, one brand new.
                    third,
                    RecipeDTO.RecipeStepDTO(text = "a new one"),
                    first,
                ),
            ),
        )

        assertEquals(listOf("third", "a new one", "first"), updated.steps.texts())
        assertEquals(third.id, updated.steps[0].id, "a reordered step keeps its row")
        assertEquals(first.id, updated.steps[2].id)
        assertTrue(
            updated.steps[1].id !in listOf(first.id, second.id, third.id),
            "a step named without an id is a new row",
        )
        assertTrue(second.id !in updated.steps.mapNotNull { it.id }, "the dropped step's row is gone")
        assertEquals(3, countRows("recipe_steps", created.id), "and no row was left behind")
    }

    /**
     * A blank comes back a blank.
     *
     * The server stores and returns what was stated, and nothing else — which is what lets an
     * editor prefill its boxes from the reply and save without turning "unspecified" into a
     * number. What a blank works out to is the reader's arithmetic (`StepAmountsTest` in the
     * app), over a recipe it already holds.
     */
    @Test
    fun `a blank amount survives a round trip`() = runTestAsAdmin {
        val created = client.createRecipe(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(butter()),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO("Melt 30", ingredients = listOf(used(0, 30f))),
                    RecipeDTO.RecipeStepDTO("The rest", ingredients = listOf(used(0))),
                ),
            )
        )

        fun RecipeInfo.said() = steps.map { step -> step.ingredients.map { it.amount } }
        assertEquals(listOf(listOf(30f), listOf(null)), created.said())
        assertEquals(listOf(listOf(30f), listOf(null)), client.getRecipe(created.id).said())

        // Saving back exactly what was read, which is what an editor does, leaves it alone.
        val resaved = client.updateRecipe(
            created.id,
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(butter()),
                steps = created.steps
                    .map { step ->
                        RecipeDTO.RecipeStepDTO(
                            text = step.text,
                            ingredients = step.ingredients.map { used(it.index, it.amount) },
                        )
                    }
                    .toMutableList(),
            )
        )
        assertEquals(listOf(listOf(30f), listOf(null)), resaved.said())
    }

    /**
     * Only what cannot be true is refused: spending more than the line has, or spelling every
     * share out and still missing the total. A gap is not a disagreement.
     */
    @Test
    fun `step amounts that cannot be true are refused`() = runTestAsAdmin {
        // Overspent: 60 + 60 of 100.
        client.createRecipeRaw(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(butter()),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO("Melt some", ingredients = listOf(used(0, 60f))),
                    RecipeDTO.RecipeStepDTO("Brush with some", ingredients = listOf(used(0, 60f))),
                ),
            )
        ).apply { assertEquals(HttpStatusCode.BadRequest, status) }

        // Short with no blank to absorb it: 30 + 30 of 100.
        client.createRecipeRaw(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(butter()),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO("Melt some", ingredients = listOf(used(0, 30f))),
                    RecipeDTO.RecipeStepDTO("Brush with some", ingredients = listOf(used(0, 30f))),
                ),
            )
        ).apply { assertEquals(HttpStatusCode.BadRequest, status) }

        // But short *with* a blank is only a gap, and the blank absorbs it.
        val ok = client.createRecipe(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(butter()),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO("Melt some", ingredients = listOf(used(0, 30f))),
                    RecipeDTO.RecipeStepDTO("The rest", ingredients = listOf(used(0))),
                ),
            )
        )
        assertEquals(
            listOf(listOf(30f), listOf(null)),
            ok.steps.map { step -> step.ingredients.map { it.amount } },
        )
    }

    /** "Salt, to taste" has no amount to divide, so a step cannot claim a share of it. */
    @Test
    fun `a step cannot put a number on an ingredient that has none`() = runTestAsAdmin {
        client.createRecipeRaw(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(
                    RecipeDTO.RecipeIngredientDTO(customName = "salt", unit = AmountUnit.NONE),
                ),
                steps = mutableListOf(RecipeDTO.RecipeStepDTO("Season", ingredients = listOf(used(0, 5f)))),
            )
        ).apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    /** A rejected split leaves nothing behind, like a rejected ingredient. */
    @Test
    fun `a recipe rejected for its step amounts is not created`() = runTestAsAdmin {
        val user = client.getMe()
        val before = client.listRecipes(user = user.id).size
        client.createRecipeRaw(
            RecipeDTO(
                title = "Nope",
                ingredients = mutableListOf(butter()),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO("a", ingredients = listOf(used(0, 80f))),
                    RecipeDTO.RecipeStepDTO("b", ingredients = listOf(used(0, 80f))),
                ),
            )
        ).apply { assertEquals(HttpStatusCode.BadRequest, status) }
        assertEquals(before, client.listRecipes(user = user.id).size)
    }

    /**
     * The one that would break.
     *
     * A save replaces both lists, and `recipe_step_ingredients` restricts deletes at both
     * ends: an ingredient row a step still points at cannot go until that step has. This
     * asserts the write order holds - steps first, then ingredients, then the links.
     */
    @Test
    fun `a recipe whose steps use ingredients can have both replaced`() = runTestAsAdmin {
        val created = client.createRecipe(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(
                    butter(),
                    RecipeDTO.RecipeIngredientDTO(customName = "flour", unit = AmountUnit.GRAM, amount = 200f),
                ),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO("Melt the butter", ingredients = listOf(used(0))),
                    RecipeDTO.RecipeStepDTO("Add the flour", ingredients = listOf(used(1))),
                ),
            )
        )

        val updated = client.updateRecipe(
            created.id,
            RecipeDTO(
                title = "My recipe",
                // Shorter, so every row the old links pointed at is deleted.
                ingredients = mutableListOf(
                    RecipeDTO.RecipeIngredientDTO(customName = "sugar", unit = AmountUnit.GRAM, amount = 50f),
                ),
                steps = mutableListOf(RecipeDTO.RecipeStepDTO("Stir the sugar", ingredients = listOf(used(0)))),
            )
        )

        assertEquals(listOf("Stir the sugar"), updated.steps.texts())
        assertEquals(listOf(listOf(0)), updated.steps.map { step -> step.ingredients.map { it.index } })
        assertEquals(listOf("sugar"), updated.ingredients.map { it.name })
    }

    /**
     * A position with no ingredient behind it is dropped rather than failing the save.
     *
     * Not through `client.createRecipe`: that helper asserts the reply matches the DTO it was
     * given (`RecipeInfo.compareToDTO`), and dropping the dead position is precisely the
     * server declining to echo what it was sent.
     */
    @Test
    fun `a step pointing past the ingredient list simply loses the link`() = runTestAsAdmin {
        val response = client.createRecipeRaw(
            RecipeDTO(
                title = "My recipe",
                ingredients = mutableListOf(butter()),
                steps = mutableListOf(RecipeDTO.RecipeStepDTO("Melt it", ingredients = listOf(used(0), used(7)))),
            )
        )
        assertEquals(HttpStatusCode.Created, response.status)
        val created = Json.decodeFromString<RecipeInfo>(response.bodyAsText())
        assertEquals(listOf(listOf(0)), created.steps.map { step -> step.ingredients.map { it.index } })
    }

    /**
     * A zero is not a timer that has run out, it is a step without one, and the server says
     * so even though no client of ours sends one — all three normalise it first.
     *
     * Deliberately not through `client.createRecipe`: that helper asserts the reply matches
     * the DTO it was given (`RecipeInfo.compareToDTO`), and this is the one field the server
     * is *meant* not to echo back unchanged.
     */
    @Test
    fun `a step timer of zero is stored as no timer`() = runTestAsAdmin {
        val response = client.createRecipeRaw(
            RecipeDTO(
                title = "My recipe",
                steps = mutableListOf(RecipeDTO.RecipeStepDTO("Stir", durationSeconds = 0)),
            )
        )
        assertEquals(HttpStatusCode.Created, response.status)
        val created = Json.decodeFromString<RecipeInfo>(response.bodyAsText())
        assertEquals(listOf("Stir"), created.steps.texts())
        assertEquals(listOf(null), created.steps.map { it.durationSeconds })
    }

    @Test
    fun `update recipe steps`() = runTestAsAdmin {
        val steps1 = setOf(
            "step1",
            "step2",
        )
        val recipeDto = RecipeDTO(
            title = "My recipe",
            steps = stepsOf(steps1)
        )

        val steps2 = setOf(
            "step2",
            "step3",
            "step4",
        )
        val recipeDto2 = RecipeDTO(
            title = "My recipe",
            steps = stepsOf(steps2)
        )

        val response = client.createRecipe(recipeDto)
        assertEquals(steps1, response.steps.texts().toSet())

        val response2 = client.updateRecipe(response.id, recipeDto2)
        assertEquals(steps2, response2.steps.texts().toSet())
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
            steps = stepsOf(
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
    fun `a deleted recipe keeps its row and everything hanging off it`() = runTestAsAdmin {
        val ingredient = client.createIngredient()
        val recipe = client.createRecipe(
            RecipeDTO(
                title = "My recipe",
                steps = stepsOf("cut", "cook"),
                ingredients = mutableListOf(
                    RecipeDTO.RecipeIngredientDTO(id = ingredient.id, unit = AmountUnit.GRAM, amount = 1f),
                ),
            )
        )
        assertEquals(1, countRows("recipe_ingredients", recipe.id), "the recipe should have started with an ingredient")
        assertEquals(2, countRows("recipe_steps", recipe.id), "and with its two steps")

        client.deleteRecipe(recipe.id)

        client.assertRecipeDoesNotExist(recipe.id)
        assertEquals(1, countRows("recipes", recipe.id), "the recipe row should still be there")
        assertEquals(1, countRows("recipe_ingredients", recipe.id), "its ingredient should still be there")
        assertEquals(2, countRows("recipe_steps", recipe.id), "and so should its steps")
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

    /**
     * Counts rows a soft delete must not have removed — raw SQL, since Ebean hides them.
     *
     * Every child of a recipe is keyed by the association now (`recipe_id`); the steps
     * joined them in 1.48, when they stopped being an element collection keyed by
     * `recipes_steps.recipes_id` and became a table of their own.
     */
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