package com.xavierclavel.cooknco.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    const val BASE_URL = "https://cooknco.eu/api/v1"
    const val IMAGE_URL = "https://cooknco.eu/image"

    /**
     * The locale the app asks the API for *content* in — ingredient names, recipe exports.
     *
     * Still a constant: the app's own copy is English-only, so asking for French ingredient
     * names inside an English screen would read worse than not. What the phone is actually
     * set to is [com.xavierclavel.cooknco.platform.deviceLocale], which is a different
     * question — it is what the backend writes *to* the user in, and it is reported rather
     * than assumed.
     */
    const val LOCALE = "EN"

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
