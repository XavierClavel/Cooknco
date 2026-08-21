package com.xavierclavel.cooknco.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(val token: String)

@Serializable
data class UserInfo(
    val id: Long,
    val version: Long,
    val username: String,
    val role: String = "USER",
    val joinDate: Long,
    val bio: String,
    val recipesCount: Int,
    val likesCount: Int,
    val cookbooksCount: Int,
    val followersCount: Int,
    val followsCount: Int,
)

@Serializable
data class UserDTO(
    val username: String,
    val password: String? = null,
    val mail: String,
    val bio: String = "",
)

@Serializable
data class RecipeOwner(
    val id: Long,
    val version: Long,
    val username: String,
    val role: String = "USER",
)

@Serializable
data class CookbookInfo(
    val id: Long,
    val version: Long,
    val title: String,
    val description: String = "",
    val recipesCount: Int,
    val usersCount: Int,
    val members: List<RecipeOwner>,
)

@Serializable
data class RecipeOverview(
    val id: Long,
    val version: Long,
    val title: String,
    val owner: RecipeOwner,
    val likesCount: Int,
    val creationDate: Long,
)

@Serializable
data class RecipeIngredientInfo(
    val id: Long,
    val name: String,
    val amount: Float? = null,
    val unit: String,
    val complement: String? = null,
    val type: String,
    val allowAmount: Boolean,
    val allowWeight: Boolean,
    val allowVolume: Boolean,
)

@Serializable
data class CustomIngredientInfo(
    val name: String,
    val amount: Float? = null,
    val unit: String,
)

@Serializable
data class RecipeInfo(
    val id: Long,
    val version: Long,
    val title: String,
    val dishClass: String,
    val owner: RecipeOwner,
    val description: String = "",
    val yield: Int? = null,
    val preparationTime: Int? = null,
    val cookingTime: Int? = null,
    val cookingTemperature: Int? = null,
    val ingredients: List<RecipeIngredientInfo> = emptyList(),
    val customIngredients: List<CustomIngredientInfo> = emptyList(),
    val steps: List<String> = emptyList(),
    val tips: String = "",
    val creationDate: Long,
    val likesCount: Int,
)

@Serializable
data class RecipeSaveDto(
    val title: String,
    val description: String,
    val dishClass: String,
    val yield: Int? = null,
    val preparationTime: Int? = null,
    val cookingTime: Int? = null,
    val cookingTemperature: Int? = null,
    val ingredients: List<RecipeIngredientSaveDto>,
    val customIngredients: List<CustomIngredientSaveDto>,
    val steps: List<String>,
    val tips: String,
)

@Serializable
data class RecipeIngredientSaveDto(
    val id: Long,
    val unit: String,
    val amount: Float? = null,
    val complement: String? = null,
)

@Serializable
data class CustomIngredientSaveDto(
    val name: String,
    val unit: String,
    val amount: Float? = null,
)

@Serializable
data class IngredientSummary(
    val id: Long,
    val name: Map<String, String>,
    val type: String = "",
    val allowAmount: Boolean,
    val allowWeight: Boolean,
    val allowVolume: Boolean,
)

@Serializable
data class IngredientSearchResult(
    val count: Int,
    val page: Int,
    val size: Int,
    val items: List<IngredientSummary>,
)

@Serializable
data class CookbookUserInfo(
    val id: Long,
    val username: String,
    val isAdmin: Boolean,
    val joinDate: Long,
)

@Serializable
data class CookbookUserSaveDto(
    val id: Long,
    val isAdmin: Boolean,
)

@Serializable
data class CookbookSaveDto(
    val title: String,
    val description: String = "",
    val visibility: String = "PUBLIC",
)

@Serializable
data class CookbookRecipeInfo(
    val id: Long,
    val title: String,
    val addedById: Long,
    val addedByUsername: String,
    val additionDate: Long,
)

@Serializable
data class UserSummary(
    val id: Long,
    val version: Long,
    val username: String,
    val role: String = "USER",
)

@Serializable
data class UserSearchResult(
    val count: Int,
    val page: Int,
    val size: Int,
    val items: List<UserSummary>,
)

@Serializable
data class RecipeSearchResult(
    val count: Int,
    val page: Int,
    val size: Int,
    val items: List<RecipeOverview>,
)

@Serializable
data class UserSettingsDTO(
    val autoAcceptFollowRequests: Boolean = false,
    val isAccountPublic: Boolean = false,
)

