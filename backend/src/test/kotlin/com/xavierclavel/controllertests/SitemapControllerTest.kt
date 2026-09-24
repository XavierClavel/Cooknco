package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.services.ModerationService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.SitemapService
import shared.dto.RecipeDTO
import shared.dto.UserSettingsDTO
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test
import org.koin.test.inject
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `GET /sitemap.xml`.
 *
 * Every request here is the anonymous one a crawler makes, and fixtures are built through the
 * services rather than over HTTP so that nothing accidentally carries a session — the whole
 * question this endpoint answers is what the public may be pointed at.
 */
class SitemapControllerTest : ApplicationTest() {
    private val recipeService: RecipeService by inject()
    private val moderationService: ModerationService by inject()
    private val sitemapService: SitemapService by inject()

    /** Matches the `frontend.url` of application-test.yaml. */
    private val siteUrl = "http://localhost:3000"

    @Test
    fun `the sitemap lists the site, a public recipe and its author`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Tarte aux pommes"),
            userService.getEntityById(owner),
        )

        val locations = client.fetchSitemap().locations()

        assertTrue(locations.contains(siteUrl), "the homepage is not listed: $locations")
        assertTrue(locations.contains("$siteUrl/recipe/view?id=${recipe.id}"), "the recipe is not listed")
        assertTrue(locations.contains("$siteUrl/user/view?user=$owner"), "the author is not listed")
    }

    @Test
    fun `a hidden recipe is not advertised`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Hidden by a moderator"),
            userService.getEntityById(owner),
        )
        moderationService.hideRecipe(recipe.id, "spam")

        val locations = client.fetchSitemap().locations()

        assertFalse(
            locations.contains("$siteUrl/recipe/view?id=${recipe.id}"),
            "a hidden recipe was advertised to crawlers",
        )
    }

    @Test
    fun `a recipe behind a private account is not advertised, and neither is the account`() = runTest {
        val owner = setupTestUser(uniqueMail(), UserSettingsDTO(isAccountPublic = false))
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Behind a private account"),
            userService.getEntityById(owner),
        )

        val locations = client.fetchSitemap().locations()

        assertFalse(locations.contains("$siteUrl/recipe/view?id=${recipe.id}"), "a private recipe was advertised")
        assertFalse(locations.contains("$siteUrl/user/view?user=$owner"), "a private profile was advertised")
    }

    @Test
    fun `a banned member's recipe and profile are not advertised`() = runTest {
        val owner = setupTestUser(uniqueMail())
        val recipe = recipeService.createRecipe(
            RecipeDTO(title = "Written before the ban"),
            userService.getEntityById(owner),
        )
        moderationService.banUser(owner, "spam")

        val locations = client.fetchSitemap().locations()

        assertFalse(locations.contains("$siteUrl/recipe/view?id=${recipe.id}"), "a banned member's recipe was advertised")
        assertFalse(locations.contains("$siteUrl/user/view?user=$owner"), "a banned member's profile was advertised")
    }

    /**
     * The routes the backend renders preview tags for are a longer list than the routes the
     * app serves signed out, and only the shorter one belongs here — see [SitemapService].
     * Asserted rather than left as a comment because adding a preview route is exactly the
     * change that would quietly add it to the sitemap too.
     */
    @Test
    fun `cookbook and ingredient pages are not advertised, because they need an account to render`() = runTest {
        val owner = setupTestUser(uniqueMail())
        recipeService.createRecipe(RecipeDTO(title = "Something"), userService.getEntityById(owner))

        val document = client.fetchSitemap()

        assertFalse(document.contains("/cookbook/view"), "a cookbook page was advertised")
        assertFalse(document.contains("/ingredient/view"), "an ingredient page was advertised")
    }

    @Test
    fun `it is served as XML, and the document parses`() = runTest {
        val owner = setupTestUser(uniqueMail())
        recipeService.createRecipe(RecipeDTO(title = "Parsed"), userService.getEntityById(owner))

        sitemapService.invalidate()
        client.get("/sitemap.xml").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.Xml, contentType()?.withoutParameters())
            val root = parse(bodyAsText()).documentElement
            assertEquals("urlset", root.tagName)
            assertEquals(
                "http://www.sitemaps.org/schemas/sitemap/0.9",
                root.getAttribute("xmlns"),
                "a urlset in the wrong namespace is rejected whole",
            )
        }
    }

    /**
     * A recipe titled with markup would otherwise be quoted into the document — the titles
     * never reach it today, only ids do, but the escaping is what keeps that true if a path
     * ever gains a slug. Asserted through the parser: a document that still parses after a
     * hostile title is one where nothing broke out.
     */
    @Test
    fun `a recipe with markup in its title cannot break the document`() = runTest {
        val owner = setupTestUser(uniqueMail())
        recipeService.createRecipe(
            RecipeDTO(title = """<loc>https://evil.example/</loc> & "quoted""""),
            userService.getEntityById(owner),
        )

        val document = client.fetchSitemap()

        assertFalse(document.contains("evil.example"), "a recipe title reached the document")
        parse(document)
    }

    /**
     * The endpoint is public and unauthenticated, and generating the document walks two
     * tables, so it is generated at most once an hour. Worth a test because the symptom of
     * losing the cache is a cost rather than a wrong answer, which review does not catch.
     */
    @Test
    fun `the document is generated once and served from the cache until it expires`() = runTest {
        val owner = setupTestUser(uniqueMail())
        recipeService.createRecipe(RecipeDTO(title = "First"), userService.getEntityById(owner))

        val first = client.fetchSitemap()
        val later = recipeService.createRecipe(RecipeDTO(title = "Second"), userService.getEntityById(owner))

        // No invalidation: the same document comes back, without the recipe added since.
        assertEquals(first, client.get("/sitemap.xml").bodyAsText())
        assertFalse(
            client.get("/sitemap.xml").bodyAsText().contains("id=${later.id}"),
            "the catalogue was read again inside the caching window",
        )

        sitemapService.invalidate()
        assertTrue(client.fetchSitemap().contains("id=${later.id}"), "the rebuilt document is missing a new recipe")
    }

    private fun uniqueMail() = "${UUID.randomUUID()}@mail.com"

    /**
     * Drops the cached document before asking for it.
     *
     * Every test in this class has just written the rows it is about to assert on, and the
     * service deliberately holds its answer for an hour — which spans the whole suite. Going
     * through this helper is what keeps a test from asserting against a document another test
     * built.
     */
    private suspend fun HttpClient.fetchSitemap(): String {
        sitemapService.invalidate()
        return get("/sitemap.xml").run {
            assertEquals(HttpStatusCode.OK, status)
            bodyAsText()
        }
    }

    private fun parse(xml: String) =
        DocumentBuilderFactory.newInstance()
            .also { it.isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

    /** Every `<loc>` in the document, which is what a crawler actually reads off it. */
    private fun String.locations(): List<String> {
        val nodes = parse(this).getElementsByTagName("loc")
        return (0 until nodes.length).map { nodes.item(it).textContent }
    }
}
