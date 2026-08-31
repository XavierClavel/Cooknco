package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Report
import com.xavierclavel.models.User
import com.xavierclavel.models.query.QReport
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.utils.DbTransaction.insertAndGet
import com.xavierclavel.utils.DbTransaction.updateAndGet
import com.xavierclavel.utils.logger
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.dto.ReportDTO
import shared.dto.ReportResolutionDTO
import shared.enums.ModerationAction
import shared.enums.ReportStatus
import shared.enums.ReportTargetType
import shared.enums.UserRole
import shared.infodto.AdminRecipeInfo
import shared.infodto.AdminUserInfo
import shared.infodto.ReportInfo
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import shared.utils.Filepath.USERS_IMG_PATH
import java.time.LocalDateTime

/**
 * Report intake, the moderation queue, and the actions a moderator can take on accounts
 * and content.
 *
 * Every action that removes an account's access also revokes its sessions, so a decision
 * takes effect on the next request instead of whenever the session would have expired.
 */
class ModerationService: KoinComponent {
    val userService: UserService by inject()
    val recipeService: RecipeService by inject()
    val cookbookService: CookbookService by inject()
    val imageService: ImageService by inject()
    val redisService: RedisService by inject()

    companion object {
        /** Longest suspension the API accepts; anything longer should be a ban. */
        const val MAX_SUSPENSION_DAYS = 3650
    }

    // ------------------------------------------------------------------ intake

    /**
     * Records a report filed by [reporterId].
     *
     * Rejects reports on one's own content, and collapses repeats: a reporter may only
     * hold one pending report per target, so a single user cannot inflate the queue.
     */
    fun createReport(reporterId: Long, reportDTO: ReportDTO): ReportInfo {
        val reporter = userService.getEntityById(reporterId)
        val target = resolveTarget(reportDTO.targetType, reportDTO.targetId)
            ?: throw NotFoundException(NotFoundCause.REPORT_TARGET_NOT_FOUND)

        if (target.author?.id == reporterId) throw BadRequestException(BadRequestCause.CANNOT_REPORT_OWN_CONTENT)
        if (reportDTO.targetType == ReportTargetType.USER && reportDTO.targetId == reporterId) {
            throw BadRequestException(BadRequestCause.CANNOT_REPORT_OWN_CONTENT)
        }

        val alreadyPending = QReport()
            .reporter.id.eq(reporterId)
            .targetType.eq(reportDTO.targetType)
            .targetId.eq(reportDTO.targetId)
            .status.eq(ReportStatus.PENDING)
            .exists()
        if (alreadyPending) throw BadRequestException(BadRequestCause.ALREADY_REPORTED)

        val report = Report.from(reportDTO, reporter).insertAndGet()
        logger.info {
            "Report ${report.id} filed by user $reporterId on ${reportDTO.targetType} " +
                "${reportDTO.targetId} for ${reportDTO.reason}"
        }
        return report.toInfoResolvingTarget()
    }

    // ------------------------------------------------------------------- queue

    fun countPendingReports(): Int =
        QReport().status.eq(ReportStatus.PENDING).findCount()

    /** Ids of every [targetType] entity that currently has at least one pending report. */
    fun pendingReportTargetIds(targetType: ReportTargetType): Set<Long> =
        QReport()
            .targetType.eq(targetType)
            .status.eq(ReportStatus.PENDING)
            .findList()
            .map { it.targetId }
            .toSet()

    fun searchReports(
        status: ReportStatus?,
        targetType: ReportTargetType?,
        paging: Paging,
    ): Pair<Int, List<ReportInfo>> {
        val query = QReport()
            .apply { if (status != null) this.status.eq(status) }
            .apply { if (targetType != null) this.targetType.eq(targetType) }

        val count = query.findCount()
        val reports = query
            .orderBy().creationDate.desc()
            .setPaging(paging)
            .findList()
            .map { it.toInfoResolvingTarget() }
        return Pair(count, reports)
    }

    fun getReport(id: Long): ReportInfo = getReportEntity(id).toInfoResolvingTarget()

    /**
     * Closes a report by applying [ReportResolutionDTO.action] to its target, then stamping
     * the report with who decided what.
     */
    suspend fun resolveReport(reportId: Long, moderatorId: Long, resolution: ReportResolutionDTO): ReportInfo {
        val report = getReportEntity(reportId)
        if (report.status != ReportStatus.PENDING) throw BadRequestException(BadRequestCause.REPORT_ALREADY_RESOLVED)
        val moderator = userService.getEntityById(moderatorId)

        applyAction(report, resolution)

        report.resolve(resolution.action, moderator, resolution.note).update()
        logger.info {
            "Report ${report.id} on ${report.targetType} ${report.targetId} resolved as " +
                "${resolution.action} by ${moderator.username}"
        }

        // A decision on one item settles every other pending report on the same target
        if (resolution.action != ModerationAction.DISMISS) {
            QReport()
                .targetType.eq(report.targetType)
                .targetId.eq(report.targetId)
                .status.eq(ReportStatus.PENDING)
                .findList()
                .forEach {
                    it.resolve(resolution.action, moderator, "Resolved together with report ${report.id}").update()
                }
        }

        return getReportEntity(reportId).toInfoResolvingTarget()
    }

    private suspend fun applyAction(report: Report, resolution: ReportResolutionDTO) {
        when (resolution.action) {
            ModerationAction.DISMISS -> Unit

            ModerationAction.HIDE_CONTENT -> when (report.targetType) {
                ReportTargetType.RECIPE -> hideRecipe(report.targetId, resolution.note)
                // Accounts and cookbooks have no hidden state of their own
                else -> throw BadRequestException(BadRequestCause.ACTION_NOT_APPLICABLE_TO_TARGET)
            }

            ModerationAction.DELETE_CONTENT -> when (report.targetType) {
                ReportTargetType.RECIPE -> deleteRecipe(report.targetId)
                ReportTargetType.COOKBOOK -> deleteCookbook(report.targetId)
                ReportTargetType.USER -> throw BadRequestException(BadRequestCause.ACTION_NOT_APPLICABLE_TO_TARGET)
            }

            ModerationAction.SUSPEND_AUTHOR -> {
                val author = requireAuthor(report)
                if (report.targetType == ReportTargetType.RECIPE) hideRecipe(report.targetId, resolution.note)
                suspendUser(author.id, resolution.suspensionDays, resolution.note)
            }

            ModerationAction.BAN_AUTHOR -> {
                val author = requireAuthor(report)
                if (report.targetType == ReportTargetType.RECIPE) hideRecipe(report.targetId, resolution.note)
                banUser(author.id, resolution.note)
            }
        }
    }

    private fun requireAuthor(report: Report): User =
        resolveTarget(report.targetType, report.targetId)?.author
            ?: throw NotFoundException(NotFoundCause.REPORT_TARGET_NOT_FOUND)

    // --------------------------------------------------------- content actions

    fun hideRecipe(recipeId: Long, reason: String): AdminRecipeInfo {
        val recipe = recipeService.getEntityById(recipeId).hide(reason).updateAndGet()
        logger.info { "Recipe $recipeId hidden by moderation" }
        return recipe.toAdminInfo(countPendingReportsOn(ReportTargetType.RECIPE, recipeId))
    }

    fun unhideRecipe(recipeId: Long): AdminRecipeInfo {
        val recipe = recipeService.getEntityById(recipeId).unhide().updateAndGet()
        logger.info { "Recipe $recipeId un-hidden by moderation" }
        return recipe.toAdminInfo(countPendingReportsOn(ReportTargetType.RECIPE, recipeId))
    }

    /** Removes a recipe outright, ignoring the like/cookbook references a self-delete respects. */
    fun deleteRecipe(recipeId: Long) {
        val recipe = recipeService.getEntityById(recipeId)
        val imageVersion = recipe.imageVersion
        recipe.delete()
        imageService.deleteImage(RECIPES_IMG_PATH, recipeId, imageVersion)
        imageService.deleteImage(RECIPES_THUMBNAIL_PATH, recipeId, imageVersion)
        logger.info { "Recipe $recipeId deleted by moderation" }
    }

    fun deleteCookbook(cookbookId: Long) {
        cookbookService.getEntityById(cookbookId).delete()
        logger.info { "Cookbook $cookbookId deleted by moderation" }
    }

    // --------------------------------------------------------- account actions

    suspend fun suspendUser(userId: Long, days: Int, reason: String): AdminUserInfo {
        if (days < 1 || days > MAX_SUSPENSION_DAYS) throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val user = moderatableUser(userId)
        user.suspend(LocalDateTime.now().plusDays(days.toLong()), reason).update()
        val revoked = redisService.deleteAllSessionsOfUser(userId)
        logger.info { "User $userId (${user.username}) suspended for $days days, $revoked session(s) revoked" }
        return adminInfoOf(user)
    }

    suspend fun banUser(userId: Long, reason: String): AdminUserInfo {
        val user = moderatableUser(userId)
        user.ban(reason).update()
        val revoked = redisService.deleteAllSessionsOfUser(userId)
        logger.info { "User $userId (${user.username}) banned, $revoked session(s) revoked" }
        return adminInfoOf(user)
    }

    fun reinstateUser(userId: Long): AdminUserInfo {
        val user = userService.getEntityById(userId).reinstate().updateAndGet()
        logger.info { "User $userId (${user.username}) reinstated" }
        return adminInfoOf(user)
    }

    /** Confirms an account on the owner's behalf, for when the verification mail never arrived. */
    fun verifyUser(userId: Long): AdminUserInfo {
        val user = userService.getEntityById(userId)
        if (!user.isVerified) user.verify().update()
        return adminInfoOf(user)
    }

    suspend fun deleteUser(userId: Long) {
        val user = moderatableUser(userId)
        val username = user.username
        val imageVersion = user.imageVersion
        redisService.deleteAllSessionsOfUser(userId)
        // deleteUserById anonymises the reports this account filed, so the queue keeps its history
        userService.deleteUserById(userId)
        imageService.deleteImage(USERS_IMG_PATH, userId, imageVersion)
        logger.info { "User $userId ($username) deleted by moderation" }
    }

    /** Admins are not moderatable: demote first, so nobody can lock out the whole backoffice. */
    private fun moderatableUser(userId: Long): User =
        userService.getEntityById(userId).also {
            if (it.role == UserRole.ADMIN) throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_MODERATE_ADMIN)
        }

    // ---------------------------------------------------------------- helpers

    fun countPendingReportsOn(targetType: ReportTargetType, targetId: Long): Int =
        QReport()
            .targetType.eq(targetType)
            .targetId.eq(targetId)
            .status.eq(ReportStatus.PENDING)
            .findCount()

    fun countReportsAgainstUser(userId: Long): Int =
        countReportsAgainstUsers(listOf(userId))[userId] ?: 0

    /**
     * Reports filed against each of [userIds] — both on the account itself and on any
     * recipe it owns. Batched, so listing a page of accounts costs three queries rather
     * than two per row.
     *
     * @return count per account id; accounts with no reports are absent from the map
     */
    fun countReportsAgainstUsers(userIds: Collection<Long>): Map<Long, Int> {
        if (userIds.isEmpty()) return emptyMap()
        val counts = mutableMapOf<Long, Int>()

        QReport()
            .targetType.eq(ReportTargetType.USER)
            .targetId.`in`(userIds)
            .findList()
            .forEach { counts.merge(it.targetId, 1, Int::plus) }

        val ownerByRecipeId = recipeService.mapRecipeIdsToOwnerIds(userIds)
        if (ownerByRecipeId.isNotEmpty()) {
            QReport()
                .targetType.eq(ReportTargetType.RECIPE)
                .targetId.`in`(ownerByRecipeId.keys)
                .findList()
                .forEach { report ->
                    ownerByRecipeId[report.targetId]?.let { counts.merge(it, 1, Int::plus) }
                }
        }
        return counts
    }

    /**
     * Pending report counts for each of [targetIds] of the given [targetType], batched for
     * the same reason as [countReportsAgainstUsers].
     *
     * @return count per target id; targets with no pending report are absent from the map
     */
    fun countPendingReportsOn(targetType: ReportTargetType, targetIds: Collection<Long>): Map<Long, Int> {
        if (targetIds.isEmpty()) return emptyMap()
        val counts = mutableMapOf<Long, Int>()
        QReport()
            .targetType.eq(targetType)
            .targetId.`in`(targetIds)
            .status.eq(ReportStatus.PENDING)
            .findList()
            .forEach { counts.merge(it.targetId, 1, Int::plus) }
        return counts
    }

    fun adminInfoOf(user: User): AdminUserInfo =
        user.toAdminInfo(
            mail = userService.readMail(user),
            reportsAgainstCount = countReportsAgainstUser(user.id),
        )

    private fun getReportEntity(id: Long): Report =
        QReport().id.eq(id).findOne() ?: throw NotFoundException(NotFoundCause.REPORT_NOT_FOUND)

    /** A report's target, resolved at read time because reports outlive what they point at. */
    private data class ResolvedTarget(
        val label: String,
        val author: User?,
        val hidden: Boolean,
    )

    private fun resolveTarget(targetType: ReportTargetType, targetId: Long): ResolvedTarget? =
        when (targetType) {
            ReportTargetType.RECIPE -> recipeService.findEntityById(targetId)?.let {
                ResolvedTarget(it.title, it.owner, it.isHidden)
            }
            ReportTargetType.USER -> userService.findEntityById(targetId)?.let {
                ResolvedTarget(it.username, it, it.isBanned || it.isSuspended())
            }
            ReportTargetType.COOKBOOK -> cookbookService.findEntityById(targetId)?.let { cookbook ->
                ResolvedTarget(
                    cookbook.title,
                    cookbook.users.firstOrNull { it.isAdmin }?.user,
                    false,
                )
            }
        }

    private fun Report.toInfoResolvingTarget(): ReportInfo {
        val target = resolveTarget(this.targetType, this.targetId)
        return this.toInfo(
            targetLabel = target?.label,
            targetAuthor = target?.author,
            targetHidden = target?.hidden ?: false,
            reportsOnTargetCount = QReport()
                .targetType.eq(this.targetType)
                .targetId.eq(this.targetId)
                .findCount(),
        )
    }
}
