package shared.infodto

import shared.overviewdto.UserOverview
import kotlinx.serialization.Serializable

/**
 * What the notification bell shows.
 *
 * Pending follow requests are not notifications but a queue the recipient has to act on, so
 * they stay a list of their own rather than being folded into [notifications].
 */
@Serializable
data class NotificationInfo(
    val followersPending : List<UserOverview>,

    /** Most recent first. Defaulted so an older client that only reads the queue still parses. */
    val notifications: List<UserNotificationInfo> = emptyList(),

    val unreadCount: Int = 0,
)
