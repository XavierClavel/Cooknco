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
