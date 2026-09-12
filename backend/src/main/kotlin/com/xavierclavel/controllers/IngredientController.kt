package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.IngredientService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getLocale
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getQuery
import com.xavierclavel.utils.json
import com.xavierclavel.utils.logger
import shared.dto.AbsorbCustomIngredientDTO
import shared.dto.AbsorbCustomIngredientResult
import shared.dto.IngredientDTO
import shared.dto.SearchResult
import shared.infodto.CustomIngredientUsage
import shared.infodto.IngredientInfo
import shared.utils.URL.INGREDIENT_URL
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

object IngredientController: Controller(INGREDIENT_URL) {
    val ingredientService : IngredientService by inject(IngredientService::class.java)

    override fun Route.routes() {
        getIngredient()
        searchIngredients()
        getCount()
        getRecipesCount()

        authenticate("admin-session") {
            createIngredient()
            createIngredientsBatch()
            updateIngredient()
            deleteIngredient()
            listCustomIngredientUsage()
            absorbCustomIngredient()
        }
    }

    /** Which free-text ingredient names users type most, i.e. what to add to the catalog next. */
    private fun Route.listCustomIngredientUsage() = get("/custom-usage") {
        val paging = getPaging()
        val usage = ingredientService.findCustomIngredientUsage(paging)
        val result = SearchResult(usage.first, paging.pageIndex(), paging.pageSize(), usage.second)
        call.respond(json.encodeToString(SearchResult.serializer(CustomIngredientUsage.serializer()), result))
    }

    /** Re-points recipe rows that used a free-text name at this ingredient. */
    private fun Route.absorbCustomIngredient() = post("/{id}/absorb-custom") {
        val id = getPathId()
        val dto = call.receive<AbsorbCustomIngredientDTO>()
        val result = ingredientService.absorbCustomIngredient(id, dto.name)
        logger.info {
            "Absorbed ${result.convertedRows} custom ingredient rows named '${dto.name}' into ingredient $id" +
                ", skipped ${result.skippedRows} whose unit it does not allow"
        }
        call.respond(result)
    }

    private fun Route.createIngredient() = post {
        val adminId = getSessionUserId()
        val ingredientDTO = call.receive<IngredientDTO>()
        val ingredient = ingredientService.createIngredient(ingredientDTO)
        logger.info { "Ingredient ${ingredient.id} created by admin $adminId" }
        call.respond(HttpStatusCode.Created, ingredient)
    }

    private fun Route.createIngredientsBatch() = post("/batch") {
        val adminId = getSessionUserId()
        val ingredientsDTO = call.receive<List<IngredientDTO>>()
        for (ingredient in ingredientsDTO) {
            ingredientService.createIngredient(ingredient)
        }
        logger.info { "${ingredientsDTO.size} ingredient(s) created in one batch by admin $adminId" }
        call.respond(HttpStatusCode.Created)
    }

    private fun Route.updateIngredient() = put("/{id}") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val ingredientDTO = call.receive<IngredientDTO>()
        val ingredient = ingredientService.updateIngredient(id, ingredientDTO)
        logger.info { "Ingredient $id edited by admin $adminId" }
        call.respond(ingredient)
    }

    private fun Route.deleteIngredient() = delete("/{id}") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val result = ingredientService.deleteById(id)
        if (result) logger.info { "Ingredient $id deleted by admin $adminId" }
        return@delete if (result) call.respond(HttpStatusCode.OK)
            else call.respond(HttpStatusCode.NotFound)
    }

    private fun Route.searchIngredients() = get {
        val searchString = getQuery()
        val paging = getPaging()
        val ingredients = ingredientService.search(searchString, paging, getLocale())
        val result = SearchResult(ingredients.first, paging.pageIndex(), paging.pageSize(), ingredients.second)
        call.respond(json.encodeToString(SearchResult.serializer(IngredientInfo.serializer()), result))
    }

    private fun Route.getIngredient() = get("/{id}") {
        val id = getPathId()
        val result = ingredientService.findById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(result)
    }

    private fun Route.getCount() = get("/count") {
        call.respond(ingredientService.countAll())
    }

    private fun Route.getRecipesCount() = get("/count/recipes/{id}") {
        call.respond(ingredientService.countRecipes(getPathId()))
    }

}