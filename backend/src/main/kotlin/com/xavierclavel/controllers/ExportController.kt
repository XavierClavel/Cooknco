package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.models.User
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.ExportService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.respondPDF
import shared.enums.Locale
import shared.enums.UnitSystem
import shared.enums.UserRole
import shared.utils.URL.EXPORT_URL
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

/**
 * PDF exports: one recipe as a sheet, one cookbook as a whole book of them.
 *
 * The first feature a subscription pays for. Any signed-in account reaches the routes and
 * [UserService.checkPremiumAccess] decides, which is why the gate is a 403 with a cause
 * rather than a 401: a client that is signed in and refused needs to be told to subscribe,
 * not to log in again.
 *
 * Opening it past the admins changes what an export may *contain*, and that is the part to
 * keep. An export used to read its subject straight from an id, with none of the visibility
 * filtering `getById` applies, because only moderators could ask — the same read now goes
 * through the ordinary rules for everyone else, or a subscription would buy the
 * moderator-hidden recipes, the ones private profiles keep, and every recipe of a cookbook
 * nobody outside it can open. An ADMIN still prints the subject as it stands: what the
 * backoffice previews and what a moderator looks into are the whole of it, hidden rows
 * included.
 *
 * Two ways in, one gate. `auth-session` is the cookie the web app authenticates with;
 * `bearer-auth` is the same session id presented as a bearer token, which is all a native
 * client can send — both resolve the session in Redis. The cookie provider is named first
 * so that a caller with neither is refused by the challenge the web app expects.
 */
object ExportController: Controller(EXPORT_URL) {
    val exportService : ExportService by inject(ExportService::class.java)
    val recipeService : RecipeService by inject(RecipeService::class.java)
    val cookbookService : CookbookService by inject(CookbookService::class.java)
    val userService : UserService by inject(UserService::class.java)

    override fun Route.routes() {
        authenticate("auth-session", "bearer-auth") {
            exportRecipe()
            exportCookbook()
        }
    }

    /**
     * @param locale which language to name the ingredients and the headings in; EN by default,
     *   so that pasting the URL into a browser returns a sheet rather than a 400
     * @param unitSystem which units to print the amounts in; metric by default, for the same
     *   reason. Asked for rather than read off the caller's account: the sheet is printed to
     *   be handed to somebody, and who that is only the caller knows
     */
    private fun Route.exportRecipe() = get("/recipe/{id}") {
        val caller = premiumCaller()
        val id = getPathId()
        val locale = getEnumQueryParam<Locale>("locale") ?: Locale.EN
        val unitSystem = getEnumQueryParam<UnitSystem>("unitSystem") ?: UnitSystem.DEFAULT
        val recipe =
            if (caller.role == UserRole.ADMIN) recipeService.getEntityById(id).toInfo(locale)
            else recipeService.getById(caller.id, id, locale)
        call.respondPDF(exportService.filenameOf(recipe), exportService.generatePDF(recipe, locale, unitSystem))
    }

    /**
     * The cookbook printed as one book, cover and every recipe in it.
     *
     * Same two parameters as the sheet, read the same way and for the same reasons. A
     * cookbook past `Configuration.Pdf.maxCookbookRecipes` is refused with a 400 rather
     * than printed short — see that field.
     *
     * The book is filtered twice over for a subscriber: the cookbook itself has to be one
     * they can open, and so does each recipe in it. A book they may read that holds one
     * recipe they may not prints without it, rather than being refused — the alternative
     * is a paid feature that fails on somebody else's moderation.
     */
    private fun Route.exportCookbook() = get("/cookbook/{id}") {
        val caller = premiumCaller()
        val id = getPathId()
        val locale = getEnumQueryParam<Locale>("locale") ?: Locale.EN
        val unitSystem = getEnumQueryParam<UnitSystem>("unitSystem") ?: UnitSystem.DEFAULT
        val isModerator = caller.role == UserRole.ADMIN
        val cookbook =
            if (isModerator) cookbookService.getEntityById(id).toInfo()
            else cookbookService.getCookbook(id, caller.id)
        call.respondPDF(
            exportService.filenameOf(cookbook),
            exportService.generateCookbookPDF(
                cookbook = cookbook,
                locale = locale,
                unitSystem = unitSystem,
                visibleTo = if (isModerator) null else caller.id,
            ),
        )
    }

    /** The account asking, once it has been found to be allowed one. */
    private suspend fun RoutingContext.premiumCaller(): User =
        userService.checkPremiumAccess(getSessionUserId())
}
