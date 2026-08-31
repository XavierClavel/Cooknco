package shared.dto

import kotlinx.serialization.Serializable

/** Payload for suspending an account for a bounded period. */
@Serializable
data class SuspensionDTO(
    val days: Int = 7,
    val reason: String = "",
)

/** Payload for a permanent ban, or for hiding a piece of content. */
@Serializable
data class ModerationReasonDTO(
    val reason: String = "",
)
