package shared.infodto

import kotlinx.serialization.Serializable

@Serializable
data class CookbookRecipeInfo(
    val id: Long,
    val title: String,
    /** The recipe's picture version, as `RecipeOverview.version`: what its image URL is named by. */
    val version: Long,
    val addedById: Long,
    val addedByUsername: String,
    val additionDate: Long,
)