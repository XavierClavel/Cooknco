package com.xavierclavel.cooknco.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    const val BASE_URL = "https://cooknco.eu/api/v1"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val httpClient: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        expectSuccess = false
    }
}
