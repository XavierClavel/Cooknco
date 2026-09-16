package com.xavierclavel.utils

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination

/**
 * Reading back where things landed in a finished PDF.
 *
 * The one thing a document cannot know about itself while it is being written: CSS has a
 * `target-counter()` for exactly this and Chromium implements none of it, and the page is
 * printed with scripting switched off (`--chromium-disable-javascript` in
 * `k8s/base/gotenberg.yaml`), so nothing inside the layout can measure the layout. A book's
 * contents is therefore numbered from the print before it — see
 * [com.xavierclavel.services.ExportService.generateCookbookPDF].
 *
 * Reading only. Nothing here writes a PDF, and nothing should: the printing is Chromium's,
 * in its own service.
 */
object PdfDestinations {

    /**
     * The page each of [names] landed on, 1-based, for the names that could be found.
     *
     * The names are HTML `id`s. Chromium writes one into the PDF as a named destination —
     * but **only when something in the document links to it**, which is why the contents
     * rows carry `href="#{{anchor}}"` and not merely the recipes an `id`. A name nothing
     * linked to is simply absent from the result rather than an error: the caller's answer
     * to not knowing where a recipe is, is to print no number for it.
     *
     * @return name to page, missing entries for names the document does not resolve
     */
    fun pagesOf(pdf: ByteArray, names: Collection<String>): Map<String, Int> {
        val found: Map<String, Int>? = read(pdf) { document: PDDocument ->
            val pages = HashMap<String, Int>()
            for (name in names) {
                // `retrievePageNumber` walks the page tree for us and answers -1 when the
                // destination names no page it can reach.
                val index = try {
                    document.documentCatalog
                        .findNamedDestinationPage(PDNamedDestination(name))
                        ?.retrievePageNumber() ?: -1
                } catch (e: Exception) {
                    -1
                }
                if (index >= 0) pages.put(name, index + 1)
            }
            pages
        }
        return found ?: emptyMap()
    }

    /** How many pages the print came out at, or null if it could not be read. */
    fun pageCountOf(pdf: ByteArray): Int? = read(pdf) { document: PDDocument -> document.numberOfPages }

    /**
     * Never fatal.
     *
     * A book that has been printed is worth handing over even if we cannot read it back;
     * all that is lost is the numbering, which every caller treats as optional. The bytes
     * come from Chromium rather than from anyone's upload, so a failure here is a bug or an
     * environment fault, not input to be validated — but it is still not worth an export.
     */
    private fun <T : Any> read(pdf: ByteArray, block: (PDDocument) -> T): T? = try {
        Loader.loadPDF(pdf).use(block)
    } catch (e: Exception) {
        logger.error(e) { "Could not read back the printed document" }
        null
    }
}
