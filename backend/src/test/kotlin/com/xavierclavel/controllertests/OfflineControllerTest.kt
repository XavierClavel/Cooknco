package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.OfflineSyncService
import com.xavierclavel.services.RecipeNotesService
import com.xavierclavel.services.RecipeService
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.absorbCustomIngredient
import main.com.xavierclavel.utils.addCookbookRecipe
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createLike
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.deleteLike
import main.com.xavierclavel.utils.deleteRecipeAsAdminRaw
import main.com.xavierclavel.utils.heldOf
import main.com.xavierclavel.utils.hideRecipe
import main.com.xavierclavel.utils.recipeDTO
import main.com.xavierclavel.utils.sampleCookbookDto
import main.com.xavierclavel.utils.syncOffline
import main.com.xavierclavel.utils.syncOfflineFully
import main.com.xavierclavel.utils.syncOfflineRaw
import main.com.xavierclavel.utils.updateRecipe
import main.com.xavierclavel.utils.uploadRecipeImage
import main.com.xavierclavel.utils.uploadStepImage
import org.junit.jupiter.api.Test
import org.koin.test.inject
import shared.dto.IngredientDTO
import shared.dto.OfflineSyncDTO
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.Locale
import shared.enums.OfflinePinSet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The offline sync: one request that tells a phone what it should hold and hands over only
 * what it does not have yet.
 *
 * Two halves are worth holding. What is *pinned* has to follow every way a recipe enters or
 * leaves an account's collections — including the ones that happen to somebody else's rows,
 * which is the reason this compares state rather than replaying a log. And what is *sent* has
 * to follow every write a copy shows, which is what `Recipe.revision` exists for: an edit, a
 * step picture, a recipe picture, and an operator's ingredient absorb all have to move it, or
 * a phone keeps a stale copy for good.
 */
class OfflineControllerTest : ApplicationTest() {
    private val recipeService: RecipeService by inject()
    private val recipeNotesService: RecipeNotesService by inject()
    private val cookbookService: CookbookService by inject()

    private fun idOf(mail: String) = userService.findByMail(mail)!!.id

    // ------------------------------------------------------------------ access

    @Test
    fun `the sync is closed to anonymous callers`() = runTest {
        assertEquals(HttpStatusCode.Unauthorized, client.syncOfflineRaw().status)
    }

    @Test
    fun `a phone claiming to hold more than the server ever sends is refused`() = runTestAsUser {
        val held = (1L..OfflineSyncService.MAX_HELD + 1L).map { OfflineSyncDTO.HeldRecipe(it, 1) }
        assertEquals(HttpStatusCode.BadRequest, client.syncOfflineRaw(held).status)
    }

    // ------------------------------------------------------------- what is pinned

    @Test
    fun `a first sync hands over every recipe the account keeps, with its notes and likes`() = runTest {
        var theirs = 0L
        var inMyCookbook = 0L
        runAsUser2 {
            theirs = client.createRecipe(recipeDTO.copy(title = "theirs, liked")).id
            inMyCookbook = client.createRecipe(recipeDTO.copy(title = "theirs, in my cookbook")).id
        }
        runAsUser1 {
            val mine = client.createRecipe(recipeDTO.copy(title = "mine")).id
            client.createLike(theirs)
            client.addCookbookRecipe(client.createCookbook(sampleCookbookDto).id, inMyCookbook)
            recipeNotesService.createNotes(mine, idOf(USER1), "35 min, not 25, in our oven")

            val answer = client.syncOffline()

            assertFalse(answer.hasMore)
            assertEquals(
                mapOf(
                    mine to setOf(OfflinePinSet.OWN),
                    inMyCookbook to setOf(OfflinePinSet.COOKBOOK),
                    theirs to setOf(OfflinePinSet.LIKED),
                ),
                answer.pinned.associate { it.id to it.sets },
            )
            // Own first, then the cookbooks, then the likes.
            assertEquals(listOf(mine, inMyCookbook, theirs), answer.pinned.map { it.id })
            assertEquals(listOf(mine, inMyCookbook, theirs), answer.recipes.map { it.id })
            assertTrue(answer.recipes.all { it.steps.isNotEmpty() }, "the recipes come whole")
            assertEquals(setOf(theirs), answer.pinned.filter { it.isLiked }.map { it.id }.toSet())
            assertEquals(mapOf(mine to "35 min, not 25, in our oven"), answer.notes.associate { it.recipeId to it.notes })
            assertEquals(listOf(mine), answer.own.map { it.id })
            assertEquals(listOf(theirs), answer.liked.map { it.id })
            assertEquals(listOf(inMyCookbook), answer.cookbooks.single().recipes.map { it.id })
        }
    }

    @Test
    fun `a phone holding everything is sent no recipe, and still told what it holds`() = runTestAsUser {
        client.createRecipe(recipeDTO.copy(title = "one"))
        client.createRecipe(recipeDTO.copy(title = "two"))
        val first = client.syncOffline()

        val second = client.syncOffline(heldOf(first.recipes))

        assertEquals(emptyList(), second.recipes)
        assertEquals(first.pinned, second.pinned)
        assertEquals(2, second.own.size)
    }

    @Test
    fun `a recipe the account no longer keeps is no longer pinned`() = runTest {
        var theirs = 0L
        runAsUser2 { theirs = client.createRecipe(recipeDTO.copy(title = "theirs")).id }
        runAsUser1 {
            client.createLike(theirs)
            val held = heldOf(client.syncOffline().recipes)
            assertEquals(listOf(theirs), held.map { it.id })

            client.deleteLike(theirs)

            val after = client.syncOffline(held)
            assertEquals(emptyList(), after.pinned)
            assertEquals(emptyList(), after.recipes)
        }
    }

    /**
     * The case a change log could not serve without writing to every phone: a moderator's
     * action on somebody else's recipe. The author keeps theirs — a hidden recipe never
     * silently vanishes on the person who wrote it.
     */
    @Test
    fun `a recipe a moderator hides leaves every phone but its author's`() = runTest {
        var theirs = 0L
        runAsUser2 { theirs = client.createRecipe(recipeDTO.copy(title = "theirs")).id }
        runAsUser1 {
            client.createLike(theirs)
            assertEquals(listOf(theirs), client.syncOffline().pinned.map { it.id })
        }
        runAsAdmin { client.hideRecipe(theirs) }

        runAsUser1 { assertEquals(emptyList(), client.syncOffline().pinned) }
        runAsUser2 { assertEquals(listOf(theirs), client.syncOffline().pinned.map { it.id }) }
    }

    @Test
    fun `a recipe a moderator deletes out of a cookbook leaves the book and the phone`() = runTest {
        var theirs = 0L
        runAsUser2 { theirs = client.createRecipe(recipeDTO.copy(title = "theirs")).id }
        runAsUser1 {
            client.addCookbookRecipe(client.createCookbook(sampleCookbookDto).id, theirs)
            assertEquals(listOf(theirs), client.syncOffline().pinned.map { it.id })
        }
        runAsAdmin { assertEquals(HttpStatusCode.OK, client.deleteRecipeAsAdminRaw(theirs).status) }

        runAsUser1 {
            val answer = client.syncOffline()
            assertEquals(emptyList(), answer.pinned)
            assertEquals(emptyList(), answer.cookbooks.single().recipes)
        }
    }

    @Test
    fun `a cookbook comes with every row, past the twenty its own route pages at`() = runTestAsUser {
        val me = userService.getEntityById(idOf(USER1))
        val cookbook = client.createCookbook(sampleCookbookDto).id
        repeat(25) { index ->
            val recipe = recipeService.createRecipe(recipeDTO.copy(title = "recipe $index"), me)
            cookbookService.addRecipeToCookbook(cookbook, recipe.id, me.id)
        }

        assertEquals(25, client.syncOffline().cookbooks.single().recipes.size)
    }

    // ---------------------------------------------------------------- what is sent

    @Test
    fun `an edit sends that recipe again, and only it`() = runTestAsUser {
        val edited = client.createRecipe(recipeDTO.copy(title = "before")).id
        client.createRecipe(recipeDTO.copy(title = "untouched"))
        val held = heldOf(client.syncOffline().recipes)

        client.updateRecipe(edited, recipeDTO.copy(title = "after"))

        val sent = client.syncOffline(held).recipes.single()
        assertEquals(edited, sent.id)
        assertEquals("after", sent.title)
        assertNotEquals(held.single { it.id == edited }.revision, sent.revision)
    }

    /**
     * The case the modification date missed: neither picture goes through the editor's save,
     * so a copy taken before either kept naming the old file.
     */
    @Test
    fun `a new step picture, and a new recipe picture, each send the recipe again`() = runTestAsUser {
        val recipe = client.createRecipe(recipeDTO)
        var held = heldOf(client.syncOffline().recipes)

        assertEquals(HttpStatusCode.OK, client.uploadStepImage(recipe.steps.first().id!!).status)
        val afterStep = client.syncOffline(held).recipes.single()
        assertEquals(1, afterStep.steps.first().imageVersion)
        held = heldOf(listOf(afterStep))

        assertEquals(HttpStatusCode.OK, client.uploadRecipeImage(recipe.id).status)
        val afterPicture = client.syncOffline(held).recipes.single()
        assertEquals(1, afterPicture.version)
    }

    @Test
    fun `absorbing a custom ingredient sends every recipe it rewrote again`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(
            title = "with yuzu",
            ingredients = mutableListOf(
                RecipeDTO.RecipeIngredientDTO(customName = "Yuzu", unit = AmountUnit.GRAM, amount = 5f),
            ),
        )).id
        val held = heldOf(client.syncOffline().recipes)
        val ingredient = client.createIngredient(IngredientDTO(
            name = mapOf(Locale.EN to "yuzu"),
            type = IngredientType.FRUIT,
            calories = 53,
        ))

        client.absorbCustomIngredient(ingredient.id, "yuzu")

        val sent = client.syncOffline(held).recipes.single()
        assertEquals(recipe, sent.id)
        assertEquals(ingredient.id, sent.ingredients.single().id)
    }

    @Test
    fun `a copy from before revisions existed comes back whole`() = runTestAsUser {
        val recipe = client.createRecipe(recipeDTO).id
        val sent = client.syncOffline(listOf(OfflineSyncDTO.HeldRecipe(recipe, revision = null))).recipes
        assertEquals(listOf(recipe), sent.map { it.id })
    }

    @Test
    fun `the recipes come a page at a time, and the pages add up to all of them`() = runTestAsUser {
        val me = userService.getEntityById(idOf(USER1))
        val total = OfflineSyncService.RECIPES_PER_RESPONSE + 5
        repeat(total) { recipeService.createRecipe(recipeDTO.copy(title = "recipe $it"), me) }

        val answers = client.syncOfflineFully()

        assertEquals(listOf(OfflineSyncService.RECIPES_PER_RESPONSE, 5), answers.map { it.recipes.size })
        assertEquals(listOf(true, false), answers.map { it.hasMore })
        assertEquals(total, answers.flatMap { it.recipes }.map { it.id }.toSet().size)
    }
}
