package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.DevicePlatform

/**
 * Who a send would reach, for the backoffice to show before one goes out.
 *
 * Counted in devices as well as users because they answer different questions: users is the
 * size of the audience, devices is how many pushes the send will make.
 */
@Serializable
data class AdminPushAudienceInfo(
    val users: Int,
    val devices: Int,
    val byPlatform: Map<DevicePlatform, Int>,
)

/**
 * What an announcement did.
 *
 * Reports what was *stored*, not what was delivered: the notifications are written before
 * the pushes go out and the pushes then run in the background, so an operator sending to
 * ten thousand devices is not left holding a request open while they do. That is also the
 * more useful number — a stored notification is one the recipient will find in the app
 * whether or not their device could be reached.
 */
@Serializable
data class AdminNotificationSendInfo(
    val recipients: Int,
    val devices: Int,
)

/**
 * What a test send did, once it had finished doing it.
 *
 * Unlike an announcement this is waited on, because it goes to the operator's own handful
 * of devices and the whole point of a test is to learn whether the push actually left.
 */
@Serializable
data class AdminNotificationTestInfo(
    val devices: Int,
    val pushed: Int,
    val failed: Int,
)
