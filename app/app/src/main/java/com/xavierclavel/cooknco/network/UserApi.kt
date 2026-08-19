package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.UserDTO
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.network.dto.UserSettingsDTO
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class UserApi(private val client: HttpClient) {

    private val base = ApiClient.BASE_URL

    suspend fun getUser(userId: Long): UserInfo {
        val response = client.get("$base/user/$userId")
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun updateUser(token: String, username: String, bio: String): UserInfo {
        val response = client.put("$base/user") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UserDTO(username = username, mail = "", bio = bio))
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun uploadProfileImage(token: String, userId: Long, imageBytes: ByteArray, mimeType: String): Long {
        val response = client.put("$base/user/$userId/image") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(formData {
                append("file", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, mimeType)
                    append(HttpHeaders.ContentDisposition, "filename=profile.webp")
                })
            }))
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun isFollowing(token: String, userId: Long): Boolean {
        val response = client.get("$base/follow/$userId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun follow(token: String, userId: Long) {
        val response = client.post("$base/follow/$userId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
    }

    suspend fun unfollow(token: String, userId: Long) {
        val response = client.delete("$base/follow/$userId") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
    }

    suspend fun getSettings(token: String): UserSettingsDTO {
        val response = client.get("$base/user/settings") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun updateSettings(token: String, settings: UserSettingsDTO): UserSettingsDTO {
        val response = client.put("$base/user/settings") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(settings)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun getUserRecipes(token: String?, profileUserId: Long, page: Int, size: Int = 20): List<com.xavierclavel.cooknco.network.dto.RecipeOverview> {
        val response = client.get("$base/recipe") {
            if (token != null) bearerAuth(token)
            parameter("user", profileUserId)
            parameter("sort", "DATE_DESCENDING")
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }
}
