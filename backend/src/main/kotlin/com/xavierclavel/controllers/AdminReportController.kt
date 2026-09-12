package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.ModerationService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.json
import com.xavierclavel.utils.logger
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject
import shared.dto.ReportResolutionDTO
import shared.dto.SearchResult
import shared.enums.ReportStatus
import shared.enums.ReportTargetType
import shared.infodto.ReportInfo

/** The moderation queue: what users reported, and what a moderator decided about it. */
object AdminReportController: Controller("reports") {
    val moderationService: ModerationService by inject(ModerationService::class.java)

    override fun Route.routes() {
        searchReports()
        getReport()
        resolveReport()
    }

    private fun Route.searchReports() = get {
        val paging = getPaging()
        val (count, reports) = moderationService.searchReports(
            status = getEnumQueryParam<ReportStatus>("status"),
            targetType = getEnumQueryParam<ReportTargetType>("targetType"),
            paging = paging,
        )
        val result = SearchResult(count, paging.pageIndex(), paging.pageSize(), reports)
        call.respond(json.encodeToString(SearchResult.serializer(ReportInfo.serializer()), result))
    }

    private fun Route.getReport() = get("/{id}") {
        call.respond(moderationService.getReport(getPathId()))
    }

    private fun Route.resolveReport() = post("/{id}/resolve") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val resolution = call.receive<ReportResolutionDTO>()
        val report = moderationService.resolveReport(id, adminId, resolution)
        logger.info { "Report $id resolved by admin $adminId with ${resolution.action}" }
        call.respond(report)
    }
}
