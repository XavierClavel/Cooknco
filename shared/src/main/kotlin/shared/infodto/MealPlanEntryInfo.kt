package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.MealSlot

/**
 * One dish in the meal plan.
 *
 * @param date ISO `yyyy-MM-dd` — see [shared.dto.MealPlanEntryDTO].
 * @param position where it sits among the dishes of the same meal, smallest first.
 * @param title always set. For a recipe the caller can still open, its current title; for
 *   one they can no longer open — deleted, hidden by a moderator, or behind an account that
 *   went private — the title it had when it was planned.
 * @param recipe the recipe to open, or null when there is none to open: a dish that never
 *   was one, or a recipe that has since gone out of the caller's reach. The entry stays
 *   either way, so a plan never loses a meal because somebody else changed their recipe.
 */
@Serializable
data class MealPlanEntryInfo(
    val id: Long,
    val date: String,
    val slot: MealSlot,
    val position: Int,
    val title: String,
    val servings: Int? = null,
    val recipe: MealPlanRecipe? = null,
)

/** Just enough of a planned recipe to draw its picture and to open it. */
@Serializable
data class MealPlanRecipe(
    val id: Long,
    /** The picture's version, as `RecipeOverview.version`. */
    val version: Long,
)
