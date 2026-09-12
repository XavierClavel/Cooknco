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
    val id: Long? = null,
    val name: String,
    val amount: Float? = null,
    val unit: String,
    val complement: String? = null,
    val type: String? = null,
    val allowedTypes: List<String> = emptyList(),
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
    val steps: List<String>,
    val tips: String,
)

@Serializable
data class RecipeIngredientSaveDto(
    val id: Long? = null,
    val customName: String? = null,
    val unit: String,
    val amount: Float? = null,
    val complement: String? = null,
)

@Serializable
data class IngredientSummary(
    val id: Long,
    val name: Map<String, String>,
    val type: String = "",
    val allowedTypes: List<String> = emptyList(),
    val defaultUnit: String? = null,
)

@Serializable
data class UnitInfo(
    val name: String,
    val type: String,
    val factorToBase: Float,
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

/**
 * An MCP client this account has approved (`GET /user/mcp-clients` — `shared.infodto.McpClientInfo`).
 *
 * [clientName] is whatever the client called itself when it registered, so it is untrusted
 * text: it is shown next to [redirectUris], the part a client cannot lie about, for the same
 * reason the consent page does.
 */
@Serializable
data class McpClientInfo(
    val clientId: String,
    val clientName: String,
    val redirectUris: List<String> = emptyList(),
    val grantedAt: Long,
    val lastUsedAt: Long,
)

/** Body of `PUT /user/password` — `shared.dto.PasswordDTO`. */
@Serializable
data class PasswordDTO(
    val old: String,
    val new: String,
)

@Serializable
data class UserSettingsDTO(
    val autoAcceptFollowRequests: Boolean = false,
    val isAccountPublic: Boolean = false,
    /**
     * The language the backend writes to this account in ("FR"/"EN"), or null.
     *
     * Null on the way out means nothing has ever told it; null on the way *in* means
     * "leave it alone" — see `shared.dto.UserSettingsDTO`. Both nullable fields here rely
     * on kotlinx omitting a property that still holds its default, so a save from a screen
     * that does not know about one of them cannot wipe it.
     */
    val locale: String? = null,
    /** Mails about what the people you follow are up to. Null means "leave it alone". */
    val mailNotificationsEnabled: Boolean? = null,
)

/**
 * One row of a followers/following list (`FollowController.getFollowers`/`getFollows`):
 * [user] is whichever side of the relationship the endpoint is listing (the follower on
 * `/followers`, the followed account on `/follows` — see `Follow.toFollowersInfo`/
 * `toFollowsInfo` server-side), and [pending] is why both screens show requested and
 * accepted rows together rather than needing a separate pending-only endpoint.
 */
@Serializable
data class FollowInfoDto(
    val user: UserSummary,
    val followedSince: Long,
    val pending: Boolean,
)

// ------------------------------------------------------------- notifications

/**
 * Where this install can be pushed to.
 *
 * The platform is sent rather than inferred: a registration token says nothing about where
 * it came from, and the payload the backend builds differs per platform.
 */
@Serializable
data class DeviceRegistrationDTO(
    val token: String,
    /**
     * Deliberately without a default. kotlinx.serialization does not encode defaults, so a
     * default here would be a field the backend never receives — and it requires this one.
     * Callers pass [com.xavierclavel.cooknco.platform.devicePlatform].
     */
    val platform: String,
    /**
     * The build doing the registering, so the backoffice can see what a version floor
     * would cost before raising it.
     *
     * No default, for the reason [platform] has none. Empty is a legitimate value — a
     * build that cannot read its own version sends it and is counted as unknown — but it
     * has to be sent rather than omitted, since an omitted field and an empty one would
     * otherwise be the same thing on the wire and only one of them is deliberate.
     */
    val appVersion: String,
)

/**
 * One notification as the backend delivered it.
 *
 * The wording arrives rendered — the backend stores what it pushed — so there is nothing to
 * template here, and the list cannot disagree with the notification already on the device.
 */
@Serializable
data class UserNotificationInfo(
    val id: Long,
    val kind: String,
    val title: String,
    val body: String,
    /** App-relative path to open, or blank. Resolved by `PushNotifications.routeFor`. */
    val link: String = "",
    val actor: UserSummary? = null,
    val createdAt: Long,
    val read: Boolean = false,
)

@Serializable
data class NotificationInfo(
    val followersPending: List<UserSummary> = emptyList(),
    val notifications: List<UserNotificationInfo> = emptyList(),
    val unreadCount: Int = 0,
)

/**
 * What the backend thinks of this build.
 *
 * [status] arrives as a string rather than an enum for the same reason the rest of this
 * file does: adding a verdict on the server must not stop an older app from parsing the
 * answer, and an unrecognised one has to degrade to "let it run". See
 * `AppVersionRepository`.
 *
 * No copy travels with it. The sentence a user reads lives in the app, in the app's own
 * language, because that is the only place that knows it.
 */
@Serializable
data class AppVersionCheckInfo(
    val status: String,
    val currentVersion: String = "",
    val minimumVersion: String? = null,
    val latestVersion: String? = null,
    val storeUrl: String? = null,
)
