package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import shared.enums.Locale
import shared.utils.URL.EXPORT_URL
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals

suspend fun HttpClient.exportRecipeRaw(recipeId: Long, locale: Locale? = null): HttpResponse =
    this.get("$EXPORT_URL/recipe/$recipeId") {
        url { locale?.let { parameters.append("locale", it.name) } }
    }

suspend fun HttpClient.exportRecipe(recipeId: Long, locale: Locale? = null): ByteArray =
    this.exportRecipeRaw(recipeId, locale).let {
        assertEquals(HttpStatusCode.OK, it.status)
        it.bodyAsBytes()
    }

/** Every page of a PDF as text, so a test can assert on what a reader would actually see. */
fun readPdfText(pdf: ByteArray): String =
    PdfDocument(PdfReader(ByteArrayInputStream(pdf))).use { document ->
        (1..document.numberOfPages).joinToString("\n") {
            PdfTextExtractor.getTextFromPage(document.getPage(it))
        }
    }

/**
 * The pictures a PDF actually embeds, as the bytes of each.
 *
 * Worth reading rather than trusting the PDF's own size: the generator logs and skips a
 * picture it cannot decode, and it falls back to a bucket default for a recipe that has
 * none, so a sheet showing the wrong picture — or no picture — is still a valid, sizeable
 * PDF. Only the bytes tell the three apart.
 */
fun readPdfImages(pdf: ByteArray): List<ByteArray> =
    PdfDocument(PdfReader(ByteArrayInputStream(pdf))).use { document ->
        (1..document.numberOfPages).flatMap { page ->
            val xObjects = document.getPage(page).resources.getResource(PdfName.XObject)
                ?: return@flatMap emptyList()
            xObjects.keySet()
                .mapNotNull { xObjects.getAsStream(it) }
                .filter { it.getAsName(PdfName.Subtype) == PdfName.Image }
                .map { PdfImageXObject(it).imageBytes }
        }
    }

/** The pixel size of an embedded picture, to tell a full-frame upload from a small default. */
fun dimensionsOfPdfImage(image: ByteArray): Pair<Int, Int> =
    ImageIO.read(ByteArrayInputStream(image)).let { it.width to it.height }
