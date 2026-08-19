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
    val steps: List<String> = listOf(),
    val tips: String = "",

    val creationDate: Long = 0,
    val editionDate: Long? = null,

    val likesCount: Int,

    /** Set when a moderator has hidden the recipe; only its owner and admins ever see it. */
    val isHidden: Boolean = false,

    ) {
    fun compareToDTO(dto: RecipeDTO): Boolean {
        return title == dto.title &&
                description == dto.description &&
                yield == dto.yield &&
                preparationTime == dto.preparationTime &&
                cookingTime == dto.preparationTime &&
                cookingTemperature == dto.cookingTemperature &&
                steps == dto.steps
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