package com.xavierclavel.controllers

import com.xavierclavel.services.AppVersionService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject
import shared.enums.AppPlatform
import shared.utils.URL.APP_VERSION_URL

/**
 * What a mobile build asks before it lets anyone in.
 *
 * Unauthenticated, like [HealthController] and for the same kind of reason: this is asked
 * at launch, before a session exists, and the builds it exists to catch are the ones whose
 * sign-in we can least count on. Nothing here is worth hiding either — the answer is the
 * store listing and two version numbers that are public the moment they ship.
 */
object AppVersionController: Controller(APP_VERSION_URL) {
    val appVersionService: AppVersionService by inject(AppVersionService::class.java)

    override fun Route.routes() {
        checkVersion()
    }

    /**
     * @param platform ANDROID or IOS, required — a client that cannot name itself is a bug,
     *   not a build to answer for
     * @param version how the build names itself; absent is treated as unknown, which is OK
     */
    private fun Route.checkVersion() = get {
        val platform = getEnumQueryParam<AppPlatform>("platform")
            ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        call.respond(appVersionService.check(platform, getStringQueryParam("version") ?: ""))
    }
}
