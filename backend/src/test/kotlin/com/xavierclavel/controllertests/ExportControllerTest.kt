package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.exportRecipe
import main.com.xavierclavel.utils.exportRecipeRaw
import main.com.xavierclavel.utils.readPdfImages
import main.com.xavierclavel.utils.readPdfText
import main.com.xavierclavel.utils.uploadRecipeImage
import org.junit.jupiter.api.Test
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.Locale
import shared.infodto.RecipeInfo
import shared.utils.URL.EXPORT_URL
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExportControllerTest : ApplicationTest() {

    private val fullRecipe = RecipeDTO(
        title = "Gâteau au chocolat",
        description = "A very rich cake",
        yield = 8,
        preparationTime = 20,
        cookingTime = 35,
        cookingTemperature = 180,
        ingredients = mutableListOf(
            RecipeDTO.RecipeIngredientDTO(customName = "flour", unit = AmountUnit.GRAM, amount = 250f),
            RecipeDTO.RecipeIngredientDTO(customName = "milk", unit = AmountUnit.MILLILITERS, amount = 1500f),
            RecipeDTO.RecipeIngredientDTO(customName = "salt", unit = AmountUnit.TEASPOON, amount = 1f),
            RecipeDTO.RecipeIngredientDTO(customName = "eggs", unit = AmountUnit.UNIT, amount = 3f, complement = "beaten"),
        ),
        steps = mutableListOf("Mix everything", "Bake it"),
        tips = "Serve warm",
    )

    // ----------------------------------------------------------- authorisation

    @Test
    fun `export is closed to anonymous callers`() = runTest {
        var recipe: RecipeInfo? = null
        runAsAdmin { recipe = client.createRecipe() }
        client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `export is closed to regular users, including on their own recipes`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 {
            recipe = client.createRecipe()
            client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        }
        // and the recipe is still exportable by an admin, so the refusal is about the caller
        runAsAdmin { client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.OK, status) } }
    }

    // ------------------------------------------------------------------ output

    @Test
    fun `export returns a pdf named after the recipe`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)

        val response = client.exportRecipeRaw(recipe.id)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
        assertEquals(
            "attachment; filename=\"gateau-au-chocolat.pdf\"",
            response.headers[HttpHeaders.ContentDisposition],
        )
    }

    /**
     * Headings are asserted case-insensitively: the packaged layout sets them in small caps
     * with `text-transform`, and Chromium applies that to the text it writes into the PDF.
     * The casing is the layout's to change, so a test that pinned it would break on a
     * restyle that lost nothing.
     */
    @Test
    fun `exported pdf holds the whole recipe`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)

        val text = readPdfText(client.exportRecipe(recipe.id))

        assertContains(text, "Gâteau au chocolat")
        assertContains(text, "A very rich cake")
        assertContains(text, "By admin")
        assertContains(text.lowercase(), "servings")
        // As one string, because the sheet lays the four facts out in a row: a yield that
        // failed to resolve would leave "20 min" starting the row rather than following 8.
        assertContains(text, "8 20 min 35 min 180 °C")
        assertContains(text.lowercase(), "ingredients")
        assertContains(text, "250g")
        assertContains(text, "flour")
        assertContains(text.lowercase(), "steps")
        assertContains(text, "Mix everything")
        assertContains(text, "Bake it")
        assertContains(text.lowercase(), "tips")
        assertContains(text, "Serve warm")
    }

    /**
     * Roboto ligates `fl`, and the ligature glyph is what a reader would extract — so a
     * sheet printed with ligatures on cannot be searched for the word it appears to show.
     * The packaged layout turns them off; this is what says so.
     */
    @Test
    fun `words in the export are searchable rather than ligated`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)

        val text = readPdfText(client.exportRecipe(recipe.id))

        assertContains(text, "flour")
        assertFalse(text.contains("\uFB02"), "the sheet still carries an fl ligature")
    }

    @Test
    fun `exported amounts roll up to the larger unit and drop trailing zeros`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)

        val text = readPdfText(client.exportRecipe(recipe.id))

        assertContains(text, "1.5L")
        assertContains(text, "1 tsp")
        // UNIT has no symbol of its own, and a complement is parenthesised after the name
        assertContains(text, "3")
        assertContains(text, "eggs (beaten)")
    }

    @Test
    fun `export honours the requested locale`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)

        val text = readPdfText(client.exportRecipe(recipe.id, Locale.FR))

        assertContains(text.lowercase(), "ingrédients")
        assertContains(text.lowercase(), "étapes")
        assertContains(text.lowercase(), "portions")
        assertContains(text, "Par admin")
        assertContains(text, "1 c. à café")
    }

    /**
     * A title is free text, and the sheet is HTML now: a recipe named with a tag has to
     * print the tag, not apply it. Mustache escapes on the way in, and this is what holds
     * that in place.
     */
    @Test
    fun `markup in a recipe is printed rather than rendered`() = runTestAsAdmin {
        val recipe = client.createRecipe(
            RecipeDTO(title = "<u>Chocolate</u> cake", description = "<script>alert(1)</script>")
        )

        val text = readPdfText(client.exportRecipe(recipe.id))

        assertContains(text, "<u>Chocolate</u> cake")
        assertContains(text, "<script>alert(1)</script>")
    }

    @Test
    fun `a recipe with no picture is exported with the bucket default`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)
        assertEquals(0, recipe.version)

        val images = readPdfImages(client.exportRecipe(recipe.id))

        assertEquals(1, images.size)
    }

    /**
     * The one that used to fail: the generator read an unversioned filename that never
     * exists, so every export threw before it produced a page.
     *
     * Asserted on the embedded picture's bytes, because a picture the exporter cannot find
     * is quietly replaced by the bucket default and the sheet is still a valid PDF. Not on
     * its dimensions any more: the layout crops the photo to a band with `object-fit`, so
     * an upload and the default now come out of Chromium at the same size and only the
     * pixels tell them apart.
     */
    @Test
    fun `an uploaded picture is the one that ends up in the export`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)
        val withDefault = readPdfImages(client.exportRecipe(recipe.id)).single()

        client.uploadRecipeImage(recipe.id)
        val withUpload = readPdfImages(client.exportRecipe(recipe.id)).single()

        assertFalse(withUpload.contentEquals(withDefault), "the export still shows the default picture")
    }

    @Test
    fun `exporting a recipe that does not exist is a 404`() = runTestAsAdmin {
        client.exportRecipeRaw(404L).apply { assertEquals(HttpStatusCode.NotFound, status) }
    }

    @Test
    fun `a missing locale defaults to english rather than failing the request`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)

        val text = readPdfText(client.exportRecipe(recipe.id, locale = null))

        assertContains(text.lowercase(), "ingredients")
    }

    @Test
    fun `a locale that is not one of ours is a 400`() = runTestAsAdmin {
        val recipe = client.createRecipe()
        client.get("$EXPORT_URL/recipe/${recipe.id}?locale=klingon").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `a title that slugs to nothing falls back to the recipe id`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(title = "!!!"))

        client.exportRecipeRaw(recipe.id).apply {
            assertEquals(
                "attachment; filename=\"recipe-${recipe.id}.pdf\"",
                headers[HttpHeaders.ContentDisposition],
            )
        }
    }
}
