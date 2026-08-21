package com.xavierclavel.cooknco.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    const val BASE_URL = "https://cooknco.eu/api/v1"
    const val IMAGE_URL = "https://cooknco.eu/image"

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
