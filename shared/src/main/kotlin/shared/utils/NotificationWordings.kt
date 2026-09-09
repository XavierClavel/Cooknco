package shared.utils

import shared.enums.Locale
import shared.enums.NotificationKind
import shared.enums.NotificationPlaceholder

/**
 * The wording the app's own push notifications go out with.
 *
 * Held in code rather than in resource files, unlike [EmailTemplates]: a notification is a
 * title and one line, so a file per kind per locale would be a directory of one-liners. It
 * is also not operator-owned — the backoffice writes announcements, whose wording arrives
 * with the send — so there is no override to fall back from.
 *
 * This lives in `shared` for the same reason the mail wordings do: the backoffice previews
 * a notification and the backend sends it, and a preview filled in differently would be a
 * lie.
 */
object NotificationWordings {
    /** What a device will actually show, and so what the backoffice accepts and stores. */
    const val MAX_TITLE_LENGTH = 127
    const val MAX_BODY_LENGTH = 511

    private val PLACEHOLDER = Regex("\\{\\{([A-Za-z0-9_]+)}}")

    /**
     * Title and body for one of the notifications the app emits itself.
     *
     * [NotificationKind.ANNOUNCEMENT] has none — its wording is typed by whoever sends it —
     * so asking for it is a programming error rather than a missing translation.
     */
    fun packaged(kind: NotificationKind, locale: Locale): Pair<String, String> {
        require(!kind.isOperatorWritten) { "${kind.key} carries the wording it is sent with" }
        val username = "{{${NotificationPlaceholder.USERNAME}}}"
        val title = "{{${NotificationPlaceholder.TITLE}}}"
        return when (locale) {
            Locale.FR -> when (kind) {
                NotificationKind.NEW_RECIPE -> "Nouvelle recette" to "$username vient de publier « $title »"
                NotificationKind.NEW_FOLLOWER -> "Nouvel abonné" to "$username vous suit désormais"
                NotificationKind.FOLLOW_REQUEST -> "Demande d'abonnement" to "$username souhaite vous suivre"
                NotificationKind.FOLLOW_ACCEPTED -> "Demande acceptée" to "$username a accepté votre demande d'abonnement"
                NotificationKind.ANNOUNCEMENT -> error("unreachable")
            }
            Locale.EN -> when (kind) {
                NotificationKind.NEW_RECIPE -> "New recipe" to "$username just published \"$title\""
                NotificationKind.NEW_FOLLOWER -> "New follower" to "$username is now following you"
                NotificationKind.FOLLOW_REQUEST -> "Follow request" to "$username would like to follow you"
                NotificationKind.FOLLOW_ACCEPTED -> "Request accepted" to "$username accepted your follow request"
                NotificationKind.ANNOUNCEMENT -> error("unreachable")
            }
        }
    }

    /**
     * Substitutes `{{name}}` for each value given.
     *
     * A placeholder nothing fills is left as it is, as in [EmailTemplates.render]: a
     * notification showing `{{username}}` is a bug worth seeing rather than one worth
     * hiding behind a blank.
     */
    fun render(text: String, values: Map<String, String>): String =
        values.entries.fold(text) { rendered, (name, value) -> rendered.replace("{{$name}}", value) }

    /** Every `{{name}}` a wording refers to, in the order they first appear. */
    fun placeholdersIn(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.groupValues[1] }.distinct().toList()

    /** Title and body of a kind, filled in. Announcements never come through here. */
    fun render(kind: NotificationKind, locale: Locale, values: Map<String, String>): Pair<String, String> {
        val (title, body) = packaged(kind, locale)
        return render(title, values) to render(body, values)
    }
}
