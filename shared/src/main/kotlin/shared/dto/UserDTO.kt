package shared.dto

import shared.enums.Locale
import shared.enums.UnitSystem
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
    /**
     * The units this account reads amounts in.
     *
     * Non-null on the way out — a reader always has a ladder, and a client formatting an
     * amount needs a definite answer rather than a fallback of its own. Null on the way
     * *in* means "leave it alone", like the two fields above, so a client that predates
     * this one cannot put an account back on metric by saving a privacy toggle.
     *
     * It says nothing about how recipes are *stored*: an author writes in whatever unit
     * they picked, and this only decides what a reader is shown — see [UnitSystem].
     */
    val unitSystem: UnitSystem? = null,
)