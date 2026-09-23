package main.com.xavierclavel.other

import com.xavierclavel.services.CookbookService
import io.ebean.Paging
import main.com.xavierclavel.utils.FetchPlanTest
import main.com.xavierclavel.utils.addCookbookRecipe
import main.com.xavierclavel.utils.addCookbookUser
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.recipeDTO
import main.com.xavierclavel.utils.sampleCookbookDto
import org.junit.jupiter.api.Test
import org.koin.test.inject
import shared.enums.Sort

/**
 * Guards the cookbook read paths against N+1.
 *
 * A listed cookbook states how many recipes and how many members it holds, and names the first ten
 * of those members — three reads of two collections, all of them per row.
 */
class CookbookFetchPlanTest : FetchPlanTest() {
    private val cookbookService: CookbookService by inject()

    private val paging = Paging.of(0, 20)

    @Test
    fun `listing cookbooks costs the same whether there is one or several`() = runTest {
        runAsUser1 {
            val me = userService.findByMail(USER1)!!.id
            client.createCookbook(sampleCookbookDto.copy(title = "one"))
            assertQueryCountDoesNotGrow(
                what = "CookbookService.listCookbooks",
                read = { cookbookService.listCookbooks(paging, Sort.NONE, null, null, null, me) },
                grow = { repeat(2) { client.createCookbook(sampleCookbookDto.copy(title = "more $it")) } },
            )
        }
    }

    /**
     * The members are named, not only counted, so a cookbook with people in it costs more than an
     * empty one however the fetch plan is written. What must not scale is the number of *cookbooks*:
     * this grows both at once, which is what a listing of shared books looks like.
     */
    @Test
    fun `listing cookbooks with members costs the same whether there is one or several`() = runTest {
        runAsUser1 {
            val me = userService.findByMail(USER1)!!.id
            val first = client.createCookbook(sampleCookbookDto.copy(title = "one")).id
            client.addCookbookUser(first, client.createUser(mail = "member-first@mail.com").id, false)
            assertQueryCountDoesNotGrow(
                what = "CookbookService.listCookbooks over shared cookbooks",
                read = { cookbookService.listCookbooks(paging, Sort.NONE, null, null, null, me) },
                grow = {
                    repeat(2) { index ->
                        val cookbook = client.createCookbook(sampleCookbookDto.copy(title = "more $index")).id
                        client.addCookbookUser(
                            cookbook,
                            client.createUser(mail = "member-$index@mail.com").id,
                            false,
                        )
                    }
                },
            )
        }
    }

    /**
     * What the recipe editor asks to draw the "add to cookbook" list: every cookbook of the caller's,
     * each marked with whether it already holds the recipe. The mark is read off the cookbook's own
     * recipes, so it is a collection read per row.
     */
    @Test
    fun `marking which cookbooks hold a recipe costs the same whether there is one or several`() = runTest {
        runAsUser1 {
            val me = userService.findByMail(USER1)!!.id
            val recipe = client.createRecipe(recipeDTO).id
            val first = client.createCookbook(sampleCookbookDto.copy(title = "one")).id
            client.addCookbookRecipe(first, recipe)
            assertQueryCountDoesNotGrow(
                what = "CookbookService.getRecipeStatusInUserCookbooks",
                read = { cookbookService.getRecipeStatusInUserCookbooks(me, recipe) },
                grow = { repeat(2) { client.createCookbook(sampleCookbookDto.copy(title = "more $it")) } },
            )
        }
    }

    /**
     * The two tabs of a cookbook's own page. Both were already clean when this was written — the
     * rows are read off the loaded cookbook rather than queried per row — and they ship as
     * regression guards.
     */
    @Test
    fun `reading a cookbook's recipes costs the same whether it holds one or several`() = runTest {
        runAsUser1 {
            val cookbook = client.createCookbook(sampleCookbookDto).id
            client.addCookbookRecipe(cookbook, client.createRecipe(recipeDTO.copy(title = "one")).id)
            assertQueryCountDoesNotGrow(
                what = "CookbookService.getCookbookRecipes",
                read = { cookbookService.getCookbookRecipes(cookbook, paging) },
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

    @Test
    fun `reading a cookbook's members costs the same whether it has one or several`() = runTest {
        runAsUser1 {
            val cookbook = client.createCookbook(sampleCookbookDto).id
            assertQueryCountDoesNotGrow(
                what = "CookbookService.getCookbookUsers",
                read = { cookbookService.getCookbookUsers(cookbook, paging) },
                grow = {
                    repeat(2) { index ->
                        client.addCookbookUser(
                            cookbook,
                            client.createUser(mail = "member-$index@mail.com").id,
                            false,
                        )
                    }
                },
            )
        }
    }
}
