package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.NotificationKind

/**
 * One notification as it was delivered to one user.
 *
 * The wording is the rendered text rather than a kind plus values, because that is what was
 * sent: re-rendering it later against today's data would show a list that disagrees with
 * the notifications already on the recipient's device.
 */
@Serializable
data class UserNotificationInfo(
    val id: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,

    /** App-relative path to open, or blank. See [shared.dto.AnnouncementDTO.link]. */
    val link: String,

    /** Whoever caused it, when a user did. Absent on announcements. */
    val actor: shared.overviewdto.UserOverview? = null,

    val createdAt: Long,
    val read: Boolean,
)
