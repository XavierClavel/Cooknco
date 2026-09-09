package shared.enums

/**
 * A push notification the application itself knows how to send.
 *
 * Each kind carries the wording it goes out with, packaged per locale in
 * [shared.utils.NotificationWordings], and the placeholders that wording may fill in. The
 * pairing is what keeps a notification honest: a kind whose wording names `{{username}}`
 * is one whose emitter is known to have a username to give it.
 *
 * [ANNOUNCEMENT] is the exception, and deliberately so — an operator types its title and
 * body at send time, so it has no packaged wording and no placeholders to fill.
 */
enum class NotificationKind(val key: String, val placeholders: List<String>) {

    /** A recipe published by someone the recipient follows. */
    NEW_RECIPE("new_recipe", listOf(NotificationPlaceholder.USERNAME, NotificationPlaceholder.TITLE)),

    /** Someone started following the recipient, on an account that accepts follows outright. */
    NEW_FOLLOWER("new_follower", listOf(NotificationPlaceholder.USERNAME)),

    /** Someone asked to follow the recipient, on an account that holds requests pending. */
    FOLLOW_REQUEST("follow_request", listOf(NotificationPlaceholder.USERNAME)),

    /** A follow request the recipient had sent was accepted. */
    FOLLOW_ACCEPTED("follow_accepted", listOf(NotificationPlaceholder.USERNAME)),

    /** Written by an operator in the backoffice, wording and all. */
    ANNOUNCEMENT("announcement", emptyList()),
    ;

    /** True when the wording comes from the sender rather than from the packaged copy. */
    val isOperatorWritten: Boolean get() = this == ANNOUNCEMENT

    companion object {
        fun of(key: String): NotificationKind? = entries.find { it.key == key }
    }
}

/**
 * The names a packaged wording may fill in as `{{name}}`.
 *
 * Its own object rather than constants on [NotificationKind], for the reason
 * [MailPlaceholder] is: an enum entry cannot read its own companion, which is not yet
 * initialised while the entries are being built.
 */
object NotificationPlaceholder {
    /** Whoever caused the notification — never the recipient. */
    const val USERNAME = "username"

    /** The title of the recipe the notification is about. */
    const val TITLE = "title"
}
