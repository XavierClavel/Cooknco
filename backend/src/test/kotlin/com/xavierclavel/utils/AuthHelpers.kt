package main.com.xavierclavel.utils

import shared.dto.PasswordDTO
import shared.dto.UserDTO
import shared.enums.Locale
import shared.infodto.UserInfo
import shared.utils.URL.AUTH_URL
import shared.utils.URL.USER_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
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
import java.util.UUID
import kotlin.test.assertEquals

suspend fun HttpClient.signup(mail: String = UUID.randomUUID().toString(), password: String = UUID.randomUUID().toString()): UserInfo  {
    this.post("$AUTH_URL/signup?locale=EN"){
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(UserDTO(mail = mail, username = UUID.randomUUID().toString(), password = password))
    }.apply {
        assertEquals(HttpStatusCode.Created, status)
        val user = Json.decodeFromString<UserInfo>(bodyAsText())
        return user
    }
}

/**
 * @param locale what the signing-in client reports, as a real one does. Null sends nothing,
 *   which is what every client did before this was managed — most tests want that, so that
 *   signing in does not quietly give an account a language the test did not ask for.
 */
suspend fun HttpClient.login(username: String, password: String, locale: Locale? = null) =
    this.post("$AUTH_URL/login${locale?.let { "?locale=$it" } ?: ""}") {
        basicAuth(username = username, password = password)
    }


suspend fun HttpClient.logout() =
    this.post("$AUTH_URL/logout")


suspend fun HttpClient.verifyUser(token: String) {
    this.post("$AUTH_URL/verify") {
        url {
            parameters.append("token", token)
        }
    }.apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}

suspend fun HttpClient.updatePassword(oldPassword: String, newPassword: String) =
    this.put("$USER_URL/password") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(PasswordDTO(old = oldPassword, new = newPassword))
    }

suspend fun HttpClient.requestPasswordReset(mail: String) =
    this.delete("$AUTH_URL/password/reset/${mail}?locale=EN").apply { assertEquals(HttpStatusCode.OK, status)  }

suspend fun HttpClient.resetPassword(token: String, newPassword: String) =
    this.put("$AUTH_URL/password/reset/${token}?password=$newPassword").apply { assertEquals(HttpStatusCode.OK, status) }
