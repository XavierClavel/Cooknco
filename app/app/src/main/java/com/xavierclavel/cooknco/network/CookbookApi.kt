package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.network.dto.CookbookSaveDto
import com.xavierclavel.cooknco.network.dto.CookbookUserInfo
import com.xavierclavel.cooknco.network.dto.CookbookUserSaveDto
import com.xavierclavel.cooknco.network.dto.UserSearchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class CookbookApi(private val client: HttpClient) {

    private val base = ApiClient.BASE_URL

    suspend fun listCookbooks(token: String, userId: Long): List<CookbookInfo> {
        val response = client.get("$base/cookbook") {
            bearerAuth(token)
            parameter("user", userId)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun getCookbook(id: Long, token: String? = null): CookbookInfo {
        val response = client.get("$base/cookbook/$id") {
            if (token != null) bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun createCookbook(token: String, dto: CookbookSaveDto): CookbookInfo {
        val response = client.post("$base/cookbook") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun updateCookbook(id: Long, token: String, dto: CookbookSaveDto): CookbookInfo {
        val response = client.put("$base/cookbook/$id") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun deleteCookbook(id: Long, token: String) {
        val response = client.delete("$base/cookbook/$id") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    suspend fun isAdminOfCookbook(id: Long, token: String): Boolean {
        val response = client.get("$base/cookbook/$id/userStatus") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun leaveCookbook(id: Long, token: String) {
        val response = client.delete("$base/cookbook/$id/leave") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    suspend fun getCookbookUsers(id: Long, token: String): List<CookbookUserInfo> {
        val response = client.get("$base/cookbook/$id/users") {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun setCookbookUsers(id: Long, token: String, users: List<CookbookUserSaveDto>) {
        val response = client.put("$base/cookbook/$id/users") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(users)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    suspend fun getCookbookRecipes(id: Long, token: String? = null): List<CookbookRecipeInfo> {
        val response = client.get("$base/cookbook/$id/recipes") {
            if (token != null) bearerAuth(token)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }

    suspend fun searchUsers(query: String, token: String? = null): UserSearchResult {
        val response = client.get("$base/user") {
            if (token != null) bearerAuth(token)
            parameter("query", query)
            parameter("page", 0)
            parameter("size", 20)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status, response.bodyAsText())
        }
        return response.body()
    }
}
