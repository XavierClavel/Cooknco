package shared.dto

import shared.enums.AmountUnit
import shared.enums.DishClass
import kotlinx.serialization.*

@Serializable
data class RecipeDTO (
    val title: String,
    val description: String = "",
    val dishClass: DishClass = DishClass.MAIN_DISH,

    val yield: Int? = null,
    val preparationTime: Int? = null,
    val cookingTime: Int? = null,
    val cookingTemperature: Int? = null,

    val ingredients: MutableList<RecipeIngredientDTO> = mutableListOf(),
    val steps: MutableList<RecipeStepDTO> = mutableListOf(),

    val tips: String = "",
) {
    /**
     * A recipe ingredient is either a reference to the ingredients table (id) or free text
     * entered by the user (customName). Exactly one of the two is set.
     */
    /**
     * One step, and how long it takes.
     *
     * [durationSeconds] is nullable and means "this step has no timer" rather than "zero" —
     * most steps do not have one, and a zero would be a timer that has already finished. It
     * is what cook mode counts down, so it is stored in seconds even though the editors
     * offer minutes: the durations are read out of the step's own wording ("laisser reposer
     * 30 mn"), and that parse has always produced seconds.
     *
     * The same type carries a step in both directions — into [RecipeDTO] and back out in
     * `RecipeInfo` — because a step is the same thing on the way in and on the way out,
     * unlike an ingredient, whose input is a reference and whose output is a resolved name.
     * It is also what lets `RecipeInfo.compareToDTO` stay a plain equality check.
     */
    @Serializable
    data class RecipeStepDTO (
        val text: String = "",
        val durationSeconds: Int? = null,
        /**
         * Which of the recipe's own ingredients this step uses, and how much of each.
         *
         * Empty for most steps: a step that says nothing about ingredients is the normal case.
         */
        val ingredients: List<RecipeStepIngredientDTO> = emptyList(),
    )

    /**
     * One of the recipe's ingredients, used by one step.
     *
     * [index] is a position in [RecipeDTO.ingredients], not an id: on the way *in* an
     * ingredient row has no identity to point at, because a save replaces the whole list —
     * the rows a client sends have not been given ids yet and the ones they replace are about
     * to lose theirs. The position is the only thing both sides can agree on inside one
     * request. The server turns them into rows once both lists exist and back into positions
     * on the way out, so a client never sees the join table.
     *
     * [amount] is what the author said, in the ingredient's own unit, and null when they said
     * nothing. There is no unit of its own, because "200 g of the 500 g of flour" is the only
     * sensible reading and a step measuring the same ingredient in a different unit would be a
     * conversion nobody asked for.
     *
     * Null is not resolved to a number anywhere on the way through. What a blank comes to
     * depends on what the recipe's other steps said about the same ingredient, and every
     * reader already holds the whole recipe — so it is worked out where it is displayed, and
     * the wire carries only what was actually stated. A second, derived field here would be
     * one that must never be written back, which is a trap a client falls into by doing the
     * obvious thing: read the recipe, put the numbers in the boxes, save.
     */
    @Serializable
    data class RecipeStepIngredientDTO (
        val index: Int,
        val amount: Float? = null,
    )

    @Serializable
    data class RecipeIngredientDTO (
        val id: Long? = null,
        val customName: String? = null,
        val unit: AmountUnit = AmountUnit.NONE,
        val amount: Float? = null,
        val complement: String? = null,
    ) {
        val isCustom: Boolean get() = id == null
    }
}

const val CUSTOM_INGREDIENT_NAME_MAX_LENGTH = 50
