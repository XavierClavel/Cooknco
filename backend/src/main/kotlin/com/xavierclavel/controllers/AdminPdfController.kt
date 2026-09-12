package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.PdfTemplateService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumPathParam
import com.xavierclavel.utils.getPathVariable
import com.xavierclavel.utils.logger
import com.xavierclavel.utils.respondPDF
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
import shared.dto.PdfPreviewDTO
import shared.dto.PdfTemplateDTO
import shared.enums.Locale

/**
 * The documents the application prints, for the backoffice documents tab.
 *
 * Shaped like [AdminMailController], minus adding and removing kinds: a document is filled
 * in by code that knows what its values mean, so a kind an operator invented would have
 * nothing to render. What this exposes is editing the layout of the kinds that exist.
 */
object AdminPdfController: Controller("documents") {
    val pdfTemplateService: PdfTemplateService by inject(PdfTemplateService::class.java)
    val exportService: ExportService by inject(ExportService::class.java)
    val recipeService: RecipeService by inject(RecipeService::class.java)

    override fun Route.routes() {
        route("/templates") {
            listTemplates()
            saveTemplate()
            restoreTemplate()
            previewTemplate()
        }
    }

    /** Every kind of document, with the layout rendered for each locale and its origin. */
    private fun Route.listTemplates() = get {
        call.respond(pdfTemplateService.describe())
    }

    private fun Route.saveTemplate() = put("/{key}/{locale}") {
        val templateKey = key()
        val locale = getEnumPathParam<Locale>("locale")
        val adminId = getSessionUserId()
        val saved = pdfTemplateService.save(
            key = templateKey,
            locale = locale,
            dto = call.receive<PdfTemplateDTO>(),
        )
        logger.info { "Document layout '$templateKey' ($locale) saved by admin $adminId" }
        call.respond(saved)
    }

    /** Drops the saved layout for one locale, putting the packaged one back in service. */
    private fun Route.restoreTemplate() = delete("/{key}/{locale}") {
        val templateKey = key()
        val locale = getEnumPathParam<Locale>("locale")
        val adminId = getSessionUserId()
        if (!pdfTemplateService.restore(templateKey, locale)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info { "Document layout '$templateKey' ($locale) restored to the packaged one by admin $adminId" }
            call.respond(pdfTemplateService.describe(templateKey))
        }
    }

    /**
     * Prints the draft in the editor, against a real recipe, and answers with the PDF.
     *
     * The PDF itself rather than the HTML behind it: the whole reason this renders through
     * a browser is that the print differs from the screen — page boxes, print media
     * queries, where the pages break — and a preview that skipped that would be the one
     * thing an operator cannot check.
     */
    private fun Route.previewTemplate() = post("/{key}/{locale}/preview") {
        val locale = getEnumPathParam<Locale>("locale")
        val dto = call.receive<PdfPreviewDTO>()
        val kind = key()

        val recipe = dto.recipeId
            ?.let { recipeService.getEntityById(it) }
            ?: recipeService.findMostRecent()

        call.respondPDF(
            "preview-$kind-${locale.name.lowercase()}.pdf",
            exportService.generatePDF(recipe.toInfo(locale), locale, body = dto.body),
        )
    }

    /** Document keys are enum-backed strings, so unlike the other controllers there is no id. */
    private fun RoutingContext.key(): String = getPathVariable("key") ?: ""
}
