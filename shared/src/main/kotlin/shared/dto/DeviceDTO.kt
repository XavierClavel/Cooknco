package shared.dto

import kotlinx.serialization.Serializable
import shared.enums.DevicePlatform

/**
 * A client telling the backend where to push to.
 *
 * Sent after the user has granted the OS notification permission, and again whenever FCM
 * rotates the token. The token is the identity of the row — registering the same one twice
 * moves it to the caller's account rather than adding a second device — so a handset that
 * changes hands stops receiving the previous owner's notifications.
 */
@Serializable
data class DeviceRegistrationDTO(
    val token: String,
    val platform: DevicePlatform,
)
