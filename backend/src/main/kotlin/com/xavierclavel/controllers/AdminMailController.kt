package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.EmailTemplateService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumPathParam
import com.xavierclavel.utils.getPathVariable
import com.xavierclavel.utils.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.inject
import shared.dto.EmailPreviewDTO
import shared.dto.EmailTemplateDTO
import shared.dto.EmailTemplateKeyDTO
import shared.dto.EmailTestDTO
import shared.enums.Locale

/**
 * The mails the application sends, for the backoffice mails tab.
 *
 * The wordings live here but are sent by mail-service, which reads them through
 * [com.xavierclavel.controllers.InternalMailTemplateController]. What this exposes is
 * therefore editing, not sending: the one endpoint that puts a mail in an inbox does it by
 * asking mail-service for it, the same way the rest of the app does.
 */
object AdminMailController: Controller("mails") {
    val emailTemplateService: EmailTemplateService by inject(EmailTemplateService::class.java)

    override fun Route.routes() {
        route("/templates") {
            listTemplates()
            addTemplate()
            saveTemplate()
            restoreTemplate()
            deleteTemplate()
            previewTemplate()
            sendTestMail()
        }
    }

    /** Every kind of mail, with what is being sent for each locale and where it comes from. */
    private fun Route.listTemplates() = get {
        call.respond(emailTemplateService.describe())
    }

    /** Adds a kind of the operator's own. Inert until backend code emits a mail under its key. */
    private fun Route.addTemplate() = post {
        val adminId = getSessionUserId()
        val key = call.receive<EmailTemplateKeyDTO>().key.trim().lowercase()
        val created = emailTemplateService.create(key)
        logger.info { "Mail template '$key' added by admin $adminId" }
        call.respond(HttpStatusCode.Created, created)
    }

    private fun Route.saveTemplate() = put("/{key}/{locale}") {
        val templateKey = key()
        val locale = getEnumPathParam<Locale>("locale")
        val adminId = getSessionUserId()
        val saved = emailTemplateService.save(
            key = templateKey,
            locale = locale,
            dto = call.receive<EmailTemplateDTO>(),
        )
        logger.info { "Mail template '$templateKey' ($locale) saved by admin $adminId" }
        call.respond(saved)
    }

    /** Drops the saved wording for one locale, putting the packaged one back in service. */
    private fun Route.restoreTemplate() = delete("/{key}/{locale}") {
        val templateKey = key()
        val locale = getEnumPathParam<Locale>("locale")
        val adminId = getSessionUserId()
        if (!emailTemplateService.restore(templateKey, locale)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info { "Mail template '$templateKey' ($locale) restored to the packaged wording by admin $adminId" }
            call.respond(emailTemplateService.describe(templateKey))
        }
    }

    /** Removes a kind an operator added, in every locale. Built-in kinds are refused. */
    private fun Route.deleteTemplate() = delete("/{key}") {
        val templateKey = key()
        val adminId = getSessionUserId()
        if (!emailTemplateService.delete(templateKey)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info { "Mail template '$templateKey' deleted by admin $adminId" }
            call.respond(HttpStatusCode.OK)
        }
    }

    /**
     * Fills a wording in as it would go out.
     *
     * A POST because the draft in the editor is the subject of the request: previewing what
     * is already saved would be the one preview an operator never needs.
     */
    private fun Route.previewTemplate() = post("/{key}/preview") {
        call.respond(emailTemplateService.preview(key(), call.receive<EmailPreviewDTO>()))
    }

    /**
     * Queues one mail to the address given, through mail-service like any other.
     *
     * Accepted rather than OK: what comes back says the request was handed over, and only
     * the recipient's inbox can say it arrived.
     */
    private fun Route.sendTestMail() = post("/{key}/test") {
        val templateKey = key()
        val adminId = getSessionUserId()
        val dto = call.receive<EmailTestDTO>()
        emailTemplateService.requestTest(templateKey, dto)
        logger.info { "Test mail '$templateKey' (${dto.locale}) queued to ${dto.recipient} by admin $adminId" }
        call.respond(HttpStatusCode.Accepted)
    }

    /** Template keys are free-form strings, so unlike the other controllers there is no id. */
    private fun RoutingContext.key(): String = getPathVariable("key") ?: ""
}
