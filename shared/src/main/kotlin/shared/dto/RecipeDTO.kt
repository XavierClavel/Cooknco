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
    val steps: MutableList<String> = mutableListOf(),

    val tips: String = "",
) {
    /**
     * A recipe ingredient is either a reference to the ingredients table (id) or free text
     * entered by the user (customName). Exactly one of the two is set.
     */
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
