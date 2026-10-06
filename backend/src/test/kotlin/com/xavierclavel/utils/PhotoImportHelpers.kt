package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import shared.enums.Locale
import shared.infodto.CooklangImportInfo
import shared.utils.URL.RECIPE_URL
import kotlin.test.assertEquals

/** Posts pages the way the app does: one file part per page, in order. */
suspend fun HttpClient.importPhotoRaw(
    pages: List<ByteArray> = listOf(testImageBytes()),
    locale: Locale? = null,
): HttpResponse =
    this.post("$RECIPE_URL/import/photo") {
        url { locale?.let { parameters.append("locale", it.name) } }
        setBody(MultiPartFormDataContent(formData {
            pages.forEachIndexed { index, bytes ->
                append("page", bytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=page$index.jpg")
                })
            }
        }))
    }

suspend fun HttpClient.importPhoto(
    pages: List<ByteArray> = listOf(testImageBytes()),
    locale: Locale? = null,
): CooklangImportInfo =
    this.importPhotoRaw(pages, locale).let {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        Json.decodeFromString<CooklangImportInfo>(it.bodyAsText())
    }
