package shared.dto

import kotlinx.serialization.Serializable
import shared.enums.Locale

/**
 * An announcement an operator is sending from the backoffice.
 *
 * The audience is [userIds] when it names anyone and everyone with a registered device
 * when it is empty. Empty-means-broadcast rather than a separate flag because a broadcast
 * is what "no filter" means everywhere else in the admin API — but the controller makes the
 * caller say so out loud, so an empty list cannot reach everybody by accident.
 */
@Serializable
data class AnnouncementDTO(
    val title: String,
    val body: String,

    /**
     * Where tapping the notification goes, as an app-relative path (`/recipe/view?id=12`).
     *
     * Relative so that one value works for every client: the web app routes to it, and the
     * app resolves it against its own navigation. Blank opens the app on its home screen.
     */
    val link: String = "",

    /** Empty means every user with a device. See the class comment. */
    val userIds: List<Long> = emptyList(),

    /**
     * Restricts the send to users who read the app in this locale.
     *
     * Null sends to everyone regardless: an announcement's wording is typed once and is not
     * translated, so the operator decides whether it is fit for the whole audience.
     */
    val locale: Locale? = null,
)

/**
 * A notification an operator is sending to themselves, to see one land on a real device.
 *
 * Kept apart from [AnnouncementDTO] so that a test cannot be aimed at anyone else: it goes
 * to the devices of whoever is signed in to the backoffice, and names no audience at all.
 */
@Serializable
data class NotificationTestDTO(
    val title: String = "",
    val body: String = "",
    val link: String = "",
)
