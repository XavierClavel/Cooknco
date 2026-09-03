package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import shared.dto.ReportDTO
import shared.enums.ModerationAction
import shared.enums.ReportReason
import shared.enums.ReportStatus
import shared.enums.ReportTargetType
import shared.infodto.ReportInfo
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * A user-submitted moderation report.
 *
 * The target is referenced by ([targetType], [targetId]) rather than by a foreign key,
 * because a single queue covers recipes, accounts and cookbooks. A report therefore
 * outlives its target: rendering resolves the label lazily and treats a missing target
 * as deleted.
 */
@Entity
@Table(name = "reports")
class Report (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    var targetType: ReportTargetType = ReportTargetType.RECIPE,

    var targetId: Long = 0,

    /** Null once the reporting account has been deleted. */
    @ManyToOne
    var reporter: User? = null,

    var reason: ReportReason = ReportReason.OTHER,

    @Column(length = 1023)
    @DbDefault("")
    var comment: String = "",

    var status: ReportStatus = ReportStatus.PENDING,

    var creationDate: LocalDateTime = LocalDateTime.now(),

    var resolutionDate: LocalDateTime? = null,

    @ManyToOne
    var resolvedBy: User? = null,

    var resolution: ModerationAction? = null,

    @Column(length = 1023)
    @DbDefault("")
    var moderatorNote: String = "",

) : Model() {
    companion object {
        fun from(reportDTO: ReportDTO, reporter: User) = Report(
            targetType = reportDTO.targetType,
            targetId = reportDTO.targetId,
            reporter = reporter,
            reason = reportDTO.reason,
            comment = reportDTO.comment.take(1023),
        )
    }

    fun resolve(action: ModerationAction, moderator: User, note: String) = this.apply {
        this.status = if (action == ModerationAction.DISMISS) ReportStatus.DISMISSED else ReportStatus.RESOLVED
        this.resolution = action
        this.resolvedBy = moderator
        this.moderatorNote = note.take(1023)
        this.resolutionDate = LocalDateTime.now()
    }

    /**
     * @param targetLabel human-readable name of the target, or null if it no longer exists
     * @param targetAuthor account responsible for the target, or null if it no longer exists
     */
    fun toInfo(
        targetLabel: String?,
        targetAuthor: User?,
        targetHidden: Boolean,
        reportsOnTargetCount: Int,
    ) = ReportInfo(
        id = this.id,
        targetType = this.targetType,
        targetId = this.targetId,
        targetLabel = targetLabel,
        targetAuthor = targetAuthor?.toOverview(),
        targetHidden = targetHidden,
        reporter = this.reporter?.toOverview(),
        reason = this.reason,
        comment = this.comment,
        status = this.status,
        creationDate = this.creationDate.toEpochSecond(ZoneOffset.UTC),
        resolutionDate = this.resolutionDate?.toEpochSecond(ZoneOffset.UTC),
        resolvedBy = this.resolvedBy?.toOverview(),
        resolution = this.resolution,
        moderatorNote = this.moderatorNote,
        reportsOnTargetCount = reportsOnTargetCount,
    )
}
