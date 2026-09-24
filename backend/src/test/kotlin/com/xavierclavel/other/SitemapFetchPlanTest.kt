package main.com.xavierclavel.other

import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.SitemapService
import main.com.xavierclavel.utils.FetchPlanTest
import main.com.xavierclavel.utils.createUser
import shared.dto.RecipeDTO
import org.junit.jupiter.api.Test
import org.koin.test.inject

/**
 * Guards the sitemap against N+1.
 *
 * It is the one read path here whose size is the whole catalogue rather than a page of it: every
 * public recipe and every public member, in one document. A query per row is survivable on a screen
 * showing twenty and is not survivable here, and the caller that would find out is a crawler rather
 * than a person who could report it.
 *
 * Both reads are bounded by `setMaxRows`, so nothing is joined from a collection and what this
 * actually pins is the day something starts reading an association off each row — a recipe's owner
 * to build a slug, say, which is exactly the shape a URL change would take.
 */
class SitemapFetchPlanTest : FetchPlanTest() {
    private val sitemapService: SitemapService by inject()
    private val recipeService: RecipeService by inject()

    @Test
    fun `the sitemap costs the same whether there are few recipes or many`() = runTest {
        val owner = userService.findByMail(USER1)
        recipeService.createRecipe(RecipeDTO(title = "First"), owner)

        assertQueryCountDoesNotGrow(
            what = "SitemapService.sitemap (recipes)",
            read = { sitemapLocations() },
            grow = {
                repeat(4) { index ->
                    recipeService.createRecipe(RecipeDTO(title = "Grown $index"), owner)
                }
            },
        )
    }

    @Test
    fun `the sitemap costs the same whether there are few members or many`() = runTest {
        assertQueryCountDoesNotGrow(
            what = "SitemapService.sitemap (members)",
            read = { sitemapLocations() },
            grow = { repeat(4) { client.createUser(mail = "listed-$it@mail.com") } },
        )
    }

    /**
     * The document's `<loc>` lines, rebuilt rather than served from the cache.
     *
     * The invalidation is what makes this measure anything: the service holds its answer for an
     * hour, so a second read would cost no queries at all and the comparison would fail describing
     * a cache rather than a fetch plan.
     */
    private fun sitemapLocations(): List<String> {
        sitemapService.invalidate()
        return sitemapService.sitemap().lines().filter { it.contains("<loc>") }
    }
}
