package com.xavierclavel.controllers

import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.respondPDF
import shared.enums.Locale
import shared.enums.UnitSystem
import shared.utils.URL.EXPORT_URL
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

/**
 * PDF exports: one recipe as a sheet, one cookbook as a whole book of them.
 *
 * Admin-only, which is what the web app already assumes — it offers both buttons inside
 * `<admin-only>`. The gate matters on this side too: an export reads its subject straight
 * from an id, with none of the visibility filtering `getById` applies, so opening it to
 * every account would hand out moderator-hidden recipes, the ones private profiles keep,
 * and every recipe of a cookbook nobody outside it can open.
 *
 * `admin-session` is cookie-based (see `configureAuthentication`), which is what the web app
 * authenticates with; the native app has no export.
 */
object ExportController: Controller(EXPORT_URL) {
    val exportService : ExportService by inject(ExportService::class.java)
    val recipeService : RecipeService by inject(RecipeService::class.java)
    val cookbookService : CookbookService by inject(CookbookService::class.java)

    override fun Route.routes() {
        authenticate("admin-session") {
            exportRecipe()
            exportCookbook()
        }
    }

    /**
     * @param locale which language to name the ingredients and the headings in; EN by default,
     *   so that pasting the URL into a browser returns a sheet rather than a 400
     * @param unitSystem which units to print the amounts in; metric by default, for the same
     *   reason. Asked for rather than read off the admin's account: the sheet is printed to
     *   be handed to somebody, and who that is only the caller knows
     */
    private fun Route.exportRecipe() = get("/recipe/{id}") {
        val id = getPathId()
        val locale = getEnumQueryParam<Locale>("locale") ?: Locale.EN
        val unitSystem = getEnumQueryParam<UnitSystem>("unitSystem") ?: UnitSystem.DEFAULT
        val recipe = recipeService.getEntityById(id).toInfo(locale)
        call.respondPDF(exportService.filenameOf(recipe), exportService.generatePDF(recipe, locale, unitSystem))
    }

    /**
     * The cookbook printed as one book, cover and every recipe in it.
     *
     * Same two parameters as the sheet, read the same way and for the same reasons. A
     * cookbook past `Configuration.Pdf.maxCookbookRecipes` is refused with a 400 rather
     * than printed short — see that field.
     */
    private fun Route.exportCookbook() = get("/cookbook/{id}") {
        val id = getPathId()
        val locale = getEnumQueryParam<Locale>("locale") ?: Locale.EN
        val unitSystem = getEnumQueryParam<UnitSystem>("unitSystem") ?: UnitSystem.DEFAULT
        val cookbook = cookbookService.getEntityById(id).toInfo()
        call.respondPDF(
            exportService.filenameOf(cookbook),
            exportService.generateCookbookPDF(cookbook, locale, unitSystem),
        )
    }
}
