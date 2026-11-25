package com.xavierclavel.controllers

import com.xavierclavel.services.CiqualService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getQuery
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

/**
 * The Ciqual table, for the backoffice to pick the food an ingredient's nutrition is copied
 * from. A read of the bundled file and nothing else: the link itself is made by saving the
 * ingredient through [IngredientController] with a `ciqualCode`.
 */
object AdminCiqualController: Controller("ciqual") {
    val ciqualService: CiqualService by inject(CiqualService::class.java)

    override fun Route.routes() {
        search()
    }

    /**
     * @param query words each name must hold one starting with, in French or English
     * @param limit how many to return; 20 unless asked, never more than [CiqualService.MAX_RESULTS]
     */
    private fun Route.search() = get {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        call.respond(ciqualService.search(getQuery(), limit))
    }
}
