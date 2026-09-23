package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import kotlinx.serialization.Serializable

/**
 * Which of the three lists is keeping a recipe on this phone.
 *
 * A recipe is commonly in more than one — your own recipe, in your own cookbook, that you
 * also liked — and it leaves the store when it is in *none*. Kept as a set rather than as a
 * single reason so that unliking a recipe of your own does not throw away a copy your
 * cookbook still wants.
 */
@Serializable
enum class PinSet { OWN, LIKED, COOKBOOK }

/**
 * One pinned recipe, as the index knows it — which is to say, just enough to tell whether the
 * copy on disk is still the right one.
 *
 * [editionDate] is what says the recipe changed and [imageVersion] is what says the picture
 * did. They move independently: an edit that does not touch the photograph leaves the image
 * alone, and a new photograph is not an edit. Diffing on only one of them would either
 * refetch every recipe whose picture changed or miss every edit that left it alone.
 */
@Serializable
data class OfflineEntry(
    val id: Long,
    val editionDate: Long = 0,
    val imageVersion: Long = 0,
    val sets: Set<PinSet> = emptySet(),
)

/**
 * What this phone is holding, and when it last managed to check.
 *
 * Written **last** by a sync, so an interrupted one leaves an index that under-claims rather
 * than one naming recipes that were never written. Under-claiming costs a refetch; the other
 * way round is a screen opening on a file that is not there.
 */
@Serializable
data class OfflineIndex(
    /**
     * Whose recipes these are.
     *
     * The store is wiped on sign-out, so this should never disagree with the session — but if
     * it ever does, it is the one thing that says the copies belong to somebody else, and the
     * sync treats a mismatch as an empty store rather than as a diff to apply.
     */
    val userId: Long = 0,
    /** Epoch seconds. What the offline banner prints, so the cook knows how old this is. */
    val syncedAt: Long = 0,
    val recipes: List<OfflineEntry> = emptyList(),
    val cookbookIds: List<Long> = emptyList(),
) {
    val byId: Map<Long, OfflineEntry> get() = recipes.associateBy { it.id }
}

/**
 * A pinned recipe as a screen needs it.
 *
 * The notes and the like are stored with the recipe rather than beside it because the recipe
 * screen draws all three at once: a copy that has the method but not the cook's own
 * "35 min not 25 in our oven" is a screen with an empty box where their writing was, which
 * reads as having lost it.
 */
@Serializable
data class OfflineRecipe(
    val recipe: RecipeInfo,
    val notes: String? = null,
    val isLiked: Boolean = false,
)

/** A pinned cookbook, and the recipe rows its screen lists. */
@Serializable
data class OfflineCookbook(
    val cookbook: CookbookInfo,
    val recipes: List<CookbookRecipeInfo> = emptyList(),
)

/**
 * The recipe lists the profile's two grids draw, kept in the order the server sent them.
 *
 * Overviews rather than ids: a grid needs a title, an owner and a like count, and rebuilding
 * those from the pinned recipes would put them back in whatever order the files happen to be
 * in — which is not the order either grid is sorted by.
 */
@Serializable
data class OfflineLists(
    val own: List<RecipeOverview> = emptyList(),
    val liked: List<RecipeOverview> = emptyList(),
    val cookbooks: List<CookbookInfo> = emptyList(),
)
