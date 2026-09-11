package main.com.xavierclavel.utils

import com.xavierclavel.services.UserService
import shared.dto.SearchResult
import shared.dto.UserDTO
import shared.dto.UserSettingsDTO
import shared.enums.Locale
import shared.infodto.UserInfo
import shared.utils.URL.AUTH_URL
import shared.utils.URL.USER_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID
import kotlin.getValue
import kotlin.test.assertEquals

val userService: UserService by inject(UserService::class.java)

/**
 * @param locale what the signing-up client reports, as a real one does. Null signs up
 *   without saying, which is the only way to get an account whose language is unknown —
 *   the state every account was in before this was managed, and the one the fallbacks are
 *   there for.
 */
suspend fun HttpClient.createUser(
    mail: String = UUID.randomUUID().toString(),
    locale: Locale? = Locale.EN,
): UserInfo  {
    this.post("$AUTH_URL/signup${locale?.let { "?locale=$it" } ?: ""}"){
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(UserDTO(mail = mail, username = UUID.randomUUID().toString(), password="password"))
    }.apply {
        assertEquals(HttpStatusCode.Created, status)
        val user = Json.decodeFromString<UserInfo>(bodyAsText())
        userService.validateUser(user.id)
        return user
    }
    //val response = this.getUser(username)
    //assertEquals(username, response.username)
}

/**
 * Signs up reporting a locale the API cannot possibly parse, which no client of ours sends
 * and any caller may. Raw rather than typed because the point is the unparseable string.
 */
suspend fun HttpClient.createUserRawLocale(mail: String, locale: String): UserInfo {
    this.post("$AUTH_URL/signup?locale=$locale") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(UserDTO(mail = mail, username = UUID.randomUUID().toString(), password = "password"))
    }.apply {
        assertEquals(HttpStatusCode.Created, status, "an unreadable locale is not worth an account")
        val user = Json.decodeFromString<UserInfo>(bodyAsText())
        userService.validateUser(user.id)
        return user
    }
}

suspend fun HttpClient.getUser(id: Long): UserInfo {
    this.get("$USER_URL/$id").apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString<UserInfo>(bodyAsText())
    }
}

suspend fun HttpClient.getMe(): UserInfo {
    this.get("$AUTH_URL/me").apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString<UserInfo>(bodyAsText())
    }
}

suspend fun HttpClient.deleteUser(id: Long) {
    this.delete("$USER_URL/$id").apply {
        assertEquals(HttpStatusCode.OK, status)
    }
    this.assertUserDoesNotExist(id)
}

suspend fun HttpClient.assertUserExists(id: Long) {
    this.get("$USER_URL/$id").apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}

suspend fun HttpClient.assertUserDoesNotExist(id: Long) {
    this.get("$USER_URL/$id").apply {
        assertEquals(HttpStatusCode.NotFound, status)
    }
}

suspend fun HttpClient.getSettingsRaw() = this.get("$USER_URL/settings")

suspend fun HttpClient.getSettings(): UserSettingsDTO {
    this.getSettingsRaw().apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString<UserSettingsDTO>(bodyAsText())
    }
}

suspend fun HttpClient.updateSettingsRaw(settings: UserSettingsDTO) =
    this.put("$USER_URL/settings") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(settings)
    }

suspend fun HttpClient.updateSettings(settings: UserSettingsDTO) =
    this.updateSettingsRaw(settings).apply { assertEquals(HttpStatusCode.OK, status) }

/**
 * Presses the unsubscribe link in a notification mail.
 *
 * Anonymous on purpose, as the real thing is: this is somebody in their inbox, and the page
 * behind the link is reached with no session at all.
 */
suspend fun HttpClient.unsubscribeRaw(token: String) = this.post("$USER_URL/unsubscribe?token=$token")

/** Saves a language the way the settings screen does, leaving the other settings as they are. */
suspend fun HttpClient.chooseLocale(locale: Locale) =
    this.updateSettings(this.getSettings().copy(locale = locale))

suspend fun HttpClient.listUsers() : SearchResult<UserInfo> {
    this.get(USER_URL).apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString<SearchResult<UserInfo>>(bodyAsText())
    }
}