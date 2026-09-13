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
     * Steps are compared field by field rather than by equality on the list, because a step's
     * ingredients come back in the recipe's order whatever order they were sent in, and a
     * position naming no ingredient is dropped rather than refused.
     */
    fun compareToDTO(dto: RecipeDTO): Boolean {
        fun RecipeDTO.RecipeStepDTO.stated() = Triple(
            text,
            durationSeconds,
            ingredients.map { it.index to it.amount }.sortedBy { it.first },
        )

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