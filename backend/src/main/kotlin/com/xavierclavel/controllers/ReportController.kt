package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.ModerationService
import com.xavierclavel.utils.Controller
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject
import shared.dto.ReportDTO
import shared.utils.URL.REPORT_URL

/**
 * User-facing entry point into moderation. Reviewing what lands here is the admin
 * backoffice's job, see [AdminController].
 */
object ReportController: Controller(REPORT_URL) {
    val moderationService: ModerationService by inject(ModerationService::class.java)

    override fun Route.routes() {
        authenticate("auth-session", "bearer-auth") {
            createReport()
        }
    }

    private fun Route.createReport() = post {
        val reportDTO = call.receive<ReportDTO>()
        call.respond(HttpStatusCode.Created, moderationService.createReport(getSessionUserId(), reportDTO))
    }
}
