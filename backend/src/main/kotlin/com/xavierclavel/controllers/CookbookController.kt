package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getOptionalSessionId
import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.controllers.UserController.imageService
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.services.CookbookService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getBooleanQueryParam
import com.xavierclavel.utils.getIdPathVariable
import com.xavierclavel.utils.getIdQueryParam
import com.xavierclavel.utils.getMandatoryIdQueryParam
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getQuery
import com.xavierclavel.utils.getSort
import com.xavierclavel.utils.handleDeletion
import com.xavierclavel.utils.logger
import shared.dto.CookbookDTO
import shared.dto.CookbookUserDTO
import shared.utils.Filepath.COOKBOOKS_IMG_PATH
import shared.utils.URL.COOKBOOK_URL
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.inject

object CookbookController: Controller(COOKBOOK_URL) {
    val cookbookService : CookbookService by inject(CookbookService::class.java)

    override fun Route.routes() {
        getCookbook()
        listCookbooks()
        isAdminOfCookbook()

        authenticate("auth-session", "bearer-auth") {
            createCookbook()

            updateCookbook()
            deleteCookbook()

            addCookbookRecipe()
            getCookbookRecipes()
            deleteCookbookRecipe()
            getRecipeStatusInUserCookbooks()

            addCookbookUser()
            getCookbookUsers()
            setCookbookUsers()
            deleteCookbookUser()
            leaveCookbook()
        }
    }

    private fun Route.createCookbook() = post {
        val cookbookDTO = call.receive<CookbookDTO>()
        val cookbook = cookbookService.createCookbook(cookbookDTO)
        val userId = getSessionUserId()
        cookbookService.addUserToCookbook(cookbook.id, userId, isAdmin = true)
        logger.info {"Cookbook ${cookbook.id} (${cookbook.title} created by user $userId"}
        call.respond(HttpStatusCode.Created, cookbook)
    }


    private fun Route.listCookbooks() = get {
        val userId = getIdQueryParam("user")
        val recipeId = getIdQueryParam("recipe")
        val search = getQuery()
        val paging = getPaging()
        val sort = getSort()
        val sessionUserId = getOptionalSessionId()
        logger.info {"current user: $sessionUserId"}
        val cookbook = cookbookService.listCookbooks(
            paging,
            sort,
            user = userId,
            recipe = recipeId,
            search = search,
            currentUser = sessionUserId
        )
        logger.info {"cookbooks: $cookbook"}
        call.respond(cookbook)
    }

    private fun Route.getRecipeStatusInUserCookbooks() = get("/recipeStatus") {
        val userId = getSessionUserId()
        val recipeId = getMandatoryIdQueryParam("recipe")
        val cookbook = cookbookService.getRecipeStatusInUserCookbooks(userId, recipeId)
        call.respond(cookbook)
    }

    private fun Route.isAdminOfCookbook() = get("/{id}/userStatus") {
        val id = getPathId()
        val userId = getOptionalSessionId() ?: return@get call.respond(false)
        call.respond(cookbookService.isAdminOfCookbook(id, userId))
    }

    private fun Route.getCookbook() = get("/{id}") {
        val id = getPathId()
        val cookbook = cookbookService.getCookbook(id, getOptionalSessionId())
        call.respond(cookbook)
    }

    private fun Route.getCookbookUsers() = get("/{id}/users") {
        val id = getPathId()
        val paging = getPaging()
        val users = cookbookService.getCookbookUsers(id, paging)
        call.respond(users)
    }

    private fun Route.getCookbookRecipes() = get("/{id}/recipes") {
        val id = getPathId()
        val paging = getPaging()
        val recipes = cookbookService.getCookbookRecipes(id, paging)
        call.respond(recipes)
    }

    private fun Route.updateCookbook() = put("/{id}") {
        val id = getPathId()
        checkIfAdminOfCookbook(id)
        val cookbookDTO = call.receive<CookbookDTO>()
        val cookbook = cookbookService.updateCookbook(id, cookbookDTO)
        call.respond(cookbook)
    }

    private fun Route.deleteCookbook() = delete("/{id}") {
        val id = getPathId()
        val cookbook = cookbookService.getEntityById(id)
        imageService.deleteImage(COOKBOOKS_IMG_PATH, id, cookbook.imageVersion)
        handleDeletion(cookbookService.deleteCookbook(id))
    }

    private fun Route.addCookbookUser() = post("/{id}/user/{user}") {
        val cookbookId = getPathId()
        checkIfAdminOfCookbook(cookbookId)
        val userId = getIdPathVariable("user") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val role = getBooleanQueryParam("role") ?: false
        cookbookService.addUserToCookbook(cookbookId, userId, role)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.setCookbookUsers() = put("/{id}/users") {
        val cookbookId = getPathId()
        checkIfAdminOfCookbook(cookbookId)
        val userInput = call.receive<List<CookbookUserDTO>>()
        cookbookService.setCookbookUsers(cookbookId, userInput)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteCookbookUser() = delete("/{id}/user/{user}") {
        val cookbookId = getPathId()
        val userId = getIdPathVariable("user") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        checkIfAdminOfCookbook(cookbookId)
        handleDeletion(cookbookService.removeUserFromCookbook(cookbookId, userId))
    }

    private fun Route.leaveCookbook() = delete("/{id}/leave") {
        val cookbookId = getPathId()
        handleDeletion(cookbookService.removeUserFromCookbook(cookbookId, getSessionUserId()))
    }

    private fun Route.addCookbookRecipe() = post("/{id}/recipe/{recipe}") {
        val cookbookId = getPathId()
        val recipeId = getIdPathVariable("recipe") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val userId = getSessionUserId()
        if (cookbookService.doesCookbookHaveRecipe(cookbookId, recipeId)) throw BadRequestException(BadRequestCause.RECIPE_ALREADY_IN_COOKBOOK)
        if (!cookbookService.isMemberOfCookbook(cookbookId, userId)) throw ForbiddenException(ForbiddenCause.NOT_MEMBER_OF_COOKBOOK)
        cookbookService.addRecipeToCookbook(cookbookId, recipeId, userId)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteCookbookRecipe() = delete("/{id}/recipe/{recipe}") {
        val cookbookId = getPathId()
        val recipeId = getIdPathVariable("recipe") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val userId = getSessionUserId()
        if (!cookbookService.isMemberOfCookbook(cookbookId, userId)) throw ForbiddenException(ForbiddenCause.NOT_MEMBER_OF_COOKBOOK)
        if (!cookbookService.isAdminOfCookbook(cookbookId, userId) && cookbookService.getCookbookRecipeAdder(cookbookId, recipeId) != userId) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_REMOVE_RECIPE)
        }
        handleDeletion(cookbookService.removeRecipeFromCookbook(cookbookId, recipeId))
    }

    private suspend fun RoutingContext.checkIfAdminOfCookbook(cookbookId: Long) {
        if (!cookbookService.isAdminOfCookbook(cookbookId, getSessionUserId())) {
            throw ForbiddenException(ForbiddenCause.MUST_BE_COOKBOOK_ADMINISTRATOR)
        }
    }

}