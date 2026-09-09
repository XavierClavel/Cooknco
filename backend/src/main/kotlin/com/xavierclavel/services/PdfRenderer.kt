package com.xavierclavel.services

import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.utils.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns a finished HTML document into a PDF.
 *
 * An interface for the reason [AppShellSource] is one: the thing that does the work is not
 * in this image and can only be reached over the network.
 */
interface PdfRenderer {
    /**
     * @param html the whole document, rendered as the page's `index.html`
     * @param assets files [html] refers to by relative name — a picture, a font. Nothing is
     *   fetched from anywhere else, so whatever the document needs has to be in here.
     * @throws ServiceUnavailableException when no PDF could be produced
     */
    suspend fun render(html: String, assets: Map<String, ByteArray> = emptyMap()): ByteArray
}

/**
 * Renders through Gotenberg, which is headless Chromium behind an HTTP form.
 *
 * Chromium rather than a PDF library laying boxes out itself, because the layouts this
 * prints are written by operators in the backoffice and previewed in a browser. A renderer
 * with its own idea of CSS would disagree with that preview, and the disagreement would be
 * the operator's problem to discover one export at a time.
 *
 * The service is run with an allow-list of the temp directory it unpacks uploads into
 * (`k8s/base/gotenberg.yaml`), so a layout naming a URL gets nothing: the only files the
 * page can read are the ones posted with it. That matters because a saved layout is markup
 * an operator wrote, and a browser that would fetch what it names is a way to reach the
 * cluster from inside it.
 */
class GotenbergPdfRenderer(
    /** Where the service answers; `Configuration.Pdf.gotenbergUrl` in production. */
    private val baseUrl: String,
    /** Prints allowed at once. See `Configuration.Pdf.maxConcurrentRenders`. */
    concurrency: Int = 2,
    /** How long a print waits for a turn before the caller is told to come back. */
    private val queueMillis: Long = 20_000,
    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            // A sheet takes well under a second; anything near this is Chromium stuck on a
            // layout, and the export is better off failing than holding the request open.
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 5_000
        }
    },
) : PdfRenderer {

    companion object {
        /** What Gotenberg treats as the page; every other part is a file next to it. */
        private const val INDEX = "index.html"
        private const val ROUTE = "forms/chromium/convert/html"
    }

    /**
     * The number of prints allowed to be in flight.
     *
     * Each one is a browser tab on the other side, so this is the backpressure that keeps a
     * burst — the backoffice's live preview, two operators, a tab left reloading — from
     * turning into a queue of tabs Gotenberg has to hold open. Callers wait their turn, and
     * are refused rather than left hanging if the wait runs long.
     */
    private val slots = Semaphore(concurrency)

    override suspend fun render(html: String, assets: Map<String, ByteArray>): ByteArray {
        val url = "${baseUrl.trimEnd('/')}/$ROUTE"

        // `acquire` releases its permit if the wait is cancelled, so a timeout leaks nothing.
        withTimeoutOrNull(queueMillis) { slots.acquire() }
            ?: run {
                logger.warn { "PDF renderer busy: no slot within ${queueMillis}ms" }
                throw ServiceUnavailableException(ServiceUnavailableCause.PDF_RENDERER_BUSY)
            }

        try {
            return print(url, html, assets)
        } finally {
            slots.release()
        }
    }

    private suspend fun print(url: String, html: String, assets: Map<String, ByteArray>): ByteArray {
        val response = try {
            client.post(url) {
                setBody(MultiPartFormDataContent(formData {
                    appendFile(INDEX, html.toByteArray())
                    assets.forEach { (name, bytes) -> appendFile(name, bytes) }
                }))
            }
        } catch (e: Exception) {
            logger.error(e) { "Could not reach the PDF renderer at $url" }
            throw ServiceUnavailableException(ServiceUnavailableCause.PDF_RENDERER_UNAVAILABLE)
        }

        if (!response.status.isSuccess()) {
            // Gotenberg answers a failed conversion in plain text, and it names the reason
            val reason = response.bodyAsText()
            logger.error { "PDF renderer refused the document: ${response.status} $reason" }
            throw ServiceUnavailableException(ServiceUnavailableCause.PDF_RENDERER_FAILED)
        }

        return response.bodyAsBytes()
    }

    /** Gotenberg reads the part's filename, not its field name, so every part is `files`. */
    private fun FormBuilder.appendFile(name: String, bytes: ByteArray) =
        append("files", bytes, Headers.build {
            append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
        })
}
