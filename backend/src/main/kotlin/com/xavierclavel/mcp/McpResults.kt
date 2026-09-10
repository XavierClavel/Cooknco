package com.xavierclavel.mcp

import kotlinx.serialization.Serializable
import shared.infodto.CookbookInfo
import shared.infodto.CookbookRecipeInfo
import shared.infodto.UserInfo

/**
 * What the tools answer with.
 *
 * A tool result is text a model reads, so these carry the API's own DTOs — the same shapes the
 * app is served, no second vocabulary to keep in step — wrapped in just enough context for the
 * answer to stand on its own: which page came back, and, for a write, what actually changed.
 */
@Serializable
data class PagedResult<T>(
    val page: Int,
    val size: Int,
    val results: List<T>,
)

@Serializable
data class CookbookWithRecipes(
    val cookbook: CookbookInfo,
    val page: Int,
    val size: Int,
    val recipes: List<CookbookRecipeInfo>,
)

@Serializable
data class IngredientPage(
    val page: Int,
    val size: Int,
    /** Matches in total, which is more than this page holds. */
    val total: Int,
    val results: List<McpIngredient>,
)

@Serializable
data class McpIngredient(
    val id: Long,
    val name: String,
    val type: String,
    /** The unit the app preselects for this ingredient, when it has one. */
    val defaultUnit: String?,
    /** Every unit this ingredient may be measured in; anything else is rejected on write. */
    val allowedUnits: List<String>,
)

@Serializable
data class UserResult(
    val user: UserInfo,
    /** Whether this is the account the calling token belongs to. */
    val isAuthenticatedUser: Boolean,
)

@Serializable
data class DeletionResult(
    val recipeId: Long,
    val title: String,
    /** False when the recipe was only withdrawn, its row kept for what still references it. */
    val erased: Boolean,
    val detail: String,
)

@Serializable
data class LikeResult(
    val recipeId: Long,
    val title: String,
    val liked: Boolean,
    /** False when the recipe was already in the requested state. */
    val changed: Boolean,
    val detail: String,
)

@Serializable
data class CookbookAdditionResult(
    val cookbook: CookbookInfo,
    val recipeId: Long,
    val title: String,
)
