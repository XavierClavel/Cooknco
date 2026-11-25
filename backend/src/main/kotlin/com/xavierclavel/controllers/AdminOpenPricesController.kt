package com.xavierclavel.controllers

import com.xavierclavel.services.OpenPricesService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getQuery
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

/**
 * The Open Prices table, for the backoffice to pick the category an ingredient is priced as. A
 * read of the bundled file and nothing else: the link is made by saving the ingredient through
 * [IngredientController] with an `openPricesCategory`.
 */
object AdminOpenPricesController: Controller("open-prices") {
    val openPricesService: OpenPricesService by inject(OpenPricesService::class.java)

    override fun Route.routes() {
        search()
    }

    /**
     * @param query words each name must hold one starting with, in French or English
     * @param limit how many to return; 20 unless asked, never more than [OpenPricesService.MAX_RESULTS]
     */
    private fun Route.search() = get {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
        call.respond(openPricesService.search(getQuery(), limit))
    }
}
