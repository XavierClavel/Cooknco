package com.xavierclavel.utils

import com.samskivert.mustache.Mustache
import com.samskivert.mustache.MustacheException
import shared.enums.Locale
import shared.enums.PdfDocumentKind

/**
 * Reading and filling in the layouts documents are rendered from.
 *
 * Mustache rather than the flat `{{name}}` substitution mails use ([shared.utils.EmailTemplates]):
 * a recipe sheet has to repeat a row per ingredient and per step, which a search-and-replace
 * cannot express. Mustache is the smallest thing that can — it evaluates no expressions and
 * calls no methods of its own, so a layout an operator saved is markup and data lookups, and
 * never a way to run something inside the backend.
 *
 * Values are escaped as HTML on the way in, so a recipe titled `<script>` prints as text.
 */
object PdfTemplates {
    /** Longest layout the backoffice accepts, and so the width of the column holding one. */
    const val MAX_BODY_LENGTH = 65535

    /**
     * Left alone rather than blanked, and never fatal.
     *
     * A name nothing fills is the operator's own typo, and a sheet with one line missing is
     * worth more than a 500. `defaultValue` is what stops Mustache throwing on it.
     */
    private val compiler: Mustache.Compiler = Mustache.compiler()
        .defaultValue("")
        .escapeHTML(true)
        // So `{{#tips}}…{{/tips}}` drops out on a recipe with no tips, and `{{#yield}}` on
        // one that serves nobody. Neither is Mustache's default, and both are what an
        // operator writing `{{#cookingTime}}Cooking {{cookingTime}} min{{/cookingTime}}`
        // means by it.
        .emptyStringIsFalse(true)
        .zeroIsFalse(true)

    /** The layout packaged in the jar, which every kind has and no operator can remove. */
    fun packaged(kind: PdfDocumentKind, locale: Locale): String {
        val resource = kind.resource(locale)
        return javaClass.getResource(resource)?.readText()
            ?: error("PDF template $resource is missing from the jar")
    }

    /**
     * Fills a layout in.
     *
     * @throws MustacheException when the layout itself is malformed — an unclosed section,
     *   say. The caller decides what that costs; a saved layout is checked by
     *   [com.xavierclavel.services.PdfTemplateService] before it can ever reach here.
     */
    fun render(template: String, model: Map<String, Any?>): String =
        compiler.compile(template).execute(model)

    /** Whether a layout can be compiled at all, so a broken one is refused at save time. */
    fun compiles(template: String): Boolean = try {
        compiler.compile(template)
        true
    } catch (e: MustacheException) {
        false
    }
}
