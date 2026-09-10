package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import shared.dto.AnnouncementDTO
import shared.dto.DeviceRegistrationDTO
import shared.dto.NotificationTestDTO
import shared.enums.DevicePlatform
import shared.enums.Locale
import shared.infodto.AdminNotificationSendInfo
import shared.infodto.AdminNotificationTestInfo
import shared.infodto.AdminPushAudienceInfo
import shared.infodto.NotificationInfo
import shared.infodto.UserNotificationInfo
import shared.utils.URL.ADMIN_URL
import shared.utils.URL.NOTIFICATION_URL
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

// ------------------------------------------------------------------- devices

suspend fun HttpClient.registerDeviceRaw(
    token: String,
    platform: DevicePlatform = DevicePlatform.ANDROID,
    locale: Locale = Locale.EN,
    appVersion: String = "",
) = this.post("$NOTIFICATION_URL/devices?locale=${locale.name}") {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(DeviceRegistrationDTO(token = token, platform = platform, appVersion = appVersion))
}

suspend fun HttpClient.registerDevice(
    token: String,
    platform: DevicePlatform = DevicePlatform.ANDROID,
    locale: Locale = Locale.EN,
    appVersion: String = "",
) = this.registerDeviceRaw(token, platform, locale, appVersion).apply {
    assertEquals(HttpStatusCode.Created, status)
}

/**
 * Registers exactly what an app that predates `appVersion` sends: a body with no such
 * field at all, rather than one carrying an empty string.
 *
 * Written as raw JSON because the DTO cannot express it — a Kotlin caller always has the
 * property, and the server's `Json` encodes defaults, so a typed helper would put the
 * field on the wire and never exercise the case this is here for.
 */
suspend fun HttpClient.registerLegacyDeviceRaw(
    token: String,
    platform: DevicePlatform = DevicePlatform.ANDROID,
    locale: Locale = Locale.EN,
) = this.post("$NOTIFICATION_URL/devices?locale=${locale.name}") {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody("""{"token":"$token","platform":"${platform.name}"}""")
}

suspend fun HttpClient.unregisterDeviceRaw(token: String) =
    this.post("$NOTIFICATION_URL/devices/unregister") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(DeviceRegistrationDTO(token = token, platform = DevicePlatform.ANDROID))
    }

// ------------------------------------------------------------- notifications

suspend fun HttpClient.getNotificationsRaw() = this.get(NOTIFICATION_URL)

suspend fun HttpClient.getNotifications(): NotificationInfo =
    this.getNotificationsRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<NotificationInfo>(it.bodyAsText())
    }

suspend fun HttpClient.listNotifications(): List<UserNotificationInfo> =
    this.get("$NOTIFICATION_URL/list").let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<List<UserNotificationInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.markNotificationReadRaw(id: Long) = this.post("$NOTIFICATION_URL/$id/read")

suspend fun HttpClient.markAllNotificationsReadRaw() = this.post("$NOTIFICATION_URL/read")

// ------------------------------------------------------------------ admin

suspend fun HttpClient.getPushAudienceRaw(users: List<Long> = emptyList(), locale: Locale? = null) =
    this.get("$ADMIN_URL/notifications/audience") {
        url {
            if (users.isNotEmpty()) parameters.append("users", users.joinToString(","))
            locale?.let { parameters.append("locale", it.name) }
        }
    }

suspend fun HttpClient.getPushAudience(
    users: List<Long> = emptyList(),
    locale: Locale? = null,
): AdminPushAudienceInfo = this.getPushAudienceRaw(users, locale).let {
    assertEquals(HttpStatusCode.OK, it.status)
    json.decodeFromString<AdminPushAudienceInfo>(it.bodyAsText())
}

suspend fun HttpClient.announceRaw(
    title: String = "Heads up",
    body: String = "The kitchen is closed.",
    link: String = "",
    userIds: List<Long> = emptyList(),
    locale: Locale? = null,
) = this.post("$ADMIN_URL/notifications/announce") {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(AnnouncementDTO(title = title, body = body, link = link, userIds = userIds, locale = locale))
}

suspend fun HttpClient.announce(
    title: String = "Heads up",
    body: String = "The kitchen is closed.",
    link: String = "",
    userIds: List<Long> = emptyList(),
    locale: Locale? = null,
): AdminNotificationSendInfo = this.announceRaw(title, body, link, userIds, locale).let {
    assertEquals(HttpStatusCode.Accepted, it.status)
    json.decodeFromString<AdminNotificationSendInfo>(it.bodyAsText())
}

suspend fun HttpClient.sendTestNotificationRaw(
    title: String = "Test",
    body: String = "Test body",
    link: String = "",
) = this.post("$ADMIN_URL/notifications/test") {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(NotificationTestDTO(title = title, body = body, link = link))
}

suspend fun HttpClient.sendTestNotification(
    title: String = "Test",
    body: String = "Test body",
    link: String = "",
): AdminNotificationTestInfo = this.sendTestNotificationRaw(title, body, link).let {
    assertEquals(HttpStatusCode.OK, it.status)
    json.decodeFromString<AdminNotificationTestInfo>(it.bodyAsText())
}
