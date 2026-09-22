package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.utils.stepsOf
import io.ktor.http.HttpStatusCode
import io.ktor.client.statement.bodyAsText
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.exportRecipeAsCooklang
import main.com.xavierclavel.utils.exportRecipeAsCooklangRaw
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.importCooklang
import main.com.xavierclavel.utils.importCooklangRaw
import org.junit.jupiter.api.Test
import shared.dto.RECIPE_STEP_TEXT_MAX_LENGTH
import shared.dto.RecipeDTO
import shared.dto.UserSettingsDTO
import shared.enums.AmountUnit
import shared.enums.DishClass
import shared.infodto.RecipeInfo
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Cooklang export and import, which are built to be each other's inverse.
 *
 * Most of what is worth checking here is that property rather than either direction on its
 * own, so the round trip is what the bulk of these assert — and it is a *full* one: the file
 * is imported and then **saved through the ordinary create route**, because an import that
 * produces a recipe the editor cannot then save would pass every parsing test and be useless.
 * The save path refuses three things a `.cook` file says happily (see `CooklangService`), and
 * this is what holds those three shut.
 */
class CooklangControllerTest : ApplicationTest() {

    private val cake = RecipeDTO(
        title = "Gâteau au chocolat",
        description = "A very rich cake",
        dishClass = DishClass.DESERT,
        yield = 8,
        preparationTime = 20,
        cookingTime = 35,
        cookingTemperature = 180,
        ingredients = mutableListOf(
            RecipeDTO.RecipeIngredientDTO(customName = "flour", unit = AmountUnit.GRAM, amount = 250f),
            RecipeDTO.RecipeIngredientDTO(customName = "milk", unit = AmountUnit.MILLILITERS, amount = 150f),
            RecipeDTO.RecipeIngredientDTO(customName = "salt", unit = AmountUnit.TEASPOON, amount = 1f),
            RecipeDTO.RecipeIngredientDTO(customName = "eggs", unit = AmountUnit.UNIT, amount = 3f, complement = "beaten"),
        ),
        steps = stepsOf("Mix the flour and the milk", "Bake it"),
        tips = "Serve warm",
    )

    private fun grantPremiumForever(mail: String) {
        userService.getEntityById(userService.findByMail(mail).id).grantPremiumForever().update()
    }

    // ----------------------------------------------------------- authorisation

    @Test
    fun `the cooklang export is closed to an account with no subscription`() = runTest {
        runAsUser1 {
            val recipe = client.createRecipe(cake)
            client.exportRecipeAsCooklangRaw(recipe.id).apply {
                assertEquals(HttpStatusCode.Forbidden, status)
                assertContains(bodyAsText(), "premium_required")
            }
        }
    }

    @Test
    fun `a subscriber exports cooklang`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val recipe = client.createRecipe(cake)
            client.exportRecipeAsCooklangRaw(recipe.id).apply { assertEquals(HttpStatusCode.OK, status) }
        }
    }

    /**
     * The other half of the gate, and the reason the two are tested together: importing is
     * free on purpose, so a route that quietly inherited the export's gate would be a
     * decision nobody made.
     */
    @Test
    fun `importing needs no subscription`() = runTest {
        runAsUser1 {
            val imported = client.importCooklang(">> title: Toast\n\nToast the @bread{2}.")
            assertEquals("Toast", imported.recipe.title)
        }
    }

    @Test
    fun `importing is closed to anonymous callers`() = runTest {
        client.importCooklangRaw(">> title: Toast\n\nToast the @bread{2}.")
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    /**
     * A subscription buys a recipe out of the product, not past its visibility rules: the
     * export reads its subject through the same filtering as the rest of the API.
     */
    @Test
    fun `a subscriber cannot export somebody else's private recipe`() = runTest {
        grantPremiumForever(USER1)
        var recipe: RecipeInfo? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") { recipe = client.createRecipe(cake) }
        runAsUser1 {
            client.exportRecipeAsCooklangRaw(recipe!!.id)
                .apply { assertTrue(status == HttpStatusCode.Forbidden || status == HttpStatusCode.NotFound) }
        }
    }

    // ------------------------------------------------------------- round trip

    /**
     * The whole feature in one test: out, back in, and saved — with the saved recipe compared
     * against the one that was exported rather than against the file in between.
     */
    @Test
    fun `a recipe survives being exported and imported back`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val original = client.createRecipe(cake)
            val file = client.exportRecipeAsCooklang(original.id)

            val imported = client.importCooklang(file)
            // Saved through the ordinary route, which is what proves the import produced
            // something this product actually accepts.
            val restored = client.getRecipe(client.createRecipe(imported.recipe).id)

            assertEquals(original.title, restored.title)
            assertEquals(original.description, restored.description)
            assertEquals(original.dishClass, restored.dishClass)
            assertEquals(original.yield, restored.yield)
            assertEquals(original.preparationTime, restored.preparationTime)
            assertEquals(original.cookingTime, restored.cookingTime)
            assertEquals(original.cookingTemperature, restored.cookingTemperature)
            assertEquals(original.tips, restored.tips)
            assertEquals(original.steps.map { it.text }, restored.steps.map { it.text })

            assertEquals(
                original.ingredients.map { Triple(it.name, it.amount, it.unit) },
                restored.ingredients.map { Triple(it.name, it.amount, it.unit) },
            )
        }
    }

    /**
     * A step's countdown is a timer in the file rather than a number in a sentence, so it is
     * still a timer on the way back — which is what the reader's cook mode counts down.
     */
    @Test
    fun `a step keeps its timer across the round trip`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val withTimer = cake.copy(
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO(text = "Let it rest", durationSeconds = 1800),
                    RecipeDTO.RecipeStepDTO(text = "Bake it", durationSeconds = 2100),
                ),
            )
            val original = client.createRecipe(withTimer)
            val file = client.exportRecipeAsCooklang(original.id)
            assertContains(file, "~{30%minutes}")

            val restored = client.importCooklang(file).recipe
            assertEquals(listOf(1800, 2100), restored.steps.map { it.durationSeconds })
        }
    }

    /**
     * The per-step shares survive, and — the part that actually breaks — they still add up to
     * the row's amount, which is what `validateStepIngredients` refuses a recipe over.
     */
    @Test
    fun `the amounts a step claims survive and still add up`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val shared = RecipeDTO(
                title = "Roux",
                ingredients = mutableListOf(
                    RecipeDTO.RecipeIngredientDTO(customName = "flour", unit = AmountUnit.GRAM, amount = 500f),
                ),
                steps = mutableListOf(
                    RecipeDTO.RecipeStepDTO(
                        text = "Whisk in half the flour",
                        ingredients = listOf(RecipeDTO.RecipeStepIngredientDTO(0, 200f)),
                    ),
                    RecipeDTO.RecipeStepDTO(
                        text = "Whisk in the rest of the flour",
                        ingredients = listOf(RecipeDTO.RecipeStepIngredientDTO(0, 300f)),
                    ),
                ),
            )
            val original = client.createRecipe(shared)
            val imported = client.importCooklang(client.exportRecipeAsCooklang(original.id))

            assertEquals(1, imported.recipe.ingredients.size)
            assertEquals(500f, imported.recipe.ingredients.first().amount)
            assertEquals(
                listOf(200f, 300f),
                imported.recipe.steps.flatMap { step -> step.ingredients.map { it.amount } },
            )
            // And it saves, which is the only thing that proves the two agree.
            client.createRecipe(imported.recipe)
        }
    }

    /**
     * An ingredient no step mentions has nowhere to live in a format whose ingredient list is
     * its steps, so the export declares it on a line of its own — and that line has to come
     * back as an ingredient rather than as a step saying nothing.
     */
    @Test
    fun `an ingredient no step mentions is neither lost nor turned into a step`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val original = client.createRecipe(cake)
            val imported = client.importCooklang(client.exportRecipeAsCooklang(original.id))

            assertEquals(4, imported.recipe.ingredients.size)
            assertEquals(cake.steps.size, imported.recipe.steps.size)
            assertTrue(imported.recipe.steps.none { it.text.isBlank() })
        }
    }

    // ------------------------------------------------- files written elsewhere

    /**
     * A file as another app writes one: no metadata this product invented, units spelled out,
     * cookware, a fraction, and a comment.
     */
    @Test
    fun `a file written by another app imports`() = runTest {
        runAsUser1 {
            val source = """
                >> title: Pancakes
                >> servings: 4

                -- the batter
                Crack the @eggs{3} into a #bowl{} and whisk with @milk{250%millilitres}.
                Sift in @plain flour{125%grams} and a @salt{1/2%teaspoon}.
                Fry each side for ~{2%minutes}.
            """.trimIndent()

            val imported = client.importCooklang(source)
            assertEquals("Pancakes", imported.recipe.title)
            assertEquals(4, imported.recipe.yield)
            assertEquals(3, imported.recipe.steps.size)

            val byName = imported.ingredientNames.zip(imported.recipe.ingredients).toMap()
            assertEquals(AmountUnit.MILLILITERS, byName.entries.first { it.key == "milk" }.value.unit)
            assertEquals(250f, byName.entries.first { it.key == "milk" }.value.amount)
            assertEquals(AmountUnit.GRAM, byName.entries.first { it.key == "plain flour" }.value.unit)
            // `1/2`, which the format allows and this product stores as a number
            assertEquals(0.5f, byName.entries.first { it.key == "salt" }.value.amount)
            // A bare count is a count, not an amount with no unit — which the save path refuses
            assertEquals(AmountUnit.UNIT, byName.entries.first { it.key == "eggs" }.value.unit)

            // The comment is not wording, and the cookware is not markup left in the sentence
            assertTrue(imported.recipe.steps.none { it.text.contains("the batter") })
            assertTrue(imported.recipe.steps.none { it.text.contains("#") || it.text.contains("@") })
            assertContains(imported.recipe.steps.first().text, "bowl")

            // and all of it saves
            client.createRecipe(imported.recipe)
        }
    }

    /**
     * A unit this product has no column for keeps both halves of what was said: the number
     * stays a number, and the word it was counted in goes in the complement. "2 cloves of
     * garlic" is a working ingredient; "garlic, cloves" has lost the 2, and "2 garlic" has
     * lost what it was 2 of.
     */
    @Test
    fun `a unit this product does not know is kept beside the amount`() = runTest {
        runAsUser1 {
            val imported = client.importCooklang("Peel the @garlic{2%cloves}.")
            val garlic = imported.recipe.ingredients.single()
            // A bare count, which is what the save path needs alongside an amount
            assertEquals(AmountUnit.UNIT, garlic.unit)
            assertEquals(2f, garlic.amount)
            assertNotNull(garlic.complement)
            assertContains(garlic.complement!!, "cloves")
            client.createRecipe(imported.recipe)
        }
    }

    /**
     * A Cooklang paragraph has no length limit and a step has one, so a hand-written method
     * run into a single paragraph has to be cut rather than truncated or refused.
     */
    @Test
    fun `a paragraph longer than a step may be is split rather than cut short`() = runTest {
        runAsUser1 {
            val sentence = "Stir the pot gently and keep it moving so nothing catches on the base. "
            val paragraph = sentence.repeat(8).trim()
            val imported = client.importCooklang(paragraph)

            assertTrue(imported.recipe.steps.size > 1)
            assertTrue(imported.recipe.steps.all { it.text.length <= RECIPE_STEP_TEXT_MAX_LENGTH })
            // Reported, so the editor can say why the method arrived in more pieces than it
            // was written in.
            assertTrue(imported.stepsWereSplit)
            // Nothing was dropped on the way
            assertEquals(
                paragraph.filterNot { it.isWhitespace() },
                imported.recipe.steps.joinToString("") { it.text }.filterNot { it.isWhitespace() },
            )
            client.createRecipe(imported.recipe)
        }
    }

    /** The converse, which is what makes the flag worth showing: an ordinary file is not split. */
    @Test
    fun `a file whose steps already fit reports no split`() = runTest {
        runAsUser1 {
            val imported = client.importCooklang(">> title: Toast\n\nToast the @bread{2}.\n\nButter it.")
            assertEquals(2, imported.recipe.steps.size)
            assertFalse(imported.stepsWereSplit)
        }
    }

    @Test
    fun `a file with nothing in it is refused`() = runTest {
        runAsUser1 {
            client.importCooklangRaw("   \n\n-- nothing but a comment\n").apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertContains(bodyAsText(), "cooklang_file_empty")
            }
        }
    }
}
