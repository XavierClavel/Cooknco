package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.AppVersionService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumPathParam
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.utils.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.inject
import shared.dto.AppVersionDTO
import shared.enums.AppPlatform

/**
 * The mobile version gates, for the backoffice releases tab.
 *
 * Shaped like [AdminPdfController]: the set of rows is fixed by the set of platforms code
 * knows about, so what an operator does is edit one or take it away. Taking it away is what
 * "no gate" means — see [AppVersionService].
 */
object AdminAppVersionController: Controller("app-versions") {
    val appVersionService: AppVersionService by inject(AppVersionService::class.java)

    override fun Route.routes() {
        listGates()
        getReach()
        saveGate()
        clearGate()
    }

    /** Every platform, with its gate or the absence of one. */
    private fun Route.listGates() = get {
        call.respond(appVersionService.describe())
    }

    /**
     * What a floor would cost, on the install base as it stands.
     *
     * Read before the save rather than after, exactly as the notifications tab reads its
     * audience: a version gate is applied by apps that have stopped asking anything else,
     * so the size of it is only actionable beforehand.
     *
     * @param minimum the candidate floor; defaults to the one already in force, which is
     *   what makes opening the tab show what today's gate is blocking
     */
    private fun Route.getReach() = get("/{platform}/reach") {
        call.respond(
            appVersionService.reach(
                platform = getEnumPathParam<AppPlatform>("platform"),
                candidateMinimum = getStringQueryParam("minimum"),
            )
        )
    }

    private fun Route.saveGate() = put("/{platform}") {
        val platform = getEnumPathParam<AppPlatform>("platform")
        val adminId = getSessionUserId()
        val dto = call.receive<AppVersionDTO>()
        val saved = appVersionService.save(platform = platform, dto = dto)
        logger.info {
            "$platform version gate set by admin $adminId to a minimum of ${dto.minimumVersion} " +
                "(latest ${dto.latestVersion})"
        }
        call.respond(saved)
    }

    /** Removes the gate, which puts every build of that platform back in service. */
    private fun Route.clearGate() = delete("/{platform}") {
        val platform = getEnumPathParam<AppPlatform>("platform")
        val adminId = getSessionUserId()
        if (!appVersionService.clear(platform)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info { "$platform version gate cleared by admin $adminId: every build is back in service" }
            call.respond(appVersionService.describe(platform))
        }
    }
}
