package main.com.xavierclavel.other

import com.xavierclavel.services.AdminService
import com.xavierclavel.services.RecipeService
import io.ebean.Paging
import io.ktor.client.HttpClient
import main.com.xavierclavel.utils.FetchPlanTest
import main.com.xavierclavel.utils.addCookbookRecipe
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.ingredientDTO
import main.com.xavierclavel.utils.recipeDTO
import main.com.xavierclavel.utils.sampleCookbookDto
import org.junit.jupiter.api.Test
import org.koin.test.inject
import shared.RecipeFilter
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.Locale
import shared.enums.Sort

/**
 * Guards the recipe read paths against N+1.
 *
 * Both listings map every row through a DTO that states a like count, and the admin one a cookbook
 * count as well — collections, so a missing fetch path costs a round trip per recipe on the busiest
 * endpoint in the product.
 */
class RecipeFetchPlanTest : FetchPlanTest() {
    private val recipeService: RecipeService by inject()
    private val adminService: AdminService by inject()

    private val paging = Paging.of(0, 20)

    @Test
    fun `listing recipes costs the same whether the feed holds one or several`() = runTest {
        runAsUser1 {
            val me = userService.findByMail(USER1)!!.id
            client.createRecipe(recipeDTO.copy(title = "one"))
            assertQueryCountDoesNotGrow(
                what = "RecipeService.findList",
                read = { recipeService.findList(me, paging, Sort.NONE, RecipeFilter()) },
                grow = { repeat(2) { client.createRecipe(recipeDTO.copy(title = "more $it")) } },
            )
        }
    }

    /**
     * The owner is a `-to-one`, batch-loaded within `lazyLoadBatchSize`, so the guard above cannot
     * see it: one query covers every recipe in a page whether the authors are one account or twenty.
     * Measured separately so that a feed of many authors — the normal case, and the one the guard
     * above does not build — is known to cost what a feed of one does.
     */
    @Test
    fun `listing recipes written by different people costs no more than one author's`() = runTest {
        runAsUser1 { client.createRecipe(recipeDTO.copy(title = "mine")) }
        assertQueryCountDoesNotGrow(
            what = "RecipeService.findList over several authors",
            read = { recipeService.findList(null, paging, Sort.NONE, RecipeFilter()) },
            grow = {
                repeat(2) { index ->
                    val mail = "author-$index@mail.com"
                    client.createUser(mail = mail)
                    runAs(mail, "password") { client.createRecipe(recipeDTO.copy(title = "theirs $index")) }
                }
            },
        )
    }

    @Test
    fun `the admin recipe table costs the same whether it holds one recipe or several`() = runTest {
        runAsUser1 {
            client.createRecipe(recipeDTO.copy(title = "one"))
            assertQueryCountDoesNotGrow(
                what = "AdminService.searchRecipes",
                read = { adminService.searchRecipes(null, null, null, false, Sort.NONE, paging).second },
                grow = { repeat(2) { client.createRecipe(recipeDTO.copy(title = "more $it")) } },
            )
        }
    }

    /**
     * What a cookbook export reads before it prints.
     *
     * The *query* is guarded; the mapping after it is not, and deliberately. `findByCookbook` sets
     * `maxRows`, which is what makes Ebean drop each recipe's steps and ingredients from the plan,
     * so printing a book still costs a few queries per recipe — the reasoning is in that method's
     * KDoc, and the print itself costs orders of magnitude more. What `describeAll` adds on top of
     * it does not scale: the like counts and the catalogue names are resolved for the whole book at
     * once, which the recipe-detail guard below is what holds.
     */
    @Test
    fun `reading a cookbook's recipes costs the same whether it holds one or several`() = runTest {
        runAsUser1 {
            val me = userService.findByMail(USER1)!!.id
            val cookbook = client.createCookbook(sampleCookbookDto).id
            client.addCookbookRecipe(cookbook, client.createRecipe(recipeDTO.copy(title = "one")).id)
            assertQueryCountDoesNotGrow(
                what = "RecipeService.findByCookbook",
                read = { recipeService.findByCookbook(cookbook, limit = 100, visibleTo = me) },
                grow = {
                    repeat(2) { index ->
                        client.addCookbookRecipe(
                            cookbook,
                            client.createRecipe(recipeDTO.copy(title = "more $index")).id,
                        )
                    }
                },
            )
        }
    }

    /**
     * A recipe's own page, where the rows are its ingredient lines rather than recipes. Each line
     * naming a catalogue entry reads its translations to find the name in the caller's language, so
     * a missing fetch path costs a query per line.
     *
     * Two recipes rather than one that grows: a recipe's lines are fixed when it is saved.
     */
    @Test
    fun `reading a recipe costs the same whether it lists one ingredient or several`() = runTest {
        var catalogue = listOf<Long>()
        runAsAdmin {
            catalogue = (0 until 3).map { client.createIngredientNamed("ingredient $it").id }
        }
        runAsUser1 {
            val me = userService.findByMail(USER1)!!.id
            val oneLine = client.createRecipe(recipeWith("one line", catalogue.take(1))).id
            val threeLines = client.createRecipe(recipeWith("three lines", catalogue)).id
            assertSameQueryCount(
                what = "RecipeService.getById over a recipe's ingredient lines",
                forFew = { recipeService.getById(me, oneLine, Locale.EN).ingredients },
                forMany = { recipeService.getById(me, threeLines, Locale.EN).ingredients },
            )
        }
    }

    private suspend fun HttpClient.createIngredientNamed(name: String) =
        createIngredient(ingredientDTO.copy(name = mapOf(Locale.EN to name)))

    private fun recipeWith(title: String, ingredients: List<Long>) = recipeDTO.copy(
        title = title,
        ingredients = ingredients
            .map { RecipeDTO.RecipeIngredientDTO(id = it, unit = AmountUnit.GRAM, amount = 100f) }
            .toMutableList(),
    )
}
