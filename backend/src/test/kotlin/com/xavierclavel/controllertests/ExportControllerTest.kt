package main.com.xavierclavel.controllertests

import com.xavierclavel.utils.stepsOf
import com.xavierclavel.ApplicationTest
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.xavierclavel.TestBuilderWrapper
import io.ktor.client.statement.bodyAsText
import main.com.xavierclavel.utils.addCookbookRecipe
import main.com.xavierclavel.utils.countPdfPages
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.exportCookbook
import main.com.xavierclavel.utils.exportCookbookRaw
import main.com.xavierclavel.utils.exportRecipe
import main.com.xavierclavel.utils.exportRecipeRaw
import main.com.xavierclavel.utils.getCookbookRecipes
import main.com.xavierclavel.utils.hideRecipe
import main.com.xavierclavel.utils.pageInContents
import main.com.xavierclavel.utils.readPdfImages
import main.com.xavierclavel.utils.readPdfPages
import main.com.xavierclavel.utils.readPdfText
import main.com.xavierclavel.utils.sampleCookbookDto
import main.com.xavierclavel.utils.savePdfTemplateRaw
import main.com.xavierclavel.utils.sessionToken
import main.com.xavierclavel.utils.testImageBytes
import main.com.xavierclavel.utils.updateSettings
import main.com.xavierclavel.utils.uploadRecipeImage
import org.junit.jupiter.api.Test
import shared.dto.CookbookDTO
import shared.dto.RecipeDTO
import shared.dto.UserSettingsDTO
import shared.enums.AmountUnit
import shared.enums.UnitSystem
import shared.enums.Locale
import shared.enums.PdfDocumentKind
import shared.enums.Visibility
import shared.infodto.CookbookInfo
import shared.infodto.RecipeInfo
import shared.utils.URL.EXPORT_URL
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        steps = stepsOf("Mix everything", "Bake it"),
        tips = "Serve warm",
    )

    /**
     * A grant written straight onto the row rather than through `/admin/users/{id}/premium`:
     * what is under test here is the gate, and going through the endpoint would make every
     * one of these tests fail for the endpoint's reasons as well as their own.
     */
    private fun grantPremiumForever(mail: String) {
        userService.getEntityById(userService.findByMail(mail).id).grantPremiumForever().update()
    }

    /** The same, dated — including with a date already past, which the endpoint refuses. */
    private fun grantPremiumUntil(mail: String, until: LocalDateTime) {
        userService.getEntityById(userService.findByMail(mail).id).grantPremiumUntil(until).update()
    }

    // ----------------------------------------------------------- authorisation

    @Test
    fun `export is closed to anonymous callers`() = runTest {
        var recipe: RecipeInfo? = null
        runAsAdmin { recipe = client.createRecipe() }
        client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `export is closed to an account with no subscription, including on its own recipes`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 {
            recipe = client.createRecipe()
            client.exportRecipeRaw(recipe!!.id).apply {
                assertEquals(HttpStatusCode.Forbidden, status)
                // A cause the client can act on: the caller is signed in and needs to
                // subscribe, not to log in again
                assertContains(bodyAsText(), "premium_required")
            }
        }
        // and the recipe is still exportable by an admin, so the refusal is about the caller
        runAsAdmin { client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.OK, status) } }
    }

    @Test
    fun `a subscriber exports`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val recipe = client.createRecipe(fullRecipe)
            client.exportRecipeRaw(recipe.id).apply { assertEquals(HttpStatusCode.OK, status) }
        }
    }

    @Test
    fun `a grant that has not run out yet exports`() = runTest {
        grantPremiumUntil(USER1, LocalDateTime.now().plusDays(1))
        runAsUser1 {
            val recipe = client.createRecipe(fullRecipe)
            client.exportRecipeRaw(recipe.id).apply { assertEquals(HttpStatusCode.OK, status) }
        }
    }

    /**
     * The whole point of a dated grant: it stops on its own, with nothing running to notice.
     * Written straight onto the row because the endpoint refuses a date already past — which
     * is a different rule, tested where it lives.
     */
    @Test
    fun `a grant that has run out is no grant`() = runTest {
        grantPremiumUntil(USER1, LocalDateTime.now().minusMinutes(1))
        runAsUser1 {
            val recipe = client.createRecipe(fullRecipe)
            client.exportRecipeRaw(recipe.id).apply { assertEquals(HttpStatusCode.Forbidden, status) }
        }
    }

    /**
     * A revoked grant bites on the next request rather than at the next sign-in: the gate
     * reads the row, not the session it was authenticated with — and that session lives for
     * thirty days.
     */
    @Test
    fun `a grant revoked mid-session stops the very next export`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val recipe = client.createRecipe(fullRecipe)
            client.exportRecipeRaw(recipe.id).apply { assertEquals(HttpStatusCode.OK, status) }

            userService.revokePremium(userService.findByMail(USER1).id)

            client.exportRecipeRaw(recipe.id).apply { assertEquals(HttpStatusCode.Forbidden, status) }
        }
    }

    /**
     * The app's way in. It holds a session token and no cookie jar, so the export it offers
     * reaches the same routes over `Authorization: Bearer` — see the `bearer-auth` provider
     * in `configureAuthentication`.
     *
     * Deliberately driven from a client that has never signed in, so the token is the only
     * thing that can be authenticating the call.
     */
    @Test
    fun `an admin exports with a bearer token, having no cookie to send`() = runTest {
        var recipe: RecipeInfo? = null
        var cookbook: CookbookInfo? = null
        runAsAdmin {
            recipe = client.createRecipe()
            cookbook = client.createCookbook()
        }
        val token = newNoRedirectClient().sessionToken("admin@mail.com", password)

        val app = newNoRedirectClient()
        app.exportRecipeRaw(recipe!!.id, token = token).apply { assertEquals(HttpStatusCode.OK, status) }
        app.exportCookbookRaw(cookbook!!.id, token = token).apply { assertEquals(HttpStatusCode.OK, status) }
        // and the same client with nothing to present is refused, so it is the token doing it
        app.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `an unsubscribed user's bearer token exports nothing`() = runTest {
        var recipe: RecipeInfo? = null
        runAsAdmin { recipe = client.createRecipe() }
        val token = newNoRedirectClient().sessionToken(USER1, password)

        newNoRedirectClient().exportRecipeRaw(recipe!!.id, token = token).apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    /** The app's way in again, for the caller the feature is actually sold to. */
    @Test
    fun `a subscriber exports with a bearer token`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe(fullRecipe) }
        grantPremiumForever(USER1)
        val token = newNoRedirectClient().sessionToken(USER1, password)

        newNoRedirectClient().exportRecipeRaw(recipe!!.id, token = token).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    // ------------------------------------------------------------ what it may contain

    /**
     * The constraint a subscription brought with it. An export used to read its subject
     * straight from an id because only moderators could ask; a paid feature that kept doing
     * that would sell the recipes a private account keeps to anyone who subscribed.
     */
    @Test
    fun `a subscriber cannot export a recipe they cannot see`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser2 {
            client.updateSettings(UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = false))
            recipe = client.createRecipe(fullRecipe)
        }
        grantPremiumForever(USER1)
        runAsUser1 {
            client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.Forbidden, status) }
        }
        // and the same recipe still prints for a moderator, so the refusal is about the subject
        runAsAdmin { client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.OK, status) } }
    }

    @Test
    fun `a subscriber cannot export a recipe moderation has hidden`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser2 { recipe = client.createRecipe(fullRecipe) }
        runAsAdmin { client.hideRecipe(recipe!!.id, "spam") }
        grantPremiumForever(USER1)
        runAsUser1 {
            client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.Forbidden, status) }
        }
    }

    /** What a moderator looks into is the whole of it, which is why they are not filtered. */
    @Test
    fun `an admin still exports a hidden recipe`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser2 { recipe = client.createRecipe(fullRecipe) }
        runAsAdmin {
            client.hideRecipe(recipe!!.id, "spam")
            client.exportRecipeRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.OK, status) }
        }
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

    /**
     * The sheet is printed to be handed to somebody, so what it reads in is asked for
     * rather than read off the admin printing it. Metric by default, which is what every
     * assertion above is written against.
     */
    @Test
    fun `export honours the requested units`() = runTestAsAdmin {
        val recipe = client.createRecipe(fullRecipe)

        val text = readPdfText(client.exportRecipe(recipe.id, unitSystem = UnitSystem.IMPERIAL))

        // 250 g of flour is under a pound, so it lands on the ounce rather than nowhere
        assertContains(text, "8.82oz")
        // and 1.5 L of milk is over a quarter of a gallon we do not print, so it is cups
        assertContains(text, "6.25 cups")
        // A spoon belongs to neither ladder and is left exactly as it was written
        assertContains(text, "1 tsp")
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

    // =================================================================== cookbooks

    /**
     * A cookbook holding [titles], in the order they are given — which is *not* the order
     * the book prints them in, so a test can tell the two apart.
     */
    private suspend fun TestBuilderWrapper.cookbookOf(vararg titles: String): CookbookInfo {
        val cookbook = client.createCookbook()
        titles.forEach { title ->
            val recipe = client.createRecipe(fullRecipe.copy(title = title))
            client.addCookbookRecipe(cookbook.id, recipe.id)
        }
        return cookbook
    }

    // ----------------------------------------------------------- authorisation

    @Test
    fun `cookbook export is closed to anonymous callers`() = runTest {
        var cookbook: CookbookInfo? = null
        runAsAdmin { cookbook = cookbookOf("Chocolate cake") }
        client.exportCookbookRaw(cookbook!!.id).apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    /**
     * Membership is not what opens the export — a subscription is. A member without one
     * gets the refusal that names what to do about it.
     */
    @Test
    fun `cookbook export is closed to an unsubscribed member`() = runTestAsUser {
        val cookbook = cookbookOf("Chocolate cake")
        client.exportCookbookRaw(cookbook.id).apply {
            assertEquals(HttpStatusCode.Forbidden, status)
            assertContains(bodyAsText(), "premium_required")
        }
    }

    @Test
    fun `a subscriber exports a cookbook they can open`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val cookbook = cookbookOf("Chocolate cake")
            client.exportCookbookRaw(cookbook.id).apply { assertEquals(HttpStatusCode.OK, status) }
        }
    }

    @Test
    fun `a subscriber cannot export a cookbook they cannot open`() = runTest {
        var cookbook: CookbookInfo? = null
        runAsUser2 { cookbook = client.createCookbook(CookbookDTO(title = "Privé", visibility = Visibility.PRIVATE)) }
        grantPremiumForever(USER1)
        runAsUser1 {
            client.exportCookbookRaw(cookbook!!.id).apply { assertEquals(HttpStatusCode.Forbidden, status) }
        }
    }

    /**
     * The book is filtered recipe by recipe as well as as a whole: being in somebody's
     * cookbook is one of the things that makes a recipe visible to them, and a recipe
     * moderation has hidden is past all of that.
     *
     * Printed short rather than refused — a paid feature that fails because of somebody
     * else's moderation is worse than one that prints what the subscriber may read.
     */
    @Test
    fun `a hidden recipe drops out of a subscriber's book and stays in a moderator's`() = runTest {
        var kept: RecipeInfo? = null
        var hidden: RecipeInfo? = null
        runAsUser2 {
            kept = client.createRecipe(fullRecipe.copy(title = "Almond tart"))
            hidden = client.createRecipe(fullRecipe.copy(title = "Burnt offering"))
        }
        var cookbook: CookbookInfo? = null
        runAsUser1 {
            cookbook = client.createCookbook()
            client.addCookbookRecipe(cookbook!!.id, kept!!.id)
            client.addCookbookRecipe(cookbook!!.id, hidden!!.id)
        }
        runAsAdmin { client.hideRecipe(hidden!!.id, "spam") }

        grantPremiumForever(USER1)
        runAsUser1 {
            val text = readPdfText(client.exportCookbook(cookbook!!.id))
            assertContains(text, "Almond tart")
            assertFalse("Burnt offering" in text, "a hidden recipe was printed into a subscriber's book")
        }
        runAsAdmin {
            assertContains(readPdfText(client.exportCookbook(cookbook!!.id)), "Burnt offering")
        }
    }

    // ------------------------------------------------------------------ output

    @Test
    fun `cookbook export returns a pdf named after the cookbook`() = runTestAsAdmin {
        val cookbook = client.createCookbook(CookbookDTO(title = "Gâteaux d'hiver"))

        val response = client.exportCookbookRaw(cookbook.id)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf, response.contentType())
        assertEquals(
            "attachment; filename=\"gateaux-d-hiver.pdf\"",
            response.headers[HttpHeaders.ContentDisposition],
        )
    }

    @Test
    fun `a cookbook title that slugs to nothing falls back to the cookbook id`() = runTestAsAdmin {
        val cookbook = client.createCookbook(CookbookDTO(title = "!!!"))

        client.exportCookbookRaw(cookbook.id).apply {
            assertEquals(
                "attachment; filename=\"cookbook-${cookbook.id}.pdf\"",
                headers[HttpHeaders.ContentDisposition],
            )
        }
    }

    @Test
    fun `the exported book holds its cover and every recipe in full`() = runTestAsAdmin {
        val cookbook = cookbookOf("Chocolate cake")

        val text = readPdfText(client.exportCookbook(cookbook.id))

        // the cover
        assertContains(text, sampleCookbookDto.title)
        assertContains(text, sampleCookbookDto.description)
        // Lowercased for the reason the headings above are: the cover sets its count line
        // in small caps with `text-transform`, and Chromium applies that to the text it
        // writes into the PDF.
        assertContains(text.lowercase(), "recipes: 1")
        assertContains(text.lowercase(), "contents")
        // and the recipe, printed exactly as its own sheet would be
        assertContains(text, "Chocolate cake")
        assertContains(text, "By admin")
        assertContains(text, "8 20 min 35 min 180 °C")
        assertContains(text, "250g")
        assertContains(text, "flour")
        assertContains(text, "Mix everything")
        assertContains(text, "Serve warm")
    }

    /**
     * A book is read a recipe at a time, so each one starts its own page: cover, contents,
     * then one page per recipe. Asserted on the page count because that is the one thing
     * the extracted text cannot show.
     */
    @Test
    fun `every recipe starts a page of its own`() = runTestAsAdmin {
        val cookbook = cookbookOf("Chocolate cake", "Apple pie")

        assertEquals(4, countPdfPages(client.exportCookbook(cookbook.id)))
    }

    @Test
    fun `recipes are printed in title order rather than in the order they were added`() = runTestAsAdmin {
        val cookbook = cookbookOf("Chocolate cake", "Apple pie")

        val text = readPdfText(client.exportCookbook(cookbook.id))

        assertTrue(
            text.indexOf("Apple pie") < text.indexOf("Chocolate cake"),
            "the book prints its recipes in the order they were added",
        )
    }

    /** The cover is worth printing on its own: a book starts empty and fills up. */
    @Test
    fun `an empty cookbook prints its cover and nothing else`() = runTestAsAdmin {
        val cookbook = client.createCookbook()

        val pdf = client.exportCookbook(cookbook.id)

        assertEquals(1, countPdfPages(pdf))
        val text = readPdfText(pdf)
        assertContains(text, sampleCookbookDto.title)
        assertContains(text.lowercase(), "recipes: 0")
        assertFalse(text.lowercase().contains("contents"), "the book printed an empty contents page")
    }

    @Test
    fun `cookbook export honours the requested locale`() = runTestAsAdmin {
        val cookbook = cookbookOf("Chocolate cake")

        val text = readPdfText(client.exportCookbook(cookbook.id, Locale.FR))

        assertContains(text.lowercase(), "sommaire")
        assertContains(text.lowercase(), "ingrédients")
        assertContains(text, "Par admin")
        assertContains(text, "1 c. à café")
    }

    @Test
    fun `cookbook export honours the requested units`() = runTestAsAdmin {
        val cookbook = cookbookOf("Chocolate cake")

        val text = readPdfText(client.exportCookbook(cookbook.id, unitSystem = UnitSystem.IMPERIAL))

        assertContains(text, "8.82oz")
        assertContains(text, "6.25 cups")
    }

    @Test
    fun `markup in a cookbook is printed rather than rendered`() = runTestAsAdmin {
        val cookbook = client.createCookbook(
            CookbookDTO(title = "<u>Winter</u> cakes", description = "<script>alert(1)</script>")
        )

        val text = readPdfText(client.exportCookbook(cookbook.id))

        assertContains(text, "<u>Winter</u> cakes")
        assertContains(text, "<script>alert(1)</script>")
    }

    /**
     * Each recipe's own picture, not one of them repeated: the pictures are posted under
     * per-recipe names, and a collision there would print the same photograph throughout.
     * Read back as bytes because a picture the exporter cannot find is quietly replaced by
     * the bucket default and the book is still a valid PDF.
     */
    @Test
    fun `each recipe carries its own picture into the book`() = runTestAsAdmin {
        val cookbook = cookbookOf("Apple pie", "Chocolate cake")
        val recipes = client.getCookbookRecipes(cookbook.id).sortedBy { it.title }
        client.uploadRecipeImage(recipes.first().id, testImageBytes(width = 40, height = 30))
        client.uploadRecipeImage(recipes.last().id, testImageBytes(width = 30, height = 40))

        val images = readPdfImages(client.exportCookbook(cookbook.id))

        // the cover and the two recipes, all three different from each other
        assertEquals(3, images.size)
        assertEquals(
            3,
            images.distinctBy { it.toList() }.size,
            "the book printed the same picture more than once",
        )
    }

    /**
     * Past the bound the export is refused rather than shortened — see
     * `Configuration.Pdf.maxCookbookRecipes`, which `application-test.yaml` lowers so this
     * costs four recipes rather than a hundred.
     */
    @Test
    fun `a cookbook with too many recipes is refused rather than printed short`() = runTestAsAdmin {
        val cookbook = cookbookOf("A cake", "B cake", "C cake")
        client.exportCookbookRaw(cookbook.id).apply { assertEquals(HttpStatusCode.OK, status) }

        val extra = client.createRecipe(fullRecipe.copy(title = "D cake"))
        client.addCookbookRecipe(cookbook.id, extra.id)

        client.exportCookbookRaw(cookbook.id).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertContains(bodyAsText(), "cookbook_too_large_to_export")
        }
    }

    @Test
    fun `exporting a cookbook that does not exist is a 404`() = runTestAsAdmin {
        client.exportCookbookRaw(404L).apply { assertEquals(HttpStatusCode.NotFound, status) }
    }

    // ----------------------------------------------------------- page numbers

    /** Enough ingredients and steps to run past one page. See [fullRecipe] for the short one. */
    private val longRecipe = fullRecipe.copy(
        ingredients = (1..24).map {
            RecipeDTO.RecipeIngredientDTO(customName = "ingredient $it", unit = AmountUnit.GRAM, amount = 250f)
        }.toMutableList(),
        steps = stepsOf(
            *(1..16).map { "Step $it: mix the dry ingredients in a bowl, make a well, then fold it in." }
                .toTypedArray()
        ),
    )

    /**
     * The point of printing the book twice.
     *
     * Asserted against where the recipes actually are rather than against fixed numbers:
     * what has to hold is that the contents agrees with the book, whatever the layout does
     * with the space. The middle recipe runs to two pages, so a contents counting recipes
     * instead of reading the print would be right about it and wrong about everything after.
     */
    @Test
    fun `the contents gives the page each recipe starts on`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        listOf(
            "Baba au rhum" to fullRecipe,
            "Clafoutis aux cerises" to longRecipe,
            "Tarte Tatin" to fullRecipe,
        ).forEach { (title, dto) ->
            client.addCookbookRecipe(cookbook.id, client.createRecipe(dto.copy(title = title)).id)
        }

        val pages = readPdfPages(client.exportCookbook(cookbook.id))
        val contents = pages[1]

        // the long one really does take two pages, or this proves nothing
        assertEquals(6, pages.size, "the fixture no longer produces a two-page recipe")

        listOf("Baba au rhum", "Clafoutis aux cerises", "Tarte Tatin").forEach { title ->
            // where the recipe actually starts: the first page after the contents holding
            // its title next to a byline, which no contents row has
            val started = pages.indexOfFirst { it.contains(title) && it.contains("By admin") } + 1
            assertEquals(started, pageInContents(contents, title), "the contents is wrong about $title")
        }
        // and, concretely, the recipe after the long one is not where counting would put it
        assertEquals(6, pageInContents(contents, "Tarte Tatin"))
    }

    /**
     * A contents saying "page 6" is only usable if page 6 says so, and the document cannot
     * draw that itself — Chromium implements none of the CSS page margin boxes — so the
     * folio is a `<template id="page-footer">` the layout carries and the renderer draws.
     *
     * Marked, so the assertion is about the folio and not about a digit that happens to be
     * in a recipe: an unmarked "3" is also what 35 minutes looks like to a substring match.
     */
    @Test
    fun `the layout's page footer is drawn on every page`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        repeat(2) { client.addCookbookRecipe(cookbook.id, client.createRecipe(fullRecipe.copy(title = "Cake $it")).id) }

        client.savePdfTemplateRaw(
            PdfDocumentKind.COOKBOOK.key,
            Locale.EN,
            """
            <meta charset="utf-8">
            <h1>{{title}}</h1>
            {{#recipes}}<article style="break-before: page"><h1>{{title}}</h1></article>{{/recipes}}
            <template id="page-footer"><div>FOLIO<span class="pageNumber"></span></div></template>
            """.trimIndent(),
        ).apply { assertEquals(HttpStatusCode.OK, status) }

        val pages = readPdfPages(client.exportCookbook(cookbook.id))

        assertEquals(3, pages.size)
        pages.forEachIndexed { index, page ->
            assertContains(page, "FOLIO${index + 1}", message = "page ${index + 1} carries no folio")
        }
    }

    /**
     * The numbers exist only because the contents links to the recipes: that is what makes
     * Chromium record where each one landed. A layout that drops the links is a layout that
     * cannot be numbered, and the book then prints none rather than printing guesses.
     */
    @Test
    fun `a contents that does not link to its recipes prints no page numbers`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.addCookbookRecipe(cookbook.id, client.createRecipe(fullRecipe.copy(title = "Baba au rhum")).id)

        client.savePdfTemplateRaw(
            PdfDocumentKind.COOKBOOK.key,
            Locale.EN,
            """
            <meta charset="utf-8">
            <h1>{{title}}</h1>
            <ol>{{#recipes}}<li>{{title}} {{#page}}{{page}}{{/page}}</li>{{/recipes}}</ol>
            {{#recipes}}<article style="break-before: page"><h1>{{title}}</h1></article>{{/recipes}}
            """.trimIndent(),
        ).apply { assertEquals(HttpStatusCode.OK, status) }

        val pages = readPdfPages(client.exportCookbook(cookbook.id))

        // the book is still whole, and still one page per recipe
        assertContains(pages[0], "Baba au rhum")
        assertEquals(2, pages.size)
        // but the contents row carries no number, because none could be stood behind
        assertNull(pageInContents(pages[0], "Baba au rhum"))
    }
}
