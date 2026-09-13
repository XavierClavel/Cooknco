package shared.infodto

import shared.dto.RecipeDTO
import shared.enums.DishClass
import shared.overviewdto.RecipeOverview
import shared.overviewdto.UserOverview
import kotlinx.serialization.*

@Serializable
data class RecipeInfo (
    val id: Long,
    val version: Long,
    val title: String,
    val dishClass: DishClass,
    val owner: UserOverview,
    val description: String,

    val yield: Int? = null,
    val preparationTime: Int? = null,
    val cookingTime: Int? = null,
    val cookingTemperature: Int? = null,

    val ingredients: List<RecipeIngredientInfo> = listOf(),
    val steps: List<RecipeDTO.RecipeStepDTO> = listOf(),
    val tips: String = "",

    val creationDate: Long = 0,
    val editionDate: Long? = null,

    val likesCount: Int,

    /** Set when a moderator has hidden the recipe; only its owner and admins ever see it. */
    val isHidden: Boolean = false,

    ) {
    /**
     * Whether the server stored what it was sent.
     *
     * Steps are compared on what a client *states* — the words, the timer, and which
     * ingredients the step uses — and not on the amounts of those ingredients, because an
     * amount is not always something the client stated. A step that names an ingredient
     * without a number is saying "unspecified", and the reply works out what that comes to
     * from what the recipe's other steps spelled out (`Recipe.blankStepAmounts`). Comparing
     * it would be asserting that the server failed to do its job.
     */
    fun compareToDTO(dto: RecipeDTO): Boolean {
        fun RecipeDTO.RecipeStepDTO.stated() =
            Triple(text, durationSeconds, ingredients.map { it.index }.sorted())

        return title == dto.title &&
                description == dto.description &&
                yield == dto.yield &&
                preparationTime == dto.preparationTime &&
                cookingTime == dto.cookingTime &&
                cookingTemperature == dto.cookingTemperature &&
                steps.map { it.stated() } == dto.steps.map { it.stated() }
    }

    fun toOverview() = RecipeOverview(
        id = this.id,
        version = this.version,
        title = this.title,
        dishClass = this.dishClass,
        owner = this.owner,
        likesCount = this.likesCount,
        creationDate = this.creationDate,
        isHidden = this.isHidden,
    )
}