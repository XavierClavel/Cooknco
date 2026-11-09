package com.xavierclavel.cooknco.network.dto

import kotlinx.serialization.Serializable

/**
 * `POST /offline/sync`'s body — `shared.dto.OfflineSyncDTO`. What the phone holds, so the
 * answer carries only what changed.
 */
@Serializable
data class OfflineSyncRequest(
    val held: List<HeldRecipe> = emptyList(),
)

/**
 * One copy on the phone, at the revision it was read at. Null for a copy written before the
 * index recorded revisions: it matches nothing, so the recipe comes back whole, once.
 */
@Serializable
data class HeldRecipe(
    val id: Long,
    val revision: Long? = null,
)

/** `shared.infodto.OfflineSyncInfo`. See the server's KDoc for what each part promises. */
@Serializable
data class OfflineSyncResponse(
    val pinned: List<OfflinePinInfo> = emptyList(),
    val recipes: List<RecipeInfo> = emptyList(),
    val hasMore: Boolean = false,
    val notes: List<OfflineNotesInfo> = emptyList(),
    val own: List<RecipeOverview> = emptyList(),
    val liked: List<RecipeOverview> = emptyList(),
    val cookbooks: List<OfflineCookbookInfo> = emptyList(),
)

/**
 * A recipe the phone should hold.
 *
 * [sets] is strings rather than [com.xavierclavel.cooknco.data.PinSet]: a collection a newer
 * server names and this build does not know would otherwise fail the whole answer, where it
 * only needs to be ignored.
 */
@Serializable
data class OfflinePinInfo(
    val id: Long,
    val revision: Long,
    val sets: Set<String> = emptySet(),
    val isLiked: Boolean = false,
)

@Serializable
data class OfflineNotesInfo(
    val recipeId: Long,
    val notes: String,
)

@Serializable
data class OfflineCookbookInfo(
    val cookbook: CookbookInfo,
    val recipes: List<CookbookRecipeInfo> = emptyList(),
)
