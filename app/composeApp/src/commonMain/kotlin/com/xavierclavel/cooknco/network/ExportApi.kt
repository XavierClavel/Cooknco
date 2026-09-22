package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.platform.COOKLANG_MIME_TYPE
import com.xavierclavel.cooknco.platform.PDF_MIME_TYPE
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

/**
 * A PDF the backend printed, and the name it was given.
 *
 * The name comes from the response rather than being built here: the backend slugs the
 * recipe's own title into it (`ExportService.filenameOf`), and a file the user is about to
 * save somewhere should be called what the web app's download is called.
 */
class ExportedDocument(
    val filename: String,
    val bytes: ByteArray,
    /**
     * What the file is, for the platform's save picker. Carried on the document rather than
     * decided where it is saved, because the only thing that knows which export this was is
     * the call that asked for it.
     */
    val mimeType: String = PDF_MIME_TYPE,
)

/**
 * `GET /export/…`, the same two routes the backoffice's export buttons call.
 *
 * Premium on the server, and the app only offers it to accounts that hold it — but the
 * gate that matters is that one, and so is the filtering behind it: what comes back holds
 * only what the caller may read. The token authenticates the call as a bearer, which is
 * all a native client can send; the web app sends a cookie instead.
 */
class ExportApi(private val client: HttpClient) {

    private val base = ApiClient.BASE_URL

    suspend fun exportRecipe(
        token: String,
        recipeId: Long,
        locale: String,
        unitSystem: String,
    ): ExportedDocument =
        export("$base/export/recipe/$recipeId", token, locale, unitSystem, "recipe-$recipeId.pdf")

    /**
     * The same recipe as a Cooklang file.
     *
     * No `unitSystem`, unlike the two above: a `.cook` file is loaded by another app rather
     * than read by a person, so it goes out in the units the recipe was written in and
     * whatever opens it converts for whoever is looking. Premium on the server exactly as
     * the PDF is.
     */
    suspend fun exportRecipeAsCooklang(
        token: String,
        recipeId: Long,
        locale: String,
    ): ExportedDocument {
        val response = client.get("$base/export/recipe/$recipeId/cooklang") {
            bearerAuth(token)
            parameter("locale", locale)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return ExportedDocument(
            filename = response.filename() ?: "recipe-$recipeId.cook",
            bytes = response.readRawBytes(),
            mimeType = COOKLANG_MIME_TYPE,
        )
    }

    suspend fun exportCookbook(
        token: String,
        cookbookId: Long,
        locale: String,
        unitSystem: String,
    ): ExportedDocument =
        export("$base/export/cookbook/$cookbookId", token, locale, unitSystem, "cookbook-$cookbookId.pdf")

    /**
     * @param locale which language the headings and the ingredient names are printed in
     * @param unitSystem which ladder the amounts are printed on. Both are asked for rather
     *   than left to the server's defaults (EN, metric): a sheet printed from a French app
     *   showing grams should read the way the screen it was printed from reads.
     */
    private suspend fun export(
        url: String,
        token: String,
        locale: String,
        unitSystem: String,
        fallbackFilename: String,
    ): ExportedDocument {
        val response = client.get(url) {
            bearerAuth(token)
            parameter("locale", locale)
            parameter("unitSystem", unitSystem)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return ExportedDocument(
            filename = response.filename() ?: fallbackFilename,
            bytes = response.readRawBytes(),
        )
    }

    /**
     * The name off `Content-Disposition`, or null if the header is missing or unreadable —
     * in which case the caller names the file after what it asked for, which is a worse
     * name and never a wrong one.
     */
    private fun HttpResponse.filename(): String? =
        headers[HttpHeaders.ContentDisposition]
            ?.let { runCatching { ContentDisposition.parse(it) }.getOrNull() }
            ?.parameter(ContentDisposition.Parameters.FileName)
            ?.takeIf { it.isNotBlank() }
}
