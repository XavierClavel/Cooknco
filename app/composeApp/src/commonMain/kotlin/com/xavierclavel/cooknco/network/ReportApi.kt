package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.ReportDto
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * What kind of thing is being reported — the backend's `ReportTargetType`.
 *
 * COOKBOOK exists on the server and is deliberately absent here: a cookbook is only
 * visible to its members, so the screen that would carry the action is one you were
 * invited to, and leaving it is the answer the app already offers.
 */
enum class ReportTargetType(val value: String) {
    RECIPE("RECIPE"),
    USER("USER"),
}

/**
 * Why something is being reported — the backend's `ReportReason`, in the order the sheet
 * offers them.
 *
 * Declared here rather than derived from a server list: the moderation queue groups on
 * these, so a reason the backend does not know is not a reason a user should be able to
 * pick. [com.xavierclavel.cooknco.ui.i18n.Strings.reportReasonName] is what names them.
 */
enum class ReportReason(val value: String) {
    INAPPROPRIATE_CONTENT("INAPPROPRIATE_CONTENT"),
    SPAM("SPAM"),
    HARASSMENT("HARASSMENT"),
    COPYRIGHT("COPYRIGHT"),
    MISINFORMATION("MISINFORMATION"),
    OTHER("OTHER"),
}

class ReportApi(private val client: HttpClient) {

    private val base = ApiClient.BASE_URL

    /**
     * `POST /report`. The backend answers with the report it filed; nothing in the app
     * reads it back — a report is only ever seen again in the backoffice — so this returns
     * nothing and the body is left on the floor.
     */
    suspend fun createReport(token: String, report: ReportDto) {
        val response = client.post("$base/report") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(report)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
    }
}
