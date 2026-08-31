package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.ModerationAction
import shared.enums.ReportReason
import shared.enums.ReportStatus
import shared.enums.ReportTargetType
import shared.overviewdto.UserOverview

/**
 * A moderation report as shown in the backoffice queue.
 *
 * [targetLabel] and [targetAuthor] are denormalised at read time so the queue can be
 * rendered without a lookup per row; both are null once the target has been deleted.
 */
@Serializable
data class ReportInfo(
    val id: Long,
    val targetType: ReportTargetType,
    val targetId: Long,
    val targetLabel: String? = null,
    val targetAuthor: UserOverview? = null,
    val targetHidden: Boolean = false,
    val reporter: UserOverview? = null,
    val reason: ReportReason,
    val comment: String,
    val status: ReportStatus,
    val creationDate: Long,
    val resolutionDate: Long? = null,
    val resolvedBy: UserOverview? = null,
    val resolution: ModerationAction? = null,
    val moderatorNote: String = "",
    /** How many reports in total target the same entity, this one included. */
    val reportsOnTargetCount: Int = 1,
)
