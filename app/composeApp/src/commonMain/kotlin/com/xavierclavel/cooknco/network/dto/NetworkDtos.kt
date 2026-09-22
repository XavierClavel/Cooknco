package com.xavierclavel.cooknco.network.dto

import com.xavierclavel.cooknco.network.ApiClient
import kotlinx.serialization.Serializable

@Serializable
data class SessionDto(val token: String)

@Serializable
data class UserInfo(
    val id: Long,
    val version: Long,
    val username: String,
    val role: String = "USER",
    /**
     * Whether the premium features are open to this account — the PDF export today.
     *
     * A flag rather than something derived here, unlike [isAdmin]: what premium means is a
     * grant with an end date behind it, and the one place that can say whether it is still
     * running is the backend. It already answers true for an admin, so nothing in the app
     * has to remember that they are not asked to subscribe.
     *
     * Defaulted false so a build of this app older than the field is simply not offered
     * the export, rather than failing to parse the response.
     */
    val isPremium: Boolean = false,
    val joinDate: Long,
    val bio: String,
    val recipesCount: Int,
    val likesCount: Int,
    val cookbooksCount: Int,
    val followersCount: Int,
    val followsCount: Int,
) {
    /**
     * Whether this account moderates the product — the backend's `UserRole.ADMIN`.
     *
     * Read off the role rather than sent as a flag of its own, and compared leniently: the
     * role is a string here because the app has no use for the rest of the enum, and an
     * account whose role the app cannot place is not an admin, which is the safe way round.
     * What it gates in the app is only what is *offered*; the routes themselves are gated
     * on the server.
     */
    val isAdmin: Boolean get() = role.equals("ADMIN", ignoreCase = true)
}

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
    val steps: List<RecipeStepInfo> = emptyList(),
    val tips: String = "",
    val creationDate: Long,
    val likesCount: Int,
)

/**
 * One step, and how long it takes.
 *
 * [durationSeconds] is null on nearly every step and means "no timer" rather than "zero": a
 * zero would be a timer that has already run out. Seconds, because that is what cook mode
 * counts down — the editors offer minutes, which is how a recipe talks.
 *
 * The same shape goes back to the server (`RecipeSaveDto.steps`), so this one type is the
 * step in both directions, as it is on the backend's side of the wire.
 */
@Serializable
data class RecipeStepInfo(
    val text: String = "",
    /**
     * The step's own row, or null for a step that has never been saved.
     *
     * Sent back exactly as it arrived: it is how the server tells which step is which, and so
     * what to update rather than replace. Anything hung off a step - a picture - is addressed
     * by it, which is why a save no longer throws the rows away and writes them again.
     */
    val id: Long? = null,
    /** Which version of this step's picture to ask for. Zero means it has none. */
    val imageVersion: Long = 0,
    val durationSeconds: Int? = null,
    /** The recipe's own ingredients this step uses. Empty for most steps. */
    val ingredients: List<RecipeStepIngredientInfo> = emptyList(),
)

/**
 * One of the recipe's ingredients, used by one step.
 *
 * [index] is a position in `RecipeInfo.ingredients`, not an id — on the way *in* an
 * ingredient row has no identity to point at, because a save replaces the whole list. The
 * server resolves positions to rows and back, so this is all a client ever deals in.
 *
 * [amount] is what the author said, in the ingredient's own unit, and null when they said
 * nothing — which is most of the time. It is stored and returned exactly as given, so an
 * editor prefilled from it keeps a blank blank.
 *
 * What a blank comes to is worked out where it is shown, from the whole recipe: see
 * `RecipeInfo.blankStepAmounts`.
 */
@Serializable
data class RecipeStepIngredientInfo(
    val index: Int,
    val amount: Float? = null,
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
    val steps: List<RecipeStepInfo>,
    val tips: String,
)

/**
 * A Cooklang file, read into what the editor should be filled in with.
 *
 * Nothing was saved: the backend parses and hands back the recipe, and the ordinary create
 * route is what writes it once the cook has looked at it. So [recipe] is a [RecipeSaveDto] —
 * literally what this app would post to save — rather than a shape of its own.
 */
@Serializable
data class CooklangImportDto(
    val recipe: RecipeSaveDto,
    /**
     * What each of [recipe]'s ingredients is called, in that order.
     *
     * Positional, on the same reasoning as [RecipeStepIngredientInfo.index]: none of these
     * rows has an id yet, so a position is the only thing both sides can agree on inside one
     * request. It is here because a row the catalogue placed carries an id and no name, and
     * the editor has to print something.
     */
    val ingredientNames: List<String> = emptyList(),
    val unmatchedIngredients: Int = 0,
    val stepsWereSplit: Boolean = false,
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

/**
 * The name in the language the app is in.
 *
 * The backend answers with every translation it has, keyed by locale, so which one is
 * shown is the client's decision — and it was being made three different ways: the
 * ingredient page asked for the current locale, while the editor's search results and the
 * row it wrote on picking one both asked for "EN" outright, which is why searching for an
 * ingredient in French answered in English.
 *
 * The fallbacks matter as much as the first choice: a catalogue entry that has not been
 * translated yet still has to appear under some name, or it reads as an empty row.
 */
fun IngredientSummary.displayName(): String =
    name[ApiClient.locale] ?: name["EN"] ?: name.values.firstOrNull() ?: ""

/**
 * One of the signed-in cook's cookbooks, and whether a given recipe is already in it.
 *
 * Answered by `GET /cookbook/recipeStatus?recipe=`, which exists so that a picker can be
 * drawn in one request rather than listing the cookbooks and then asking after each.
 */
@Serializable
data class CookbookRecipeStatus(
    val id: Long,
    val title: String,
    val hasRecipe: Boolean,
)

@Serializable
data class UnitInfo(
    val name: String,
    val type: String,
    val factorToBase: Float,
    /**
     * The ladder this unit sits on ("METRIC"/"IMPERIAL"), or null when it sits on neither
     * and is never converted in either direction — a countable piece, a spoon.
     */
    val system: String? = null,
    /** Whether a conversion may land on this unit, as opposed to merely start from it. */
    val isDisplayUnit: Boolean = false,
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
    /**
     * The units this account reads amounts in ("METRIC"/"IMPERIAL").
     *
     * Non-null on the way out. Null on the way *in* means "leave it alone", like the two
     * above — and it stays null here until the user picks one, so saving a privacy toggle
     * from a build that predates the setting cannot put the account back on metric.
     */
    val unitSystem: String? = null,
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
    /** App-relative path to open, or blank. Resolved by `WebRoutes.routeForPath`. */
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

/**
 * A report on its way to the moderation queue.
 *
 * [targetType] and [reason] travel as the strings the backend's enums are named by — see
 * [com.xavierclavel.cooknco.network.ReportTargetType] and
 * [com.xavierclavel.cooknco.network.ReportReason], which is where the app's own list of
 * them lives.
 */
@Serializable
data class ReportDto(
    val targetType: String,
    val targetId: Long,
    val reason: String,
    val comment: String = "",
)
