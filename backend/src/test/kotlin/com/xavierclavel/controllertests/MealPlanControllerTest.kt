package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.TestBuilderWrapper
import com.xavierclavel.models.query.QMealPlanEntry
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.deleteMyAccount
import main.com.xavierclavel.utils.deleteRecipe
import main.com.xavierclavel.utils.editMealPlanEntry
import main.com.xavierclavel.utils.editMealPlanEntryRaw
import main.com.xavierclavel.utils.hideRecipe
import main.com.xavierclavel.utils.listMealPlan
import main.com.xavierclavel.utils.listMealPlanRaw
import main.com.xavierclavel.utils.mealPlanSuggestions
import main.com.xavierclavel.utils.planMeal
import main.com.xavierclavel.utils.planMealRaw
import main.com.xavierclavel.utils.recipeDTO
import main.com.xavierclavel.utils.removeMealPlanEntryRaw
import main.com.xavierclavel.utils.unhideRecipe
import main.com.xavierclavel.utils.updateRecipe
import org.junit.jupiter.api.Test
import shared.dto.MEAL_PLAN_MAX_SERVINGS
import shared.dto.MEAL_PLAN_TITLE_MAX_LENGTH
import shared.dto.MealPlanEntryDTO
import shared.dto.MealPlanEntryEditDTO
import shared.dto.UserSettingsDTO
import shared.enums.MealSlot
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The meal plan: one account's dishes by day and meal, premium, and private.
 *
 * Most of what is worth holding is what happens to a planned recipe that somebody *else*
 * changes — deleted, hidden, renamed, or gone with its author's account. The plan must keep
 * the meal through all of it, and must never be the thing that stops the other person doing
 * what they did. See `MealPlanEntry`.
 */
class MealPlanControllerTest : ApplicationTest() {

    private val monday = "2026-10-12"
    private val tuesday = "2026-10-13"
    private val wednesday = "2026-10-14"
    private val sunday = "2026-10-18"

    private fun grantPremiumForever(mail: String) {
        userService.getEntityById(userService.findByMail(mail).id).grantPremiumForever().update()
    }

    private fun dish(title: String, date: String = monday, slot: MealSlot = MealSlot.DINNER, servings: Int? = null) =
        MealPlanEntryDTO(date = date, slot = slot, title = title, servings = servings)

    private fun recipe(id: Long, date: String = monday, slot: MealSlot = MealSlot.DINNER, servings: Int? = null) =
        MealPlanEntryDTO(date = date, slot = slot, recipeId = id, servings = servings)

    // ----------------------------------------------------------- authorisation

    @Test
    fun `the meal plan is closed to an account with no subscription`() = runTest {
        runAsUser1 {
            client.listMealPlanRaw(monday, sunday).apply {
                assertEquals(HttpStatusCode.Forbidden, status)
                assertContains(bodyAsText(), "premium_required")
            }
            client.planMealRaw(dish("Leftovers")).apply {
                assertEquals(HttpStatusCode.Forbidden, status)
                assertContains(bodyAsText(), "premium_required")
            }
        }
        assertEquals(0, QMealPlanEntry().findCount())
    }

    @Test
    fun `the meal plan is closed to anonymous callers`() = runTest {
        client.listMealPlanRaw(monday, sunday).apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.planMealRaw(dish("Leftovers")).apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    /**
     * A plan made while the grant ran is kept when it ends — the gate closes the door rather
     * than emptying the room — and is there again when the grant comes back.
     */
    @Test
    fun `a plan outlives the grant it was made under`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            client.planMeal(dish("Leftovers"))
            userService.revokePremium(userService.findByMail(USER1).id)
            client.listMealPlanRaw(monday, sunday).apply { assertEquals(HttpStatusCode.Forbidden, status) }
            grantPremiumForever(USER1)
            assertEquals(listOf("Leftovers"), client.listMealPlan(monday, sunday).map { it.title })
        }
    }

    // ----------------------------------------------------------- planning

    @Test
    fun `a dish that is not a recipe is planned under the words it was given`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val entry = client.planMeal(dish("  Leftovers  ", slot = MealSlot.LUNCH, servings = 2))
            assertEquals("Leftovers", entry.title)
            assertEquals(monday, entry.date)
            assertEquals(MealSlot.LUNCH, entry.slot)
            assertEquals(2, entry.servings)
            assertNull(entry.recipe)
            assertEquals(listOf(entry), client.listMealPlan(monday, sunday))
        }
    }

    /**
     * The servings are copied off the recipe when it is planned, not read through it: a meal
     * planned for four is still for four after the recipe is rewritten for six.
     */
    @Test
    fun `a planned recipe is shown under its title and makes what the recipe makes`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val lasagne = client.createRecipe(recipeDTO.copy(title = "Lasagne", yield = 4))
            val planned = client.planMeal(recipe(lasagne.id))
            assertEquals("Lasagne", planned.title)
            assertEquals(4, planned.servings)
            assertEquals(lasagne.id, planned.recipe?.id)

            assertEquals(6, client.planMeal(recipe(lasagne.id, date = tuesday, servings = 6)).servings)

            client.updateRecipe(lasagne.id, recipeDTO.copy(title = "Lasagne", yield = 6))
            assertEquals(4, client.listMealPlan(monday, monday).single().servings)
        }
    }

    /**
     * Nothing bounds a recipe's yield, and an edit refuses servings past the plan's bound — so a
     * yield past it is not copied, or the dish it made could never be moved again.
     */
    @Test
    fun `a yield the plan could not take back is left unsaid`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val crumbs = client.createRecipe(recipeDTO.copy(title = "Crumbs", yield = MEAL_PLAN_MAX_SERVINGS + 1))
            val planned = client.planMeal(recipe(crumbs.id))
            assertNull(planned.servings)
            client.editMealPlanEntry(planned.id, MealPlanEntryEditDTO(date = tuesday, slot = MealSlot.LUNCH, servings = planned.servings))
        }
    }

    @Test
    fun `the plan lists the days asked for, in the order a day is eaten in`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            client.planMeal(dish("Soup", date = tuesday, slot = MealSlot.DINNER))
            client.planMeal(dish("Bread", date = tuesday, slot = MealSlot.DINNER))
            client.planMeal(dish("Cake", date = tuesday, slot = MealSlot.SNACK))
            client.planMeal(dish("Porridge", date = tuesday, slot = MealSlot.BREAKFAST))
            client.planMeal(dish("Salad", date = monday, slot = MealSlot.LUNCH))
            client.planMeal(dish("Before", date = "2026-10-11"))
            client.planMeal(dish("After", date = "2026-10-19"))

            assertEquals(
                listOf("Salad", "Porridge", "Cake", "Soup", "Bread"),
                client.listMealPlan(monday, sunday).map { it.title },
            )
            assertEquals(listOf("Salad"), client.listMealPlan(monday, monday).map { it.title })
        }
    }

    @Test
    fun `a range is refused when it is backwards, too long, or not dates`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            listOf(
                sunday to monday,
                "2026-10-01" to "2026-12-31",
                "12/10/2026" to sunday,
                null to sunday,
            ).forEach { (from, to) ->
                client.listMealPlanRaw(from, to).apply {
                    assertEquals(HttpStatusCode.BadRequest, status, "$from..$to")
                    assertTrue(bodyAsText().contains("meal_plan_range_invalid") || bodyAsText().contains("meal_plan_date_invalid"))
                }
            }
            // The longest a range may be, and still answered
            client.listMealPlan("2026-10-01", "2026-12-01")
        }
    }

    @Test
    fun `a dish is refused with no name, too long a name, a bad date, or servings past reason`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            mapOf(
                dish("   ") to "meal_plan_title_empty",
                MealPlanEntryDTO(date = monday, slot = MealSlot.DINNER) to "meal_plan_title_empty",
                dish("x".repeat(MEAL_PLAN_TITLE_MAX_LENGTH + 1)) to "meal_plan_title_too_long",
                dish("Soup", date = "2026-02-30") to "meal_plan_date_invalid",
                dish("Soup", servings = 0) to "meal_plan_servings_invalid",
                dish("Soup", servings = MEAL_PLAN_MAX_SERVINGS + 1) to "meal_plan_servings_invalid",
            ).forEach { (dto, cause) ->
                client.planMealRaw(dto).apply {
                    assertEquals(HttpStatusCode.BadRequest, status, dto.toString())
                    assertContains(bodyAsText(), cause)
                }
            }
            assertTrue(client.listMealPlan("2026-10-01", "2026-12-01").isEmpty())
        }
    }

    // ----------------------------------------------------------- editing

    @Test
    fun `a dish moved to another meal goes to the end of it, and one left in place keeps its place`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val soup = client.planMeal(dish("Soup"))
            client.planMeal(dish("Bread"))
            val salad = client.planMeal(dish("Salad", slot = MealSlot.LUNCH))

            client.editMealPlanEntry(salad.id, MealPlanEntryEditDTO(date = monday, slot = MealSlot.DINNER, servings = 3))
            assertEquals(listOf("Soup", "Bread", "Salad"), client.listMealPlan(monday, monday).map { it.title })

            val edited = client.editMealPlanEntry(soup.id, MealPlanEntryEditDTO(date = monday, slot = MealSlot.DINNER, servings = 5, title = "Leek soup"))
            assertEquals("Leek soup", edited.title)
            assertEquals(5, edited.servings)
            assertEquals(soup.position, edited.position)

            client.editMealPlanEntry(soup.id, MealPlanEntryEditDTO(date = tuesday, slot = MealSlot.LUNCH))
            assertEquals(listOf("Bread", "Salad"), client.listMealPlan(monday, monday).map { it.title })
            client.listMealPlan(tuesday, tuesday).single().let {
                assertEquals("Leek soup", it.title)
                assertEquals(MealSlot.LUNCH, it.slot)
                assertNull(it.servings, "null servings clears them")
            }

            client.editMealPlanEntryRaw(soup.id, MealPlanEntryEditDTO(date = tuesday, slot = MealSlot.LUNCH, title = " ")).apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertContains(bodyAsText(), "meal_plan_title_empty")
            }
        }
    }

    @Test
    fun `a planned recipe keeps its name whatever title an edit sends`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val lasagne = client.createRecipe(recipeDTO.copy(title = "Lasagne"))
            val planned = client.planMeal(recipe(lasagne.id))
            val edited = client.editMealPlanEntry(planned.id, MealPlanEntryEditDTO(date = tuesday, slot = MealSlot.LUNCH, title = "Pizza"))
            assertEquals("Lasagne", edited.title)
            assertEquals(lasagne.id, edited.recipe?.id)
        }
    }

    @Test
    fun `a dish is removed`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val soup = client.planMeal(dish("Soup"))
            client.planMeal(dish("Bread"))
            client.removeMealPlanEntryRaw(soup.id).apply { assertEquals(HttpStatusCode.OK, status) }
            assertEquals(listOf("Bread"), client.listMealPlan(monday, monday).map { it.title })
            client.removeMealPlanEntryRaw(soup.id).apply { assertEquals(HttpStatusCode.NotFound, status) }
        }
    }

    // ----------------------------------------------------------- privacy

    @Test
    fun `another account's plan is not there at all`() = runTest {
        grantPremiumForever(USER1)
        grantPremiumForever(USER2)
        val soup = runAsUser1Returning { client.planMeal(dish("Soup")) }
        runAsUser2 {
            assertTrue(client.listMealPlan(monday, sunday).isEmpty())
            client.editMealPlanEntryRaw(soup.id, MealPlanEntryEditDTO(date = tuesday, slot = MealSlot.LUNCH)).apply {
                assertEquals(HttpStatusCode.NotFound, status)
                assertContains(bodyAsText(), "meal_plan_entry_not_found")
            }
            client.removeMealPlanEntryRaw(soup.id).apply { assertEquals(HttpStatusCode.NotFound, status) }
        }
        runAsUser1 {
            client.listMealPlan(monday, monday).single().let {
                assertEquals("Soup", it.title)
                assertEquals(MealSlot.DINNER, it.slot)
            }
        }
    }

    @Test
    fun `a recipe the caller cannot open cannot be planned`() = runTest {
        setupTestUser("private@mail.com", UserSettingsDTO(isAccountPublic = false))
        val hidden = runAsReturning("private@mail.com") { client.createRecipe(recipeDTO.copy(title = "Secret")) }
        grantPremiumForever(USER1)
        runAsUser1 {
            listOf(hidden.id, hidden.id + 1000).forEach { id ->
                client.planMealRaw(recipe(id)).apply {
                    assertEquals(HttpStatusCode.NotFound, status)
                    assertContains(bodyAsText(), "recipe_not_found")
                }
            }
        }
        assertEquals(0, QMealPlanEntry().findCount())
    }

    // ----------------------------------------------------------- a recipe changing under the plan

    @Test
    fun `a planned recipe its owner deletes stays in the plan under its old name, and can still be moved`() = runTest {
        grantPremiumForever(USER1)
        val stew = runAsUser2Returning { client.createRecipe(recipeDTO.copy(title = "Stew", yield = 2)) }
        val planned = runAsUser1Returning { client.planMeal(recipe(stew.id)) }
        runAsUser2 { client.deleteRecipe(stew.id) }
        runAsUser1 {
            client.listMealPlan(monday, monday).single().let {
                assertEquals("Stew", it.title)
                assertEquals(2, it.servings)
                assertNull(it.recipe, "there is no recipe left to open")
            }
            // The edit does not re-check the recipe, which is what lets a dish whose recipe has
            // gone still be moved — see MealPlanEntryEditDTO
            client.editMealPlanEntry(planned.id, MealPlanEntryEditDTO(date = tuesday, slot = MealSlot.LUNCH, servings = 2))
            assertEquals("Stew", client.listMealPlan(tuesday, tuesday).single().title)
        }
    }

    /**
     * The app is shown such a dish as words and offers to rename it like one, so the rename has
     * to land — and once it has, the name is the cook's and the recipe coming back must not
     * take it over again.
     */
    @Test
    fun `a dish whose recipe is out of reach renames like words, and lets the recipe go`() = runTest {
        grantPremiumForever(USER1)
        val stew = runAsUser2Returning { client.createRecipe(recipeDTO.copy(title = "Stew")) }
        val planned = runAsUser1Returning { client.planMeal(recipe(stew.id)) }
        runAsAdmin { client.hideRecipe(stew.id) }
        runAsUser1 {
            val renamed = client.editMealPlanEntry(planned.id, MealPlanEntryEditDTO(date = monday, slot = MealSlot.DINNER, title = "Granny's stew"))
            assertEquals("Granny's stew", renamed.title)
        }
        runAsAdmin { client.unhideRecipe(stew.id) }
        runAsUser1 {
            client.listMealPlan(monday, monday).single().let {
                assertEquals("Granny's stew", it.title)
                assertNull(it.recipe)
            }
        }
    }

    /** Resolved when the plan is read, so it comes back on its own when the recipe does. */
    @Test
    fun `a planned recipe a moderator hides loses its link until it is shown again`() = runTest {
        grantPremiumForever(USER1)
        val stew = runAsUser2Returning { client.createRecipe(recipeDTO.copy(title = "Stew")) }
        runAsUser1 { client.planMeal(recipe(stew.id)) }
        runAsAdmin { client.hideRecipe(stew.id) }
        runAsUser1 {
            client.listMealPlan(monday, monday).single().let {
                assertEquals("Stew", it.title)
                assertNull(it.recipe)
            }
        }
        runAsAdmin { client.unhideRecipe(stew.id) }
        runAsUser1 { assertEquals(stew.id, client.listMealPlan(monday, monday).single().recipe?.id) }
    }

    @Test
    fun `a planned recipe is shown under its current name`() = runTest {
        grantPremiumForever(USER1)
        val stew = runAsUser2Returning { client.createRecipe(recipeDTO.copy(title = "Stew")) }
        runAsUser1 { client.planMeal(recipe(stew.id)) }
        runAsUser2 { client.updateRecipe(stew.id, recipeDTO.copy(title = "Beef stew")) }
        runAsUser1 { assertEquals("Beef stew", client.listMealPlan(monday, monday).single().title) }
    }

    /**
     * An account's deletion takes its recipes for good rather than softly, so the plan's
     * pointer has to let go of them in the database — `ON DELETE SET NULL`. Were it the
     * default `RESTRICT`, somebody planning this recipe would make its author unable to leave.
     */
    @Test
    fun `a planned recipe whose author deletes their account stays in the plan`() = runTest {
        setupTestUser("author@mail.com")
        val stew = runAsReturning("author@mail.com") { client.createRecipe(recipeDTO.copy(title = "Stew")) }
        grantPremiumForever(USER1)
        runAsUser1 { client.planMeal(recipe(stew.id)) }
        runAs("author@mail.com", password) { client.deleteMyAccount() }
        assertNull(userService.findEntityByMail("author@mail.com"))
        runAsUser1 {
            client.listMealPlan(monday, monday).single().let {
                assertEquals("Stew", it.title)
                assertNull(it.recipe)
            }
        }
    }

    @Test
    fun `deleting an account takes its plan with it`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            val mine = client.createRecipe(recipeDTO.copy(title = "Stew"))
            client.planMeal(recipe(mine.id))
            client.planMeal(dish("Leftovers"))
            client.deleteMyAccount()
        }
        assertNull(userService.findEntityByMail(USER1))
        assertEquals(0, QMealPlanEntry().findCount())
    }

    // ----------------------------------------------------------- suggestions

    @Test
    fun `suggestions are the dishes typed before, once each and newest first`() = runTest {
        grantPremiumForever(USER1)
        grantPremiumForever(USER2)
        runAsUser2 { client.planMeal(dish("Somebody else's soup")) }
        runAsUser1 {
            val stew = client.createRecipe(recipeDTO.copy(title = "Stew"))
            client.planMeal(dish("Leftovers", date = monday))
            client.planMeal(dish("Pizza night", date = tuesday))
            client.planMeal(dish("leftovers", date = wednesday))
            client.planMeal(recipe(stew.id, date = wednesday))

            assertEquals(listOf("leftovers", "Pizza night"), client.mealPlanSuggestions())
            assertEquals(listOf("Pizza night"), client.mealPlanSuggestions("PIZ"))
            assertTrue(client.mealPlanSuggestions("stew").isEmpty(), "a recipe is found as a recipe, not suggested as words")
        }
    }

    // ----------------------------------------------------------- helpers

    /** [runAs], handing back what was made under the account so another one can use it. */
    private suspend fun <T> TestBuilderWrapper.runAsReturning(mail: String, block: suspend TestBuilderWrapper.() -> T): T {
        val result = mutableListOf<T>()
        runAs(mail, password) { result += block() }
        return result.single()
    }

    private suspend fun <T> TestBuilderWrapper.runAsUser1Returning(block: suspend TestBuilderWrapper.() -> T): T =
        runAsReturning(USER1, block)

    private suspend fun <T> TestBuilderWrapper.runAsUser2Returning(block: suspend TestBuilderWrapper.() -> T): T =
        runAsReturning(USER2, block)
}
