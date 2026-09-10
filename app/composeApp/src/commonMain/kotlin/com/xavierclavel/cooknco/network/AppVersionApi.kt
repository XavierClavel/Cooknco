package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.network.dto.AppVersionCheckInfo
import com.xavierclavel.cooknco.platform.appVersion
import com.xavierclavel.cooknco.platform.devicePlatform
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class AppVersionApi(private val client: HttpClient) {

    /**
     * Asks whether this build is still allowed to run.
     *
     * No bearer token, and none to send: this is the first call of the launch, before the
     * stored session has been restored, and the build most likely to be blocked is the one
     * whose sign-in we can least count on.
     *
     * [devicePlatform] is reused rather than a second constant of its own — it is already
     * the string the backend names this client by, and one of them being wrong is easier
     * to notice than two of them disagreeing. The backend only knows ANDROID and IOS here,
     * so a platform it does not gate answers 400, which the repository treats as "no gate".
     */
    suspend fun check(version: String = appVersion): AppVersionCheckInfo {
        val response = client.get("${ApiClient.BASE_URL}/app-version") {
            parameter("platform", devicePlatform)
            parameter("version", version)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status, response.bodyAsText())
        return response.body()
    }
}
