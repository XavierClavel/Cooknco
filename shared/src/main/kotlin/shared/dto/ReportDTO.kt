package shared.dto

import kotlinx.serialization.Serializable
import shared.enums.ModerationAction
import shared.enums.ReportReason
import shared.enums.ReportTargetType

/** Payload a user submits when reporting a piece of content or another account. */
@Serializable
data class ReportDTO(
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: ReportReason,
    val comment: String = "",
)

/** Payload a moderator submits to close a report. */
@Serializable
data class ReportResolutionDTO(
    val action: ModerationAction,
    val note: String = "",
    /** Only read for [ModerationAction.SUSPEND_AUTHOR]. */
    val suspensionDays: Int = 7,
)
