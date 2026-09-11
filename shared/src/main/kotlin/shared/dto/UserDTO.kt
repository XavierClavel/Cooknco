package shared.dto

import shared.enums.Locale
import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    var username: String,
    val password: String? = null,
    val googleId: String? = null,
    val mail: String = "mail@example.com",
    val bio: String = "",
)

@Serializable
data class UserSettingsDTO(
    val autoAcceptFollowRequests: Boolean = false,
    val isAccountPublic: Boolean = false,
    /**
     * The language this account is written to in, or null.
     *
     * Null on the way out means the backend has never been told — see `User.locale`. Null on
     * the way *in* means "leave it alone", which is what a client that does not know about
     * this field sends, and the reason a save from one cannot wipe a choice made in another.
     */
    val locale: Locale? = null,
)