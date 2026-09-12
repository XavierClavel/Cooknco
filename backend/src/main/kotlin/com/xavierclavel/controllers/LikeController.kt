package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.controllers.RecipeController.recipeService
import com.xavierclavel.services.LikeService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getIdPathVariable
import com.xavierclavel.utils.logger
import shared.utils.URL.LIKE_URL
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject

object LikeController: Controller(LIKE_URL) {
    val likeService : LikeService by inject(LikeService::class.java)

    override fun Route.routes() {
        getLikes()
        isLiked()
        countLikes()
        createLike()
        deleteLike()
    }

    private fun Route.getLikes() = get {
        val userId = getIdPathVariable("user")
        val recipeId = getIdPathVariable("recipe")
        val paging = getPaging()
        call.respond(likeService.find(recipeId, userId, paging))
    }

    private fun Route.isLiked() = get("/{id}") {
        val recipeId = getIdPathVariable("id")
        val userId = getSessionUserId()
        call.respond(likeService.exists(recipeId, userId))
    }

    private fun Route.countLikes() = get("/count") {
        val userId = getIdPathVariable("user")
        val recipeId = getIdPathVariable("recipe")
        call.respond(likeService.count(recipeId, userId))
    }

    private fun Route.createLike() = post("/{id}") {
        val recipeId = getPathId()
        val userId = getSessionUserId()
        val userCreated = likeService.createLike(recipeId, userId)
        logger.info { "Recipe $recipeId liked by user $userId" }
        call.respond(HttpStatusCode.Created, userCreated)
    }

    private fun Route.deleteLike() = delete("/{id}") {
        val recipeId = getPathId()
        val userId = getSessionUserId()
        val result = likeService.deleteLike(recipeId, userId) ?: return@delete call.respond(HttpStatusCode.BadRequest)
        if (!result) return@delete call.respond(HttpStatusCode.BadRequest)
        logger.info { "Like on recipe $recipeId taken back by user $userId" }
        // Purging is only for a recipe whose owner already asked for deletion and which was
        // kept alive by what still referenced it. tryDelete does not check the tag itself, so
        // calling it unconditionally erased a live recipe the moment its last like was taken
        // back — see the same check in CookncoMcpServer's like_recipe.
        if (recipeService.getEntityById(recipeId).taggedForDeletion) {
            recipeService.tryDelete(recipeId)
        }
        call.respond(HttpStatusCode.OK)
    }

}