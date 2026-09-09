package com.xavierclavel.cooknco.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    const val BASE_URL = "https://cooknco.eu/api/v1"
    const val IMAGE_URL = "https://cooknco.eu/image"

    /**
     * The locale the app asks the API for, and registers its devices under.
     *
     * A constant because the app has no language switch yet — the endpoints that take a
     * locale were already passing this literal, and this is them agreeing on one place to
     * change when it does.
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
