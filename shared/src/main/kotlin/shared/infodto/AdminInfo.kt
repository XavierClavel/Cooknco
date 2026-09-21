package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.AccountStatus
import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.Locale
import shared.enums.MeasurementType
import shared.enums.PremiumStatus
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
    /**
     * Accounts holding a running grant. Admins are not counted: they pass every premium
     * gate without one, so counting them would inflate the only figure that says how many
     * people this product is paid for by.
     */
    val premiumUsersCount: Int = 0,
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
    /**
     * The grant itself, rather than `UserInfo.isPremium`'s "may use the premium features":
     * an operator manages what was given out, so a lapsed grant reads [PremiumStatus.NONE]
     * with the date it ran out still next to it, and an ADMIN with no grant of their own
     * reads NONE here while every premium gate lets them through.
     */
    val premiumStatus: PremiumStatus = PremiumStatus.NONE,
    /** When a timed grant ends — or ended. Null for a permanent grant and for none at all. */
    val premiumUntil: Long? = null,
    val bio: String,
    /** Null while nothing has reported one for this account — see `User.locale`. */
    val locale: Locale? = null,
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
    /** Carried in the row so the catalogue shows which ingredients still lack a conversion. */
    val allowedTypes: Set<MeasurementType> = setOf(MeasurementType.NONE),
    val defaultUnit: AmountUnit? = null,
)
