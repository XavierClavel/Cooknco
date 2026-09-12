package com.xavierclavel.cooknco.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    const val BASE_URL = "https://cooknco.eu/api/v1"
    const val IMAGE_URL = "https://cooknco.eu/image"

    /**
     * The locale the app asks the API for *content* in — ingredient names, recipe exports.
     *
     * Written by [com.xavierclavel.cooknco.data.AppLanguage], which resolves it from the
     * account's language and the handset's. It was a constant `EN` for as long as the app's
     * own copy was English only: asking for French ingredient names inside an English screen
     * would have read worse than not. Now that the screens speak both, the two agree.
     *
     * A plain `var`: it is written from the main thread when the language resolves and read
     * from request threads, and a reference assignment is atomic — the worst a reader can
     * see is the language from a moment ago, on a request it is about to repeat anyway.
     */
    var locale: String = "EN"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // No explicit engine: each target contributes exactly one (OkHttp on Android,
    // Darwin on iOS) and Ktor picks it up.
    val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        expectSuccess = false
    }
}

/**
 * Reads a JSON body the API serves as `text/plain`.
 *
 * `GET /user` and `GET /ingredient` answer a generic `SearchResult<T>`, which has no
 * serializer Ktor can find by type alone, so those two controllers encode it themselves
 * and `call.respond` a plain String — which goes out as `text/plain`. ContentNegotiation
 * only converts the content types it registered, so `body()` throws
 * `NoTransformationFoundException` on them however well-formed the JSON is. Decoding the
 * text ourselves ignores the content type, and keeps working if the backend ever labels
 * those responses correctly.
 */
suspend inline fun <reified T> HttpResponse.decodeJsonText(): T =
    ApiClient.json.decodeFromString(bodyAsText())
