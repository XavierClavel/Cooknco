package main.com.xavierclavel.servicetests

import com.xavierclavel.utils.PdfTemplates
import org.junit.jupiter.api.Test
import shared.enums.Locale
import shared.enums.PdfDocumentKind
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse

/**
 * Lifting the page footer back out of a layout.
 *
 * A folio cannot be drawn by the document that needs it — Chromium implements none of the
 * CSS page margin boxes — so it travels inside the layout in a `<template id="page-footer">`
 * and is handed to the renderer separately. What is worth pinning down is where that
 * search must *not* look.
 */
class PdfTemplatesTest {

    private val footer = """<div class="folio"><span class="pageNumber"></span></div>"""

    @Test
    fun `a layout with no footer asks for none`() {
        assertNull(PdfTemplates.footerOf("<h1>a sheet</h1>"))
    }

    @Test
    fun `the footer is lifted out of the layout`() {
        val body = """<h1>a book</h1><template id="page-footer">$footer</template>"""
        assertEquals(footer, PdfTemplates.footerOf(body))
    }

    @Test
    fun `an empty footer asks for none, rather than for an empty one`() {
        assertNull(PdfTemplates.footerOf("""<template id="page-footer">   </template>"""))
    }

    /**
     * The one that bit: both packaged layouts explain the convention in a comment at the
     * top, naming the element. Taking that mention for the real thing made the footer
     * everything from the comment to the end of the file, so every page was printed with
     * the whole book in its bottom margin.
     */
    @Test
    fun `a comment naming the footer is not mistaken for the footer`() {
        val body = """
            <!-- The folio is the <template id="page-footer"> at the end of this file. -->
            <h1>a book</h1>
            <template id="page-footer">$footer</template>
        """.trimIndent()

        val found = PdfTemplates.footerOf(body)

        assertEquals(footer, found)
        assertFalse(found!!.contains("<h1>"), "the book leaked into its own footer")
    }

    /** Both packaged books ship one, which is what puts a folio on the pages they print. */
    @Test
    fun `the packaged cookbook layouts carry a page footer`() {
        Locale.entries.forEach { locale ->
            val packaged = PdfTemplates.packaged(PdfDocumentKind.COOKBOOK, locale)
            val found = PdfTemplates.footerOf(packaged)
            assertNotNull(found, "the $locale cookbook layout lost its folio")
            assertContains(found, "pageNumber")
        }
    }

    @Test
    fun `the footer may name values, which are filled in before it is lifted out`() {
        val rendered = PdfTemplates.render(
            """<template id="page-footer"><span>{{siteName}}</span></template>""",
            mapOf("siteName" to "Cook&Co"),
        )
        // Mustache escapes on the way out, so the ampersand arrives as an entity
        assertEquals("<span>Cook&amp;Co</span>", PdfTemplates.footerOf(rendered))
    }
}
