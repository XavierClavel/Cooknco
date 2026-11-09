package main.com.xavierclavel.other

import com.xavierclavel.services.OfflineSyncService
import main.com.xavierclavel.utils.FetchPlanTest
import main.com.xavierclavel.utils.addCookbookRecipe
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createLike
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.ingredientDTO
import main.com.xavierclavel.utils.recipeDTO
import main.com.xavierclavel.utils.sampleCookbookDto
import org.junit.jupiter.api.Test
import org.koin.test.inject
import shared.dto.OfflineSyncDTO
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.Locale

/**
 * Guards the offline sync against N+1.
 *
 * It is the one read here that hands over recipes *whole* by the hundred — steps, the
 * ingredients each step uses, and the ingredient lines — where every other path either maps a
 * single recipe or pages overviews. Read off the entities, each of those collections costs a
 * query per recipe, which at a first sync is a few hundred queries behind one request.
 */
class OfflineFetchPlanTest : FetchPlanTest() {
    private val offlineSyncService: OfflineSyncService by inject()

    @Test
    fun `a sync costs the same whether it sends one recipe or several`() = runTest {
        var catalogue = listOf<Long>()
        runAsAdmin {
            catalogue = (0 until 2).map {
                client.createIngredient(ingredientDTO.copy(name = mapOf(Locale.EN to "ingredient $it"))).id
            }
        }
        var theirs = listOf<Long>()
        runAsUser2 {
            theirs = (0 until 3).map { client.createRecipe(whole("theirs $it", catalogue)).id }
        }
        runAsUser1 {
            val me = userService.findByMail(USER1)!!.id
            client.createRecipe(whole("mine", catalogue))
            // One liked from the start: an empty list costs no like count at all, which would
            // read as growth the moment the first like arrived.
            client.createLike(theirs.first())
            assertQueryCountDoesNotGrow(
                what = "OfflineSyncService.sync over the recipes it sends",
                read = { offlineSyncService.sync(me, OfflineSyncDTO(), Locale.EN).recipes },
                grow = {
                    client.createRecipe(whole("mine again", catalogue))
                    // Liked rather than written, so the authors differ as well as the count.
                    theirs.drop(1).forEach { client.createLike(it) }
                },
            )
        }
    }

    @Test
    fun `a sync costs the same whether the account is in one cookbook or several`() = runTestAsUser {
        val me = userService.findByMail(USER1)!!.id
        fun addBook(title: String) = suspend {
            val book = client.createCookbook(sampleCookbookDto.copy(title = title)).id
            client.addCookbookRecipe(book, client.createRecipe(recipeDTO.copy(title = "in $title")).id)
        }
        addBook("one")()
        assertQueryCountDoesNotGrow(
            what = "OfflineSyncService.sync over the cookbooks",
            read = { offlineSyncService.sync(me, OfflineSyncDTO(), Locale.EN).cookbooks },
            grow = { addBook("two")(); addBook("three")() },
        )
    }

    /** A recipe with every collection a whole copy carries: lines, steps, and a step's uses. */
    private fun whole(title: String, catalogue: List<Long>) = recipeDTO.copy(
        title = title,
        ingredients = catalogue
            .map { RecipeDTO.RecipeIngredientDTO(id = it, unit = AmountUnit.GRAM, amount = 100f) }
            .toMutableList(),
        steps = mutableListOf(
            RecipeDTO.RecipeStepDTO(
                text = "mix",
                ingredients = catalogue.indices.map { RecipeDTO.RecipeStepIngredientDTO(it, null) },
            ),
            RecipeDTO.RecipeStepDTO(text = "bake"),
        ),
    )
}
