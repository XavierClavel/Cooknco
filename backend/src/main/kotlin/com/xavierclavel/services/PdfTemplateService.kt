package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.PdfTemplate
import com.xavierclavel.models.query.QPdfTemplate
import com.xavierclavel.utils.PdfTemplates
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import shared.dto.PdfTemplateDTO
import shared.enums.Locale
import shared.enums.PdfDocumentKind
import shared.infodto.AdminPdfTemplateInfo
import shared.infodto.AdminPdfTemplateLocale

/**
 * The layouts the application renders its documents from.
 *
 * Every document has two possible sources, in order: the layout an operator saved from the
 * backoffice, and the one packaged in the jar. The packaged layout is a floor rather than a
 * seed — it is never written to the database — so a fresh install exports something
 * presentable, the tab shows no rows nobody wrote, and restoring a layout is just deleting
 * the override. This is [EmailTemplateService]'s design, for the same reasons.
 */
class PdfTemplateService: KoinComponent {

    // ------------------------------------------------------------------- reading

    /** The layout to render a document with: the saved one, or the packaged one. */
    fun bodyOf(kind: PdfDocumentKind, locale: Locale): String =
        findOverride(kind.key, locale)?.body ?: PdfTemplates.packaged(kind, locale)

    /** Every kind of document, with what is rendered for each locale and where it comes from. */
    fun describe(): List<AdminPdfTemplateInfo> {
        val overrides = findOverrides()
        return PdfDocumentKind.entries.map { describe(it, overrides) }
    }

    fun describe(key: String): AdminPdfTemplateInfo = describe(requireKind(key), findOverrides())

    private fun describe(
        kind: PdfDocumentKind,
        overrides: Map<Pair<String, Locale>, PdfTemplate>,
    ) = AdminPdfTemplateInfo(
        key = kind.key,
        variables = kind.variables,
        locales = Locale.entries.map { locale ->
            overrides[kind.key to locale]?.toInfo() ?: AdminPdfTemplateLocale(
                locale = locale,
                body = PdfTemplates.packaged(kind, locale),
                custom = false,
            )
        },
    )

    private fun findOverrides(): Map<Pair<String, Locale>, PdfTemplate> =
        QPdfTemplate().findList().associateBy { it.key to it.locale }

    private fun findOverride(key: String, locale: Locale): PdfTemplate? =
        QPdfTemplate().key.eq(key).locale.eq(locale).findOne()

    // ------------------------------------------------------------------- writing

    /** Saves a layout over whatever is being rendered, packaged or not. */
    fun save(key: String, locale: Locale, dto: PdfTemplateDTO): AdminPdfTemplateInfo {
        val kind = requireKind(key)
        validate(dto.body)

        val template = findOverride(kind.key, locale) ?: PdfTemplate(key = kind.key, locale = locale)
        template.body = dto.body.trim()
        template.save()

        logger.info { "PDF template ${kind.key}/$locale saved" }
        return describe(kind.key)
    }

    /**
     * Drops an override, which puts the packaged layout back in service.
     *
     * @return false when there was no override to remove
     */
    fun restore(key: String, locale: Locale): Boolean {
        val kind = requireKind(key)
        val removed = findOverride(kind.key, locale)?.delete() ?: false
        if (removed) logger.info { "PDF template ${kind.key}/$locale restored to the packaged layout" }
        return removed
    }

    /**
     * Refuses a layout that could not render.
     *
     * Only what would break the export is checked. A `{{name}}` nothing fills prints as
     * nothing and is the operator's to notice in the preview; an unclosed section is a
     * layout Mustache cannot compile at all, and saving it would take the next export down
     * with it.
     */
    private fun validate(body: String) {
        if (body.isBlank()) throw BadRequestException(BadRequestCause.PDF_TEMPLATE_EMPTY)
        if (body.length > PdfTemplates.MAX_BODY_LENGTH) {
            throw BadRequestException(BadRequestCause.PDF_TEMPLATE_TOO_LONG)
        }
        if (!PdfTemplates.compiles(body)) throw BadRequestException(BadRequestCause.PDF_TEMPLATE_MALFORMED)
    }

    private fun requireKind(key: String): PdfDocumentKind =
        PdfDocumentKind.of(key) ?: throw NotFoundException(NotFoundCause.PDF_TEMPLATE_NOT_FOUND)
}
