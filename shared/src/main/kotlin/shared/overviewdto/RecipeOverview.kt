package shared.overviewdto

import shared.enums.DishClass
import kotlinx.serialization.*

@Serializable
data class RecipeOverview(
    val id: Long,
    val version: Long,
    val title: String,
    val dishClass: DishClass,
    val owner: UserOverview,
    val likesCount: Int,
    val creationDate: Long,
    /**
     * When the recipe was last edited, as `Recipe.modificationDate`.
     *
     * Here so that a client holding a copy can tell whether it is still current without
     * fetching the recipe to find out — which is what the app's offline store diffs on
     * (`docs/offline-recipe-access.md`). [version] cannot answer that: it is the *image*
     * version and moves only when the picture does.
     *
     * Defaulted, like every field added to a DTO here, so a client that predates it parses
     * the response rather than failing on it.
     */
    val editionDate: Long = 0,
    /** Set when a moderator has hidden the recipe; only its owner and admins ever see it. */
    val isHidden: Boolean = false,
)