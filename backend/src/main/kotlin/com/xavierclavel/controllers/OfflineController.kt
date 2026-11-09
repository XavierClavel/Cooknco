package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.OfflineSyncService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getLocale
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject
import shared.dto.OfflineSyncDTO
import shared.utils.URL.OFFLINE_URL

/**
 * What the app keeps on the phone so that a cook's recipes open with no signal.
 *
 * A POST because the request carries what the phone holds — up to six hundred ids — which is
 * no query string. It writes nothing, so it logs no edit.
 */
object OfflineController : Controller(OFFLINE_URL) {
    private val offlineSyncService: OfflineSyncService by inject(OfflineSyncService::class.java)

    override fun Route.routes() {
        sync()
    }

    /**
     * See [OfflineSyncService.sync].
     *
     * @param locale which language the catalogue ingredients are named in, as on `GET /recipe/{id}`.
     */
    private fun Route.sync() = post("/sync") {
        val request = call.receive<OfflineSyncDTO>()
        call.respond(offlineSyncService.sync(getSessionUserId(), request, getLocale()))
    }
}
