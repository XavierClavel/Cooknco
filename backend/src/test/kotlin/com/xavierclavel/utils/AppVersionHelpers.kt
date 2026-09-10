package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import shared.dto.AppVersionDTO
import shared.enums.AppPlatform
import shared.infodto.AdminAppVersionInfo
import shared.infodto.AdminAppVersionReach
import shared.infodto.AppVersionCheckInfo
import shared.utils.URL.ADMIN_URL
import shared.utils.URL.APP_VERSION_URL
import kotlin.test.assertEquals

private val json = Json { ignoreUnknownKeys = true }

private const val APP_VERSIONS_URL = "$ADMIN_URL/app-versions"

// -------------------------------------------------------------- what a client asks

suspend fun HttpClient.checkAppVersionRaw(platform: String?, version: String?): HttpResponse =
    this.get(APP_VERSION_URL) {
        platform?.let { parameter("platform", it) }
        version?.let { parameter("version", it) }
    }

suspend fun HttpClient.checkAppVersion(platform: AppPlatform, version: String): AppVersionCheckInfo =
    this.checkAppVersionRaw(platform.name, version).let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<AppVersionCheckInfo>(it.bodyAsText())
    }

// ------------------------------------------------------------ what an operator sets

suspend fun HttpClient.listAppVersionsRaw(): HttpResponse = this.get(APP_VERSIONS_URL)

suspend fun HttpClient.listAppVersions(): List<AdminAppVersionInfo> =
    this.listAppVersionsRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        json.decodeFromString<List<AdminAppVersionInfo>>(it.bodyAsText())
    }

suspend fun HttpClient.appVersion(platform: AppPlatform): AdminAppVersionInfo =
    this.listAppVersions().first { it.platform == platform }

/**
 * The listing as it actually goes over the wire.
 *
 * Decoding into [AdminAppVersionInfo] would fill a missing field back in from its default,
 * so a field the server dropped would look present — which is the one thing the backoffice
 * form cannot survive.
 */
suspend fun HttpClient.listAppVersionsJson(): List<JsonObject> =
    this.listAppVersionsRaw().let {
        assertEquals(HttpStatusCode.OK, it.status)
        Json.parseToJsonElement(it.bodyAsText()).jsonArray.map { row -> row.jsonObject }
    }

suspend fun HttpClient.saveAppVersionRaw(
    platform: AppPlatform,
    minimum: String,
    latest: String,
    storeUrl: String = "https://play.google.com/store/apps/details?id=com.xavierclavel.cooknco",
) = this.put("$APP_VERSIONS_URL/${platform.name}") {
    contentType(ContentType.Application.Json)
    header(HttpHeaders.ContentType, ContentType.Application.Json)
    setBody(AppVersionDTO(minimumVersion = minimum, latestVersion = latest, storeUrl = storeUrl))
}

suspend fun HttpClient.saveAppVersion(
    platform: AppPlatform,
    minimum: String,
    latest: String,
    storeUrl: String = "https://play.google.com/store/apps/details?id=com.xavierclavel.cooknco",
): AdminAppVersionInfo = this.saveAppVersionRaw(platform, minimum, latest, storeUrl).let {
    assertEquals(HttpStatusCode.OK, it.status)
    json.decodeFromString<AdminAppVersionInfo>(it.bodyAsText())
}

suspend fun HttpClient.clearAppVersionRaw(platform: AppPlatform) =
    this.delete("$APP_VERSIONS_URL/${platform.name}")

// ---------------------------------------------------------- what a floor costs

suspend fun HttpClient.appVersionReachRaw(platform: AppPlatform, minimum: String? = null) =
    this.get("$APP_VERSIONS_URL/${platform.name}/reach") {
        minimum?.let { parameter("minimum", it) }
    }

suspend fun HttpClient.appVersionReach(
    platform: AppPlatform,
    minimum: String? = null,
): AdminAppVersionReach = this.appVersionReachRaw(platform, minimum).let {
    assertEquals(HttpStatusCode.OK, it.status)
    json.decodeFromString<AdminAppVersionReach>(it.bodyAsText())
}
