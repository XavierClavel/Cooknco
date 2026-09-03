package com.xavierclavel.controllers

import com.xavierclavel.services.AdminService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject
import shared.enums.TimeGranularity
import shared.utils.URL.ADMIN_URL

/**
 * Root of the admin backoffice API.
 *
 * Everything below it sits behind `admin-session`, so authorisation is declared once here
 * rather than repeated per sub-controller. `admin-session` is cookie-based (see
 * `configureAuthentication`), which is what the backoffice frontend uses.
 */
object AdminController: Controller(ADMIN_URL) {
    val adminService: AdminService by inject(AdminService::class.java)

    override fun Route.routes() {
        authenticate("admin-session") {
            getOverview()
            getTrends()
            AdminUserController.serve(this)
            AdminRecipeController.serve(this)
            AdminIngredientController.serve(this)
            AdminReportController.serve(this)
            AdminStorageController.serve(this)
            AdminLogController.serve(this)
        }
    }

    private fun Route.getOverview() = get("/overview") {
        call.respond(adminService.buildOverview())
    }

    /**
     * Activity per time bucket for the dashboard charts.
     *
     * @param granularity DAY, WEEK or MONTH; defaults to WEEK
     * @param buckets how many buckets to return; defaults to the granularity's own
     */
    private fun Route.getTrends() = get("/trends") {
        val granularity = getEnumQueryParam<TimeGranularity>("granularity") ?: TimeGranularity.WEEK
        val buckets = call.request.queryParameters["buckets"]?.toIntOrNull() ?: granularity.defaultBuckets
        call.respond(adminService.buildTrends(granularity, buckets))
    }
}
