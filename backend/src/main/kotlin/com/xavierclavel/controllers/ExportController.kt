package com.xavierclavel.controllers

import com.xavierclavel.services.ExportService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.respondPDF
import shared.enums.Locale
import shared.utils.URL.EXPORT_URL
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

/**
 * PDF exports.
 *
 * Admin-only, which is what the web app already assumes — it offers the button inside
 * `<admin-only>`. The gate matters on this side too: the export reads a recipe straight
 * from its id, with none of the visibility filtering `getById` applies, so opening it to
 * every account would hand out moderator-hidden recipes and the ones private profiles keep.
 *
 * `admin-session` is cookie-based (see `configureAuthentication`), which is what the web app
 * authenticates with; the native app has no export.
 */
object ExportController: Controller(EXPORT_URL) {
    val exportService : ExportService by inject(ExportService::class.java)
    val recipeService : RecipeService by inject(RecipeService::class.java)

    override fun Route.routes() {
        authenticate("admin-session") {
            exportRecipe()
        }
    }

    /**
     * @param locale which language to name the ingredients and the headings in; EN by default,
     *   so that pasting the URL into a browser returns a sheet rather than a 400
     */
    private fun Route.exportRecipe() = get("/recipe/{id}") {
        val id = getPathId()
        val locale = getEnumQueryParam<Locale>("locale") ?: Locale.EN
        val recipe = recipeService.getEntityById(id).toInfo(locale)
        call.respondPDF(exportService.filenameOf(recipe), exportService.generatePDF(recipe, locale))
    }
}
