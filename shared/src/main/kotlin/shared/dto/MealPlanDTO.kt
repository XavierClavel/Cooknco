package shared.dto

import kotlinx.serialization.Serializable
import shared.enums.MealSlot

/**
 * A dish to put in the meal plan: a recipe, or anything else named in a few words.
 *
 * @param date ISO `yyyy-MM-dd`. A day, not an instant — unlike every other date this API
 *   carries, which are epoch seconds. A dinner planned for Tuesday is on Tuesday wherever the
 *   phone is, and an instant converted at UTC midnight would move it to Monday evening for
 *   anyone west of Greenwich.
 * @param recipeId the recipe this is, or null for a dish named only by [title]. It has to be
 *   one the caller can open.
 * @param title what the dish is called when it is not a recipe — "Leftovers", "Pizza night".
 *   Ignored when [recipeId] is set: the recipe names itself, and its title is what is kept.
 * @param servings how many it is cooked for. Null on a recipe means as many as the recipe
 *   makes; null on anything else means nobody said.
 */
@Serializable
data class MealPlanEntryDTO(
    val date: String,
    val slot: MealSlot,
    val recipeId: Long? = null,
    val title: String? = null,
    val servings: Int? = null,
)

/**
 * A change to a planned dish: when it is, for how many, and — for one shown as words — what
 * it is called.
 *
 * Which recipe an entry is cannot be changed, only removed and planned again. Allowing it
 * would mean checking the recipe could still be opened on every edit, and a recipe hidden or
 * deleted since it was planned would then refuse to let its entry be moved to another day.
 *
 * @param title applied only to an entry the caller is shown as words: one with no recipe, or
 *   one whose recipe they can no longer open — which the rename then lets go of. Refused blank
 *   as it is on creation. Null leaves it as it is.
 * @param servings stored as sent: null clears it.
 */
@Serializable
data class MealPlanEntryEditDTO(
    val date: String,
    val slot: MealSlot,
    val servings: Int? = null,
    val title: String? = null,
)

/**
 * How long a planned dish's name may be, and the width of `meal_plan_entries.title`.
 *
 * As wide as a recipe's title, because a recipe's title is what the column holds for a
 * planned recipe — it is kept there so the entry outlives the recipe. See `MealPlanEntry`.
 */
const val MEAL_PLAN_TITLE_MAX_LENGTH = 255

/**
 * Past it, the number is a typo rather than a plan. Generous because a recipe's yield is what a
 * planned recipe defaults to, and a yield counts pieces as often as people — a batch of cookies
 * is a hundred and fifty of them.
 */
const val MEAL_PLAN_MAX_SERVINGS = 999
