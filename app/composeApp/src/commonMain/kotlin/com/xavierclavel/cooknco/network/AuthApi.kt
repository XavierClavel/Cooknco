package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.SessionDto
import com.xavierclavel.cooknco.network.dto.UserDTO
import com.xavierclavel.cooknco.network.dto.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64

class AuthApi(private val client: HttpClient) {

    private val base = ApiClient.BASE_URL

    suspend fun login(email: String, password: String): SessionDto {
        val credentials = Base64.encode("$email:$password".encodeToByteArray())
        val response = client.post("$base/auth/login") {
            header(HttpHeaders.Authorization, "Basic $credentials")
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun signup(username: String, email: String, password: String): UserInfo {
        val response = client.post("$base/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(UserDTO(username = username, mail = email, password = password))
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun whoami(token: String): UserInfo {
        val response = client.get("$base/auth/me") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun logout(token: String) {
        client.post("$base/auth/logout") {
            bearerAuth(token)
        }
    }
}

class ApiException(val status: HttpStatusCode, val body: String) :
    Exception("HTTP ${status.value}: $body")
