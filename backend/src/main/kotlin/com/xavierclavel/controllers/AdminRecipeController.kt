package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.AdminService
import com.xavierclavel.services.ModerationService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.enumValueOfIgnoreCase
import com.xavierclavel.utils.getBooleanQueryParam
import com.xavierclavel.utils.getIdQueryParam
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getSort
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.utils.json
import com.xavierclavel.utils.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject
import shared.dto.ModerationReasonDTO
import shared.dto.SearchResult
import shared.enums.Locale
import shared.infodto.AdminRecipeInfo

/**
 * Recipe management. Distinct from [RecipeController] in that nothing here is gated on
 * ownership or on the visibility rules that hide moderated content.
 */
object AdminRecipeController: Controller("recipes") {
    val adminService: AdminService by inject(AdminService::class.java)
    val moderationService: ModerationService by inject(ModerationService::class.java)
    val recipeService: RecipeService by inject(RecipeService::class.java)

    override fun Route.routes() {
        searchRecipes()
        getRecipe()
        getRecipeDetail()
        hideRecipe()
        unhideRecipe()
        deleteRecipe()
    }

    private fun Route.searchRecipes() = get {
        val paging = getPaging()
        val (count, recipes) = adminService.searchRecipes(
            query = getStringQueryParam("query"),
            ownerId = getIdQueryParam("owner"),
            hidden = getBooleanQueryParam("hidden"),
            reportedOnly = getBooleanQueryParam("reported") == true,
            sort = getSort(),
            paging = paging,
        )
        val result = SearchResult(count, paging.pageIndex(), paging.pageSize(), recipes)
        call.respond(json.encodeToString(SearchResult.serializer(AdminRecipeInfo.serializer()), result))
    }

    private fun Route.getRecipe() = get("/{id}") {
        call.respond(adminService.getRecipe(getPathId()))
    }

    /** Full recipe content, so a moderator can read what was reported before deciding. */
    private fun Route.getRecipeDetail() = get("/{id}/detail") {
        val locale = getStringQueryParam("locale")
            ?.let { enumValueOfIgnoreCase<Locale>(it) }
            ?: Locale.EN
        call.respond(recipeService.getEntityById(getPathId()).toInfo(locale))
    }

    private fun Route.hideRecipe() = post("/{id}/hide") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val dto = call.receive<ModerationReasonDTO>()
        val recipe = moderationService.hideRecipe(id, dto.reason)
        logger.info { "Recipe $id hidden by admin $adminId (reason: ${dto.reason})" }
        call.respond(recipe)
    }

    private fun Route.unhideRecipe() = post("/{id}/unhide") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val recipe = moderationService.unhideRecipe(id)
        logger.info { "Recipe $id unhidden by admin $adminId" }
        call.respond(recipe)
    }

    private fun Route.deleteRecipe() = delete("/{id}") {
        val id = getPathId()
        val adminId = getSessionUserId()
        moderationService.deleteRecipe(id)
        logger.info { "Recipe $id deleted by admin $adminId" }
        call.respond(HttpStatusCode.OK)
    }
}
