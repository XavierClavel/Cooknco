package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import shared.enums.Locale
import shared.enums.UnitSystem
import shared.utils.URL.EXPORT_URL
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals

/**
 * @param token a session token to send as a bearer, for the app's way in. Null leaves the
 *   client's cookie to authenticate, which is the web app's.
 */
suspend fun HttpClient.exportRecipeRaw(
    recipeId: Long,
    locale: Locale? = null,
    unitSystem: UnitSystem? = null,
    token: String? = null,
): HttpResponse =
    this.get("$EXPORT_URL/recipe/$recipeId") {
        token?.let { bearerAuth(it) }
        url {
            locale?.let { parameters.append("locale", it.name) }
            unitSystem?.let { parameters.append("unitSystem", it.name) }
        }
    }

suspend fun HttpClient.exportRecipe(
    recipeId: Long,
    locale: Locale? = null,
    unitSystem: UnitSystem? = null,
): ByteArray =
    this.exportRecipeRaw(recipeId, locale, unitSystem).let {
        assertEquals(HttpStatusCode.OK, it.status)
        it.bodyAsBytes()
    }

/** See [exportRecipeRaw] for [token]. */
suspend fun HttpClient.exportCookbookRaw(
    cookbookId: Long,
    locale: Locale? = null,
    unitSystem: UnitSystem? = null,
    token: String? = null,
): HttpResponse =
    this.get("$EXPORT_URL/cookbook/$cookbookId") {
        token?.let { bearerAuth(it) }
        url {
            locale?.let { parameters.append("locale", it.name) }
            unitSystem?.let { parameters.append("unitSystem", it.name) }
        }
    }

suspend fun HttpClient.exportCookbook(
    cookbookId: Long,
    locale: Locale? = null,
    unitSystem: UnitSystem? = null,
): ByteArray =
    this.exportCookbookRaw(cookbookId, locale, unitSystem).let {
        assertEquals(HttpStatusCode.OK, it.status)
        it.bodyAsBytes()
    }

/** How many pages a PDF came out at, which is what says a book kept a recipe per page. */
fun countPdfPages(pdf: ByteArray): Int =
    PdfDocument(PdfReader(ByteArrayInputStream(pdf))).use { it.numberOfPages }

/** Every page of a PDF as text, so a test can assert on what a reader would actually see. */
fun readPdfText(pdf: ByteArray): String = readPdfPages(pdf).joinToString("\n")

/**
 * The same, kept page by page.
 *
 * What a book's contents claims is only checkable against where each recipe actually is,
 * and that is a question about pages rather than about the text as a whole.
 */
fun readPdfPages(pdf: ByteArray): List<String> =
    PdfDocument(PdfReader(ByteArrayInputStream(pdf))).use { document ->
        (1..document.numberOfPages).map { PdfTextExtractor.getTextFromPage(document.getPage(it)) }
    }

/**
 * The page a contents line gives for [title], or null when it gives none.
 *
 * Read with a pattern rather than by an exact string: the number sits at the far side of
 * the row, and how much whitespace lands between the two is the layout's business.
 */
fun pageInContents(contents: String, title: String): Int? =
    Regex("${Regex.escape(title)}\\s+(\\d+)").find(contents)?.groupValues?.get(1)?.toInt()

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
