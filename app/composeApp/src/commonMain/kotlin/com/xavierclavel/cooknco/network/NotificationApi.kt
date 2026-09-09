package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.DeviceRegistrationDTO
import com.xavierclavel.cooknco.network.dto.NotificationInfo
import com.xavierclavel.cooknco.network.dto.UserNotificationInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class NotificationApi(private val client: HttpClient) {

    private val base = "${ApiClient.BASE_URL}/notification"

    /**
     * Tells the backend where to push to.
     *
     * The locale is the one the app is running in, not the account's: the backend renders
     * each notification per device, so the language a notification arrives in is the
     * language of the client it arrives on.
     */
    suspend fun registerDevice(token: String, pushToken: String, locale: String = ApiClient.LOCALE) {
        val response = client.post("$base/devices") {
            bearerAuth(token)
            parameter("locale", locale)
            contentType(ContentType.Application.Json)
            setBody(DeviceRegistrationDTO(token = pushToken))
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
    }

    /**
     * Forgets this device, on sign-out.
     *
     * A POST carrying the token rather than a DELETE naming it in the path: a registration
     * token in a URL is a registration token in every access log along the way.
     */
    suspend fun unregisterDevice(token: String, pushToken: String) {
        val response = client.post("$base/devices/unregister") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(DeviceRegistrationDTO(token = pushToken))
        }
        // A 404 means it was already gone, which is the outcome the caller wanted
        if (!response.status.isSuccess() && response.status.value != 404) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    /** Everything the bell shows: the notification list, the unread count, follow requests. */
    suspend fun getNotifications(token: String): NotificationInfo {
        val response = client.get(base) {
            bearerAuth(token)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun listNotifications(token: String, page: Int, size: Int): List<UserNotificationInfo> {
        val response = client.get("$base/list") {
            bearerAuth(token)
            parameter("page", page)
            parameter("size", size)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }

    suspend fun markRead(token: String, notificationId: Long) {
        val response = client.post("$base/$notificationId/read") { bearerAuth(token) }
        if (!response.status.isSuccess() && response.status.value != 404) {
            throw ApiException(response.status, response.bodyAsText())
        }
    }

    suspend fun markAllRead(token: String) {
        val response = client.post("$base/read") { bearerAuth(token) }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
    }
}
