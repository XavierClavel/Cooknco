package main.com.xavierclavel.other

import com.xavierclavel.services.MealPlanService
import main.com.xavierclavel.utils.FetchPlanTest
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.planMeal
import main.com.xavierclavel.utils.recipeDTO
import org.junit.jupiter.api.Test
import org.koin.test.inject
import shared.dto.MealPlanEntryDTO
import shared.enums.MealSlot

/**
 * Guards the meal plan's read against N+1.
 *
 * Each planned recipe is shown under its current title and picture, and only if the reader may
 * still open it — a visibility check per recipe, which is exactly the shape that turns into a
 * query per row. `MealPlanService.describeAll` asks about all of them at once instead.
 */
class MealPlanFetchPlanTest : FetchPlanTest() {
    private val mealPlanService: MealPlanService by inject()

    @Test
    fun `reading a week costs the same whether it holds one dish or several`() = runTest {
        userService.getEntityById(userService.findByMail(USER1).id).grantPremiumForever().update()
        val stew = runAsUser2Returning { client.createRecipe(recipeDTO.copy(title = "Stew")).id }
        runAsUser1 {
            val me = userService.findByMail(USER1).id
            client.planMeal(MealPlanEntryDTO(date = "2026-10-12", slot = MealSlot.DINNER, recipeId = stew))
            assertQueryCountDoesNotGrow(
                what = "MealPlanService.list",
                read = { mealPlanService.list(me, "2026-10-12", "2026-10-18") },
                grow = {
                    repeat(3) { day ->
                        val mine = client.createRecipe(recipeDTO.copy(title = "Mine $day")).id
                        client.planMeal(MealPlanEntryDTO(date = "2026-10-1${3 + day}", slot = MealSlot.LUNCH, recipeId = mine))
                        client.planMeal(MealPlanEntryDTO(date = "2026-10-1${3 + day}", slot = MealSlot.DINNER, title = "Leftovers"))
                    }
                },
            )
        }
    }

    private suspend fun <T> com.xavierclavel.TestBuilderWrapper.runAsUser2Returning(
        block: suspend com.xavierclavel.TestBuilderWrapper.() -> T,
    ): T {
        val result = mutableListOf<T>()
        runAsUser2 { result += block() }
        return result.single()
    }
}
