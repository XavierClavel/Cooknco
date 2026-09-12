package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.services.RecipeNotesService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.logger
import shared.utils.URL.RECIPE_NOTES_URL
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.inject

object RecipeNotesController: Controller(RECIPE_NOTES_URL) {
    val recipeNotesService: RecipeNotesService by inject(RecipeNotesService::class.java)

    override fun Route.routes() {
        getRecipeNotes()
        createRecipeNotes()
        updateRecipeNotes()
        deleteRecipeNotes()
    }

    private fun Route.getRecipeNotes() = get("/{id}") {
        val recipeId = getPathId()
        val userId = getSessionUserId()
        val recipeNotes = recipeNotesService.findNotes(recipeId, userId)
        call.respond(recipeNotes.notes)
    }

    private fun Route.createRecipeNotes() = post("/{id}") {
        val recipeId = getPathId()
        val userId = getSessionUserId()
        val notes = call.receive<String>()
        if (recipeNotesService.notesExists(recipeId, userId)) throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val recipeNotes = recipeNotesService.createNotes(recipeId, userId, notes)
        logger.info { "Notes created on recipe $recipeId by user $userId" }
        call.respond(recipeNotes)
    }

    private fun Route.updateRecipeNotes() = put("/{id}") {
        val recipeId = getPathId()
        val userId = getSessionUserId()
        val notes = call.receive<String>()
        val recipeNotes = recipeNotesService.updateNotes(recipeId, userId, notes)
        logger.info { "Notes edited on recipe $recipeId by user $userId" }
        call.respond(recipeNotes)
    }

    private fun Route.deleteRecipeNotes() = delete("/{id}") {
        val recipeId = getPathId()
        val userId = getSessionUserId()
        val recipeNotes = recipeNotesService.deleteNotes(recipeId, userId)
        logger.info { "Notes deleted on recipe $recipeId by user $userId" }
        call.respond(recipeNotes)
    }

}