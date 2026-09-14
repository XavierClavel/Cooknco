package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ReportApi
import com.xavierclavel.cooknco.network.ReportReason
import com.xavierclavel.cooknco.network.ReportTargetType
import com.xavierclavel.cooknco.network.dto.ReportDto
import kotlinx.coroutines.flow.first

class ReportRepository(
    private val reportApi: ReportApi,
    private val tokenDataStore: TokenDataStore,
) {
    private suspend fun requireToken(): String =
        tokenDataStore.tokenFlow.first() ?: throw IllegalStateException("Not authenticated")

    suspend fun report(
        targetType: ReportTargetType,
        targetId: Long,
        reason: ReportReason,
        comment: String,
    ): Result<Unit> = runCatching {
        reportApi.createReport(
            requireToken(),
            ReportDto(
                targetType = targetType.value,
                targetId = targetId,
                reason = reason.value,
                // Trimmed here rather than in the sheet: whitespace a user left behind is
                // not a detail a moderator asked for.
                comment = comment.trim(),
            ),
        )
    }
}
