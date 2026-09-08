package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.IngredientService
import com.xavierclavel.services.ModerationService
import com.xavierclavel.services.RecipeService
import main.com.xavierclavel.utils.FakeAppShellSource
import shared.dto.CookbookDTO
import shared.dto.IngredientDTO
import shared.dto.RecipeDTO
import shared.dto.UserSettingsDTO
import shared.enums.IngredientType
import shared.enums.Locale
import shared.enums.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test
import org.koin.test.inject
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The documents behind shared links.
 *
 * Fixtures are built through the services rather than over HTTP so that every request in a
 * test is the anonymous one a crawler makes — which is the whole point of the endpoint, and
 * what its visibility checks are written against.
 */
class LinkPreviewControllerTest : ApplicationTest() {
    private val recipeService: RecipeService by inject()
    private val cookbookService: CookbookService by inject()
    private val ingredientService: IngredientService by inject()
    private val moderationService: ModerationService by inject()

    /** Matches the `frontend.url` of application-test.yaml. */
    private val siteUrl = "http://localhost:3000"

    /** As it reaches the attribute: every og: value is HTML-escaped, the ampersand included. */
    private val defaultTitle = "Cook&amp;Co"
    private val defaultImage = "$siteUrl/og-default.png"

    // region recipes

    @Test
    fun `recipe preview carries the recipe's own title, description and image`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Tarte aux pommes", description = "A simple apple tart."),
            userService.getEntityById(owner),
        )

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertEquals("Tarte aux pommes", document.metaContent("og:title"))
        assertEquals("A simple apple tart.", document.metaContent("og:description"))
        assertEquals("article", document.metaContent("og:type"))
        assertEquals("$siteUrl/recipe/view?id=${recipe.id}", document.metaContent("og:url"))
        // No picture was uploaded, so the bucket's backoffice-managed default stands in —
        // the same URL the app itself would build (`getRecipeImageUrl`).
        assertEquals("$siteUrl/image/recipes/default.webp", document.metaContent("og:image"))
        assertEquals("Tarte aux pommes", document.metaContent("twitter:title"))
        assertEquals("summary_large_image", document.metaContent("twitter:card"))
        assertEquals("<title>Tarte aux pommes</title>", document.titleTag())
    }

    @Test
    fun `recipe preview names the uploaded picture once there is one`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(RecipeDTO(title = "Soup"), userService.getEntityById(owner))
        recipeService.getEntityById(recipe.id).increaseVersion()

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertEquals("$siteUrl/image/recipes/${recipe.id}-v1.webp", document.metaContent("og:image"))
    }

    @Test
    fun `a hidden recipe previews as the site, not as itself`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Hidden by a moderator", description = "Secret steps."),
            userService.getEntityById(owner),
        )
        moderationService.hideRecipe(recipe.id, "spam")

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertEquals(defaultTitle, document.metaContent("og:title"))
        assertEquals(defaultImage, document.metaContent("og:image"))
        assertFalse(document.contains("Hidden by a moderator"), "the hidden recipe's title leaked")
        assertFalse(document.contains("Secret steps"), "the hidden recipe's description leaked")
    }

    @Test
    fun `a recipe whose owner is not public previews as the site`() = runTest {
        val owner = setupTestUser(uniqueMail(), UserSettingsDTO(isAccountPublic = false))
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Behind a private account"),
            userService.getEntityById(owner),
        )

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertEquals(defaultTitle, document.metaContent("og:title"))
        assertFalse(document.contains("Behind a private account"), "the private recipe's title leaked")
    }

    // endregion

    // region members

    @Test
    fun `member preview carries the username and bio`() = runTest {
        val id = setupTestUser(uniqueMail())
        val user = userService.getEntityById(id).apply { bio = "Cooks every Sunday." }.also { it.update() }

        val document = client.fetchPreview("/user/view?user=$id")

        assertEquals(user.username, document.metaContent("og:title"))
        assertEquals("Cooks every Sunday.", document.metaContent("og:description"))
        assertEquals("profile", document.metaContent("og:type"))
        assertEquals("$siteUrl/user/view?user=$id", document.metaContent("og:url"))
        assertEquals("$siteUrl/image/users/default.webp", document.metaContent("og:image"))
    }

    @Test
    fun `the trailing-slash form the app builds is served too`() = runTest {
        val id = setupTestUser(uniqueMail())

        // `toViewUser` navigates to `/user/view/?user=…`, unlike every other view route.
        val document = client.fetchPreview("/user/view/?user=$id")

        assertEquals(userService.getEntityById(id).username, document.metaContent("og:title"))
    }

    @Test
    fun `a non-public member previews as the site`() = runTest {
        val id = setupTestUser(uniqueMail(), UserSettingsDTO(isAccountPublic = false))

        val document = client.fetchPreview("/user/view?user=$id")

        assertEquals(defaultTitle, document.metaContent("og:title"))
        assertFalse(document.contains(userService.getEntityById(id).username), "a private member's name leaked")
    }

    @Test
    fun `a banned member previews as the site`() = runTest {
        val id = setupTestUser(uniqueMail())
        moderationService.banUser(id, "abuse")

        val document = client.fetchPreview("/user/view?user=$id")

        assertEquals(defaultTitle, document.metaContent("og:title"))
        assertFalse(document.contains(userService.getEntityById(id).username), "a banned member's name leaked")
    }

    // endregion

    // region cookbooks and ingredients

    @Test
    fun `cookbook preview carries its title and description`() = runTest {
        val cookbook = cookbookService.createCookbook(
            CookbookDTO(title = "Weeknight dinners", description = "Fast and cheap.", visibility = Visibility.PUBLIC)
        )

        val document = client.fetchPreview("/cookbook/view?cookbook=${cookbook.id}")

        assertEquals("Weeknight dinners", document.metaContent("og:title"))
        assertEquals("Fast and cheap.", document.metaContent("og:description"))
        assertEquals("website", document.metaContent("og:type"))
        assertEquals("$siteUrl/image/cookbooks/default.webp", document.metaContent("og:image"))
    }

    @Test
    fun `a private cookbook previews as the site`() = runTest {
        val cookbook = cookbookService.createCookbook(
            CookbookDTO(title = "My secret list", description = "Nobody's business.", visibility = Visibility.PRIVATE)
        )

        val document = client.fetchPreview("/cookbook/view?cookbook=${cookbook.id}")

        assertEquals(defaultTitle, document.metaContent("og:title"))
        assertFalse(document.contains("My secret list"), "the private cookbook's title leaked")
        assertFalse(document.contains("Nobody's business"), "the private cookbook's description leaked")
    }

    @Test
    fun `ingredient preview carries the name in the requested locale`() = runTest {
        val ingredient = ingredientService.createIngredient(
            IngredientDTO(name = mapOf(Locale.EN to "Tomato", Locale.FR to "Tomate"), type = IngredientType.VEGETABLE)
        )

        assertEquals("Tomato", client.fetchPreview("/ingredient/view?ingredient=${ingredient.id}").metaContent("og:title"))
        assertEquals("Tomate", client.fetchPreview("/ingredient/view?ingredient=${ingredient.id}&locale=fr").metaContent("og:title"))
    }

    // endregion

    // region fallbacks

    @Test
    fun `an unknown id previews as the site, and is not an error`() = runTest {
        client.get("/recipe/view?id=999999").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(defaultTitle, bodyAsText().metaContent("og:title"))
        }
    }

    @Test
    fun `a missing or unparseable id previews as the site`() = runTest {
        assertEquals(defaultTitle, client.fetchPreview("/recipe/view").metaContent("og:title"))
        assertEquals(defaultTitle, client.fetchPreview("/recipe/view?id=not-a-number").metaContent("og:title"))
        // With no entity to point at, the canonical URL is the site's own.
        assertEquals(siteUrl, client.fetchPreview("/recipe/view").metaContent("og:url"))
    }

    @Test
    fun `the fallback wording follows the locale`() = runTest {
        val english = client.fetchPreview("/recipe/view").metaContent("og:description")!!
        val french = client.fetchPreview("/recipe/view?locale=FR").metaContent("og:description")!!

        assertTrue(english.startsWith("Share your recipes"), english)
        assertTrue(french.startsWith("Partagez vos recettes"), french)
        // An unreadable locale is no reason to answer a crawler with a 400.
        assertEquals(english, client.fetchPreview("/recipe/view?locale=klingon").metaContent("og:description"))
    }

    // endregion

    // region the document itself

    @Test
    fun `the shell is kept and only its preview block is replaced`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(RecipeDTO(title = "Ratatouille"), userService.getEntityById(owner))

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertTrue(document.contains(FakeAppShellSource.OUTSIDE_HEAD), "the shell's head was lost")
        assertTrue(document.contains(FakeAppShellSource.OUTSIDE_BODY), "the app's mount point was lost")
        assertTrue(document.contains("""src="/src/main.ts""""), "the app's entry script was lost")
        // Replaced, not appended to: two og:title tags and a crawler picks whichever it likes.
        assertEquals(1, Regex("""property="og:title"""").findAll(document).count())
        assertEquals(1, Regex("<title>").findAll(document).count())
        assertEquals("<title>Ratatouille</title>", document.titleTag(), "the default block survived")
        assertFalse(document.contains("preview:start"), "the markers were left in the output")
    }

    @Test
    fun `a shell built before the markers existed still gets the tags`() = runTest {
        fakeAppShellSource.html = FakeAppShellSource.SHELL_WITHOUT_MARKERS
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(RecipeDTO(title = "Older build"), userService.getEntityById(owner))

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertEquals("Older build", document.metaContent("og:title"))
        assertTrue(document.contains(FakeAppShellSource.OUTSIDE_BODY), "the app's mount point was lost")
    }

    /**
     * nginx answered these paths from disk before this feature, where a HEAD got a 200 like
     * any other static file. Ktor answers 405 for an undeclared verb, and unfurlers and
     * uptime monitors do probe with HEAD, so the 405 would cost the very preview this is for.
     */
    @Test
    fun `HEAD is answered, not rejected as a bad method`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(RecipeDTO(title = "Probed"), userService.getEntityById(owner))

        for (path in listOf(
            "/recipe/view?id=${recipe.id}",
            "/user/view/?user=$owner",
            "/cookbook/view?cookbook=1",
            "/ingredient/view?ingredient=1",
        )) {
            client.head(path).apply {
                assertEquals(HttpStatusCode.OK, status, "HEAD $path")
                assertEquals(ContentType.Text.Html, contentType()?.withoutParameters(), "HEAD $path")
            }
        }
    }

    /**
     * The one assertion that spans both halves of the feature. The service looks for markers
     * that live in a file in another module, built by another toolchain — nothing but this
     * test notices if one side loses them, and the symptom in production would be a
     * duplicated <title> rather than an error.
     */
    @Test
    fun `the markers in the shipped index html are the ones the service replaces`() = runTest {
        fakeAppShellSource.html = shippedIndexHtml()
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(RecipeDTO(title = "Real shell"), userService.getEntityById(owner))

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertEquals("Real shell", document.metaContent("og:title"))
        assertEquals("<title>Real shell</title>", document.titleTag())
        assertEquals(1, Regex("""property="og:title"""").findAll(document).count())
        // The parts of the head that must outlive the swap, because they sit outside the
        // markers: the app's own entry point and its icon.
        assertTrue(document.contains("""<div id="app">"""), "the app's mount point was lost")
        assertTrue(document.contains("/src/main.ts"), "the app's entry script was lost")
    }

    @Test
    fun `an unreadable shell is a bad gateway, so nginx can answer from disk instead`() = runTest {
        fakeAppShellSource.html = null

        client.get("/recipe/view?id=1").apply {
            assertEquals(HttpStatusCode.BadGateway, status)
        }
    }

    @Test
    fun `a title and description are escaped, not injected`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(
            RecipeDTO(
                title = """Tarte "maison" & <script>alert(1)</script>""",
                description = """It's <b>good</b> & "cheap"""",
            ),
            userService.getEntityById(owner),
        )

        val document = client.fetchPreview("/recipe/view?id=${recipe.id}")

        assertFalse(document.contains("<script>"), "a raw script tag reached the document")
        assertFalse(document.contains("<b>"), "raw markup reached the document")
        // The attribute value survives intact once the entities are decoded, and nothing in
        // it can end the attribute or open a tag.
        assertEquals(
            "Tarte &quot;maison&quot; &amp; &lt;script&gt;alert(1)&lt;/script&gt;",
            document.metaContent("og:title"),
        )
        assertEquals("It&#39;s &lt;b&gt;good&lt;/b&gt; &amp; &quot;cheap&quot;", document.metaContent("og:description"))
    }

    @Test
    fun `a long description is cut on a word boundary`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val description = List(80) { "flour" }.joinToString(" ")
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Wordy", description = description),
            userService.getEntityById(owner),
        )

        val cut = client.fetchPreview("/recipe/view?id=${recipe.id}").metaContent("og:description")!!

        assertTrue(cut.endsWith("…"), cut)
        assertTrue(cut.length <= 201, "${cut.length} characters is longer than the limit")
        assertFalse(cut.removeSuffix("…").endsWith("flou"), "cut mid-word: $cut")
    }

    // endregion

    private fun uniqueMail() = "${UUID.randomUUID()}@mail.com"

    /** `frontend/index.html` as checked in, found from wherever Gradle runs the tests. */
    private fun shippedIndexHtml(): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "frontend/index.html")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException("frontend/index.html not found from ${File("").absolutePath}")
    }

    private suspend fun HttpClient.fetchPreview(path: String): String =
        this.get(path).run {
            assertEquals(HttpStatusCode.OK, status, "GET $path")
            assertEquals(ContentType.Text.Html, contentType()?.withoutParameters(), "GET $path")
            bodyAsText()
        }

    /**
     * Asserting on the tag's value rather than on the document containing a substring: a
     * `contains` check passes just as happily when the value lands in the wrong attribute.
     */
    private fun String.metaContent(property: String): String? =
        Regex("""<meta (?:property|name)="${Regex.escape(property)}" content="([^"]*)">""")
            .find(this)
            ?.groupValues
            ?.get(1)

    private fun String.titleTag(): String? = Regex("<title>.*?</title>").find(this)?.value
}
