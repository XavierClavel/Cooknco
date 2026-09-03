package com.xavierclavel.controllers

import com.xavierclavel.services.AdminService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.utils.json
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject
import shared.dto.SearchResult
import shared.enums.IngredientType
import shared.infodto.AdminIngredientInfo

/**
 * Ingredient catalogue browsing for the backoffice. Writes reuse the existing admin-gated
 * endpoints on [IngredientController]; this adds the substring search across every locale
 * and the per-ingredient usage counts a catalogue table needs.
 */
object AdminIngredientController: Controller("ingredients") {
    val adminService: AdminService by inject(AdminService::class.java)

    override fun Route.routes() {
        searchIngredients()
    }

    private fun Route.searchIngredients() = get {
        val paging = getPaging()
        val (count, ingredients) = adminService.searchIngredients(
            query = getStringQueryParam("query"),
            type = getEnumQueryParam<IngredientType>("type"),
            paging = paging,
        )
        val result = SearchResult(count, paging.pageIndex(), paging.pageSize(), ingredients)
        call.respond(json.encodeToString(SearchResult.serializer(AdminIngredientInfo.serializer()), result))
    }
}
