package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.AccountStatus
import shared.enums.IngredientType
import shared.enums.Locale
import shared.enums.UserRole
import shared.overviewdto.UserOverview

/** Headline figures for the backoffice landing page. */
@Serializable
data class AdminOverview(
    val usersCount: Int,
    val activeUsersCount: Int,
    val unverifiedUsersCount: Int,
    val suspendedUsersCount: Int,
    val bannedUsersCount: Int,
    val recipesCount: Int,
    val hiddenRecipesCount: Int,
    val ingredientsCount: Int,
    val cookbooksCount: Int,
    val likesCount: Int,
    val followsCount: Int,
    val pendingReportsCount: Int,
    val newUsersLastWeek: Int,
    val newRecipesLastWeek: Int,
    val errorsInLogBuffer: Int,
    val version: String,
)

/**
 * Everything the backoffice shows about an account, including fields never exposed on
 * the public profile (mail address, verification and moderation state).
 */
@Serializable
data class AdminUserInfo(
    val id: Long,
    val version: Long,
    val username: String,
    val mail: String,
    val role: UserRole,
    val status: AccountStatus,
    val isVerified: Boolean,
    val isBanned: Boolean,
    val suspendedUntil: Long? = null,
    val moderationNote: String = "",
    val bio: String,
    val locale: Locale,
    val isAccountPublic: Boolean,
    val joinDate: Long,
    val lastActivityDate: Long,
    val recipesCount: Int,
    val likesCount: Int,
    val cookbooksCount: Int,
    val followersCount: Int,
    val followsCount: Int,
    val reportsAgainstCount: Int,
)

/** A recipe row in the backoffice content table. */
@Serializable
data class AdminRecipeInfo(
    val id: Long,
    val version: Long,
    val title: String,
    val owner: UserOverview? = null,
    val creationDate: Long,
    val modificationDate: Long,
    val likesCount: Int,
    val cookbooksCount: Int,
    val isHidden: Boolean,
    val hiddenReason: String = "",
    val taggedForDeletion: Boolean,
    val pendingReportsCount: Int,
)

/** An ingredient row in the backoffice catalogue table, with its usage count. */
@Serializable
data class AdminIngredientInfo(
    val id: Long,
    val name: Map<Locale, String>,
    val type: IngredientType,
    val calories: Int,
    val recipesCount: Int,
)
