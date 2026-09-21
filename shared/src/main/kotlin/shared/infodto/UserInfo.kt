package shared.infodto

import shared.enums.UserRole
import shared.overviewdto.UserOverview
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val id: Long,
    val version: Long,
    val username: String,
    val role: UserRole = UserRole.USER,
    /**
     * Whether this account may use the premium features — the one question a client asks,
     * and therefore one flag rather than the grant behind it.
     *
     * True for an ADMIN whatever their own grant says, because the gate treats them as
     * premium (`User.hasPremiumAccess`); an operator is not asked to sell themselves a
     * subscription to moderate with. The grant itself — for good, or until a date — is an
     * operator's business and rides on `AdminUserInfo` instead.
     *
     * Public, like [role] already is: it says an account pays for the product, not
     * anything about the person. Defaulted so that a client older than the field reads a
     * response from a newer backend.
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
    fun toOverview() = UserOverview(
        id = this.id,
        role = this.role,
        version = this.version,
        username = this.username,
    )
}