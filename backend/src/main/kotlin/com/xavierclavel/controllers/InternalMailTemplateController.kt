package com.xavierclavel.controllers

import com.xavierclavel.services.EmailTemplateService
import com.xavierclavel.utils.Controller
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject
import shared.utils.URL.INTERNAL_MAIL_TEMPLATES_URL

/**
 * What mail-service reads the operator-saved wordings through.
 *
 * The one endpoint the cluster's other pods call, and the reason it needs no credential of
 * its own: `frontend/nginx.conf` proxies `/api/`, `/image/` and the log stream and nothing
 * else, so a route outside those prefixes has no path in from the internet. The ingress
 * points at the frontend, never at this service directly.
 *
 * It answers with overrides only. A built-in mail nobody reworded is absent, and
 * mail-service renders it from the copy packaged in its own jar — so the wording exists in
 * one place whichever of the two is serving it.
 */
object InternalMailTemplateController: Controller(INTERNAL_MAIL_TEMPLATES_URL) {
    val emailTemplateService: EmailTemplateService by inject(EmailTemplateService::class.java)

    override fun Route.routes() {
        getTemplates()
    }

    private fun Route.getTemplates() = get {
        call.respond(emailTemplateService.effective())
    }
}
