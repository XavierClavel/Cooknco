package shared.infodto

import kotlinx.serialization.Serializable

@Serializable
data class CookbookUserInfo(
    val id: Long,
    /** The member's profile picture version, as `UserInfo.version`: what their avatar URL is named by. */
    val version: Long,
    val username: String,
    val isAdmin: Boolean,
    val joinDate: Long,
)