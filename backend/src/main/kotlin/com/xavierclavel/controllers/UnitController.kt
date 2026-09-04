package com.xavierclavel.controllers

import com.xavierclavel.utils.Controller
import shared.infodto.UnitInfo
import shared.utils.URL.UNIT_URL
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Serves the unit catalog so clients don't duplicate the measurement families or the conversion
 * factors. The response only changes when the backend is redeployed, hence the long cache window.
 */
object UnitController: Controller(UNIT_URL) {
    override fun Route.routes() {
        listUnits()
    }

    private fun Route.listUnits() = get {
        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
        call.respond(UnitInfo.all())
    }
}
