package shared.dto

import kotlinx.serialization.Serializable

/**
 * The gate an operator saved for one client platform.
 *
 * All three fields are required, and there is no "off" flag: not gating a platform is
 * deleting its row, the same way not overriding a mail wording is having none. That keeps
 * a half-filled gate from existing at all — a floor with no store to send anyone to is a
 * dead end, and a floor nobody can see is worse than none.
 */
@Serializable
data class AppVersionDTO(
    /** The oldest build allowed to run. Anything below it is blocked. */
    val minimumVersion: String,
    /** The newest build published. Anything below it is nudged, above it is left alone. */
    val latestVersion: String,
    /** Where the blocked build is sent to update itself. */
    val storeUrl: String,
)
