package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.models.query.QPdfTemplate
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.containers.GotenbergTestContainer
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.exportRecipe
import main.com.xavierclavel.utils.listPdfTemplates
import main.com.xavierclavel.utils.listPdfTemplatesRaw
import main.com.xavierclavel.utils.pdfTemplate
import main.com.xavierclavel.utils.previewPdfTemplate
import main.com.xavierclavel.utils.previewPdfTemplateRaw
import main.com.xavierclavel.utils.readPdfText
import main.com.xavierclavel.utils.restorePdfTemplateRaw
import main.com.xavierclavel.utils.savePdfTemplateRaw
import org.junit.jupiter.api.Test
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.Locale
import shared.enums.PdfDocumentKind
import shared.enums.PdfVariable
import shared.utils.URL.ADMIN_URL
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The layouts the app prints its documents from.
 *
 * They work like the mail wordings: what an operator saves is an override, the copy
 * packaged in the jar is the floor, and restoring one is a delete. What is worth pinning
 * down past that is the part a mail has no equivalent of — a saved layout has to actually
 * reach the export, a broken one must never be saved at all, and a preview has to come back
 * as the printed document rather than as the markup behind it.
 */
class AdminPdfTemplateControllerTest : ApplicationTest() {

    private val recipeKey = PdfDocumentKind.RECIPE.key

    private val recipe = RecipeDTO(
        title = "Gâteau au chocolat",
        description = "A very rich cake",
        ingredients = mutableListOf(
            RecipeDTO.RecipeIngredientDTO(customName = "flour", unit = AmountUnit.GRAM, amount = 250f),
        ),
        steps = mutableListOf("Mix everything"),
    )

    /** Deliberately nothing like the packaged one, so a sheet printed from it is unmistakable. */
    private fun layout(marker: String) = """
        <meta charset="utf-8">
        <h1>$marker {{${PdfVariable.TITLE}}}</h1>
        {{#${PdfVariable.HAS_STEPS}}}<ol>{{#${PdfVariable.STEPS}}}<li>{{text}}</li>{{/${PdfVariable.STEPS}}}</ol>{{/${PdfVariable.HAS_STEPS}}}
    """.trimIndent()

    // ----------------------------------------------------------- authorisation

    @Test
    fun `document templates are closed to anonymous callers`() = runTest {
        client.listPdfTemplatesRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.savePdfTemplateRaw(recipeKey, Locale.EN, layout("X"))
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.restorePdfTemplateRaw(recipeKey, Locale.EN)
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.previewPdfTemplateRaw(recipeKey, Locale.EN, layout("X"))
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `document templates are closed to regular users`() = runTestAsUser {
        client.listPdfTemplatesRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // ------------------------------------------------------------------ listing

    @Test
    fun `every kind is listed with the packaged layout until one is saved`() = runTestAsAdmin {
        val templates = client.listPdfTemplates()

        assertEquals(PdfDocumentKind.entries.size, templates.size)
        val sheet = templates.first { it.key == recipeKey }
        assertEquals(Locale.entries.size, sheet.locales.size)
        sheet.locales.forEach {
            assertFalse(it.custom, "${it.locale} is reported as saved when nothing was")
            assertNull(it.updatedAt)
            assertTrue(it.body.isNotBlank(), "${it.locale} has no packaged layout")
        }
        assertContains(sheet.variables, PdfVariable.INGREDIENTS)
    }

    /** Nothing is written until an operator saves: the packaged layout is a floor, not a seed. */
    @Test
    fun `listing writes no rows`() = runTestAsAdmin {
        client.listPdfTemplates()
        assertEquals(0, QPdfTemplate().findCount())
    }

    // ------------------------------------------------------------------ saving

    @Test
    fun `a saved layout is what the export is printed from`() = runTestAsAdmin {
        val created = client.createRecipe(recipe)
        assertContains(readPdfText(client.exportRecipe(created.id)).lowercase(), "ingredients")

        client.savePdfTemplateRaw(recipeKey, Locale.EN, layout("MARKER")).apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        val text = readPdfText(client.exportRecipe(created.id))
        assertContains(text, "MARKER Gâteau au chocolat")
        assertContains(text, "Mix everything")
        // the packaged layout's furniture is gone, so this really is the saved one
        assertFalse(text.lowercase().contains("ingredients"))
    }

    @Test
    fun `a layout is saved per locale, leaving the other one packaged`() = runTestAsAdmin {
        val created = client.createRecipe(recipe)
        client.savePdfTemplateRaw(recipeKey, Locale.EN, layout("ENGLISH"))

        assertContains(readPdfText(client.exportRecipe(created.id, Locale.EN)), "ENGLISH")
        assertContains(readPdfText(client.exportRecipe(created.id, Locale.FR)).lowercase(), "ingrédients")

        val sheet = client.pdfTemplate(recipeKey)
        assertTrue(sheet.locales.first { it.locale == Locale.EN }.custom)
        assertNotNull(sheet.locales.first { it.locale == Locale.EN }.updatedAt)
        assertFalse(sheet.locales.first { it.locale == Locale.FR }.custom)
    }

    @Test
    fun `saving twice replaces the layout rather than adding a second row`() = runTestAsAdmin {
        client.savePdfTemplateRaw(recipeKey, Locale.EN, layout("FIRST"))
        client.savePdfTemplateRaw(recipeKey, Locale.EN, layout("SECOND"))

        assertEquals(1, QPdfTemplate().findCount())
        assertContains(client.pdfTemplate(recipeKey).locales.first { it.locale == Locale.EN }.body, "SECOND")
    }

    /**
     * The check that earns its keep: Mustache cannot compile an unclosed section, so a
     * layout saved with one would take down every export until someone noticed.
     */
    @Test
    fun `a layout that cannot compile is refused`() = runTestAsAdmin {
        client.savePdfTemplateRaw(recipeKey, Locale.EN, "<h1>{{#steps}}oops</h1>").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        assertEquals(0, QPdfTemplate().findCount())
    }

    @Test
    fun `an empty layout is refused`() = runTestAsAdmin {
        client.savePdfTemplateRaw(recipeKey, Locale.EN, "   ").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        assertEquals(0, QPdfTemplate().findCount())
    }

    @Test
    fun `a kind the app does not print is a 404`() = runTestAsAdmin {
        client.savePdfTemplateRaw("invoice", Locale.EN, layout("X")).apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun `a locale that is not one of ours is a 400`() = runTestAsAdmin {
        assertEquals(
            HttpStatusCode.BadRequest,
            client.delete("$ADMIN_URL/documents/templates/$recipeKey/klingon").status,
        )
    }

    /**
     * A section over a list that has nothing in it prints nothing — heading included.
     *
     * Worth its own test because it is the one piece of Mustache an operator has to
     * understand to write a layout that survives a sparse recipe, and because getting it
     * wrong leaves a sheet with a bare "Ingredients" over empty space.
     */
    @Test
    fun `a section with nothing to show drops out of the sheet`() = runTestAsAdmin {
        val bare = client.createRecipe(RecipeDTO(title = "Boiled water"))

        val text = readPdfText(client.exportRecipe(bare.id)).lowercase()

        assertFalse(text.contains("ingredients"), "an empty ingredient list still printed its heading")
        assertFalse(text.contains("steps"), "an empty step list still printed its heading")
        assertContains(text, "boiled water")
    }

    /**
     * A saved layout is markup an operator wrote, and it is printed by a browser sitting
     * inside the cluster. Nothing it names may be fetched — not a public URL, not a service
     * next door, not a file off the renderer's own disk.
     *
     * Asserted through the alt text, which is what a browser draws in place of a picture it
     * could not load: if any of these fetched, its alt text would be absent from the sheet.
     * The renderer is started with the deployment's own flags (`GotenbergTestContainer`),
     * so this is the deployed policy being exercised rather than a test-only one.
     */
    @Test
    fun `a layout cannot fetch anything the export did not send it`() = runTestAsAdmin {
        val created = client.createRecipe(recipe)
        client.savePdfTemplateRaw(recipeKey, Locale.EN, """
            <meta charset="utf-8">
            <h1>{{${PdfVariable.TITLE}}}</h1>
            <img src="https://example.com/tracker.png" alt="REMOTE_BLOCKED">
            <img src="http://cooknco-backend:8080/api/v1/health" alt="NEIGHBOUR_BLOCKED">
            <img src="file:///etc/passwd" alt="TRAVERSAL_BLOCKED">
        """.trimIndent()).apply { assertEquals(HttpStatusCode.OK, status) }

        val text = readPdfText(client.exportRecipe(created.id))

        // What the browser drew instead of each picture
        assertContains(text, "REMOTE_BLOCKED")
        assertContains(text, "NEIGHBOUR_BLOCKED")
        assertContains(text, "TRAVERSAL_BLOCKED")

        // ...and that it was refused rather than merely unreachable. Alt text alone would
        // also appear for a URL that simply 404'd, which would make this pass on a renderer
        // with no policy at all.
        val refusals = GotenbergTestContainer.gotenberg.logs
        listOf("example.com/tracker.png", "cooknco-backend:8080", "file:///etc/passwd").forEach {
            assertTrue(
                refusals.lineSequence().any { line -> line.contains(it) && line.contains("filtered") },
                "the renderer never reported refusing $it",
            )
        }
    }

    // --------------------------------------------------------------- restoring

    @Test
    fun `restoring puts the packaged layout back in service`() = runTestAsAdmin {
        val created = client.createRecipe(recipe)
        client.savePdfTemplateRaw(recipeKey, Locale.EN, layout("MARKER"))

        client.restorePdfTemplateRaw(recipeKey, Locale.EN).apply { assertEquals(HttpStatusCode.OK, status) }

        assertEquals(0, QPdfTemplate().findCount())
        val text = readPdfText(client.exportRecipe(created.id))
        assertFalse(text.contains("MARKER"))
        assertContains(text.lowercase(), "ingredients")
    }

    @Test
    fun `restoring a layout nobody saved is a 404`() = runTestAsAdmin {
        client.restorePdfTemplateRaw(recipeKey, Locale.EN).apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    // ----------------------------------------------------------------- preview

    /**
     * The preview answers with the PDF, not with the HTML behind it.
     *
     * That is the whole reason the sheet is printed by a browser: the print differs from
     * the screen, and a preview that stopped at the markup would hide exactly the part an
     * operator cannot check any other way.
     */
    @Test
    fun `a preview prints the draft in the editor without saving it`() = runTestAsAdmin {
        val created = client.createRecipe(recipe)

        val pdf = client.previewPdfTemplate(recipeKey, Locale.EN, layout("DRAFT"), created.id)

        assertContains(readPdfText(pdf), "DRAFT Gâteau au chocolat")
        assertEquals(0, QPdfTemplate().findCount())
        // and the export is still printed from the packaged layout
        assertFalse(readPdfText(client.exportRecipe(created.id)).contains("DRAFT"))
    }

    @Test
    fun `a preview with no recipe named falls back to the newest one`() = runTestAsAdmin {
        client.createRecipe(recipe)
        val newest = client.createRecipe(RecipeDTO(title = "Tarte tatin"))

        val pdf = client.previewPdfTemplate(recipeKey, Locale.EN, layout("DRAFT"))

        assertContains(readPdfText(pdf), "DRAFT Tarte tatin")
        assertEquals(newest.title, "Tarte tatin")
    }

    @Test
    fun `a preview of a recipe that does not exist is a 404`() = runTestAsAdmin {
        client.previewPdfTemplateRaw(recipeKey, Locale.EN, layout("DRAFT"), 404L).apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }
}
