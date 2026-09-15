package com.xavierclavel.controllers

import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.PdfTemplateService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumPathParam
import com.xavierclavel.utils.getPathVariable
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
import shared.enums.PdfDocumentKind

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
    val cookbookService: CookbookService by inject(CookbookService::class.java)

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
        call.respond(
            pdfTemplateService.save(
                key = key(),
                locale = getEnumPathParam<Locale>("locale"),
                dto = call.receive<PdfTemplateDTO>(),
            )
        )
    }

    /** Drops the saved layout for one locale, putting the packaged one back in service. */
    private fun Route.restoreTemplate() = delete("/{key}/{locale}") {
        if (!pdfTemplateService.restore(key(), getEnumPathParam<Locale>("locale"))) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(pdfTemplateService.describe(key()))
        }
    }

    /**
     * Prints the draft in the editor, against a real subject, and answers with the PDF.
     *
     * The PDF itself rather than the HTML behind it: the whole reason this renders through
     * a browser is that the print differs from the screen — page boxes, print media
     * queries, where the pages break — and a preview that skipped that would be the one
     * thing an operator cannot check.
     *
     * Which subject depends on the kind, and so does which service fills the layout in:
     * a layout is only previewable by the code that knows what its names mean, which is
     * the same reason [shared.enums.PdfDocumentKind] has no operator-added entries. A kind
     * added without a branch here is one the tab cannot show, so the `when` is exhaustive
     * rather than defaulting to the recipe sheet.
     */
    private fun Route.previewTemplate() = post("/{key}/{locale}/preview") {
        val locale = getEnumPathParam<Locale>("locale")
        val dto = call.receive<PdfPreviewDTO>()
        val kind = PdfDocumentKind.of(key()) ?: throw NotFoundException(NotFoundCause.PDF_TEMPLATE_NOT_FOUND)

        val pdf = when (kind) {
            PdfDocumentKind.RECIPE -> {
                val recipe = dto.subjectId?.let { recipeService.getEntityById(it) }
                    ?: recipeService.findMostRecent()
                exportService.generatePDF(recipe.toInfo(locale), locale, body = dto.body)
            }
            PdfDocumentKind.COOKBOOK -> {
                val cookbook = dto.subjectId?.let { cookbookService.getEntityById(it) }
                    ?: cookbookService.findMostRecent()
                exportService.generateCookbookPDF(cookbook.toInfo(), locale, body = dto.body)
            }
        }

        call.respondPDF("preview-${kind.key}-${locale.name.lowercase()}.pdf", pdf)
    }

    /** Document keys are enum-backed strings, so unlike the other controllers there is no id. */
    private fun RoutingContext.key(): String = getPathVariable("key") ?: ""
}
