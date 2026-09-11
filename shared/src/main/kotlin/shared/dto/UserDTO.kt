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
    /**
     * Mails about what the people you follow are up to. Off until asked for.
     *
     * Nullable, and absent means "leave as it is": a client that predates the field saving
     * any other setting would otherwise switch this one back to its default behind the
     * user's back. It governs notification mail only — never the account mails, which
     * [shared.enums.EmailTemplateKind.isTransactional] marks, nor push, which the app
     * controls per device.
     */
    val mailNotificationsEnabled: Boolean? = null,
)