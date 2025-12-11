package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getOptionalSessionId
import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.CustomIngredientService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.RecipeIngredientService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.checkRecipeEditionRights
import com.xavierclavel.utils.getIdPathVariable
import com.xavierclavel.utils.getIdPathVariableSet
import com.xavierclavel.utils.getLocale
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getSort
import com.xavierclavel.utils.logger
import shared.RecipeFilter
import shared.dto.RecipeDTO
import shared.enums.DishClass
import shared.enums.Locale
import shared.utils.URL.RECIPE_URL
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.inject

object RecipeController: Controller(RECIPE_URL) {
    val recipeService: RecipeService by inject(RecipeService::class.java)
    val recipeIngredientService: RecipeIngredientService by inject(RecipeIngredientService::class.java)
    val customIngredientService: CustomIngredientService by inject(CustomIngredientService::class.java)
    val userService: UserService by inject(UserService::class.java)
    val imageService: ImageService by inject(ImageService::class.java)

    override fun Route.routes() {
        getRecipe()
        listRecipes()
        authenticate("auth-session", "bearer-auth") {
            createRecipe()
            updateRecipe()
            deleteRecipe()
        }
    }

    private fun Route.getRecipe() = get("/{id}") {
        val recipeId = getPathId()
        val recipe = recipeService.getById(getOptionalSessionId(),recipeId, getLocale())
        call.respond(recipe)
    }

    private fun Route.listRecipes() = get {
        val paging = getPaging()
        val sort = getSort()
        val recipeFilter = RecipeFilter(
            user = getIdPathVariable(RecipeFilter::user.name),
            likedBy = getIdPathVariable(RecipeFilter::likedBy.name),
            cookbook = getIdPathVariable(RecipeFilter::cookbook.name),
            cookbookUser = getIdPathVariable(RecipeFilter::cookbookUser.name),
            followedBy = getIdPathVariable(RecipeFilter::followedBy.name),
            ingredient = getIdPathVariableSet(RecipeFilter::ingredient.name),
            dishClasses = call.parameters[RecipeFilter::dishClasses.name]?.split(",")?.map { DishClass.valueOf(it.trim()) }?.toSet() ?: setOf(),
            search = call.parameters[RecipeFilter::search.name],
        )

        val result = recipeService.findList(
            requestorId = getOptionalSessionId(),
            paging = paging,
            sort = sort,
            recipeFilter = recipeFilter,
        )

        call.respond(result)
    }

    private fun Route.createRecipe() = post {
        try {
            val user = userService.getEntityById(getSessionUserId())
            val recipeDto = call.receive<RecipeDTO>()
            val recipe = recipeService.createRecipe(recipeDto, user)
            recipeIngredientService.updateRecipeIngredients(recipe.id, recipeDto)
            customIngredientService.updateCustomIngredients(recipe.id, recipeDto)
            val recipeInfo = recipeService.getRawById(recipe.id, getSessionUserId(), Locale.EN)
            logger.info{"Recipe ${recipeInfo.id} (${recipeInfo.title}) created by user ${user.username}"}
            call.respond(HttpStatusCode.Created, recipeInfo)
        } catch (e: Exception) {
            logger.error {e.message}
        }

    }

    private fun Route.updateRecipe() = put("/{id}") {
        try {
            val recipeId = getPathId()
            val recipe = recipeService.getRawById(recipeId, getSessionUserId(), Locale.EN)
            checkRecipeEditionRights(recipe.owner.id)
            val recipeDto = call.receive<RecipeDTO>()
            recipeIngredientService.updateRecipeIngredients(recipe.id, recipeDto)
            customIngredientService.updateCustomIngredients(recipe.id, recipeDto)
            val recipeInfo = recipeService.updateRecipe(recipeId, recipeDto)
            logger.info{"Recipe ${recipeInfo.id} (${recipeInfo.title}) edited by user ${recipe.owner.username}"}
            call.respond(HttpStatusCode.OK, recipeInfo)
        } catch (e: Exception) {
            logger.error {e}
        }
    }

    private fun Route.deleteRecipe() = delete("/{id}") {
        val recipeId = getPathId()
        val recipe = recipeService.getRawById(recipeId, getSessionUserId(), Locale.EN)
        checkRecipeEditionRights(recipe.owner!!.id)
        recipeService.tagRecipeForDeletion(recipeId)
        recipeService.tryDelete(recipeId)
        logger.info{"Recipe ${recipe.id} (${recipe.title}) deleted by user ${recipe.owner.username}"}
        call.respond(HttpStatusCode.OK)
    }





}