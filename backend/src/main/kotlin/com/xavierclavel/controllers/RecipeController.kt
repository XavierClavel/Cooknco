package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getOptionalSessionId
import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.services.CooklangService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.NotificationService
import com.xavierclavel.services.RecipeIngredientService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.checkRecipeEditionRights
import com.xavierclavel.utils.getIdPathVariable
import com.xavierclavel.utils.getIdPathVariableSet
import com.xavierclavel.utils.getLocale
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.readBounded
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
import io.ktor.server.request.receiveStream
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
    val userService: UserService by inject(UserService::class.java)
    val imageService: ImageService by inject(ImageService::class.java)
    val notificationService: NotificationService by inject(NotificationService::class.java)
    val cooklangService: CooklangService by inject(CooklangService::class.java)

    override fun Route.routes() {
        getRecipe()
        listRecipes()
        authenticate("auth-session", "bearer-auth") {
            createRecipe()
            importCooklang()
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
        val user = userService.getEntityById(getSessionUserId())
        val recipeDto = call.receive<RecipeDTO>()
        // Validated before the recipe is inserted, so a rejected ingredient leaves nothing behind.
        val ingredients = recipeIngredientService.validateIngredients(recipeDto)
        recipeIngredientService.validateStepIngredients(recipeDto)
        val recipe = recipeService.createRecipe(recipeDto, user)
        recipeIngredientService.replaceRecipeIngredients(recipe.id, ingredients)
        // Last, because it is the only point at which both the steps and the ingredients
        // exist as rows able to point at each other.
        recipeIngredientService.linkStepIngredients(recipe.id, recipeDto.steps)
        val recipeInfo = recipeService.getRawById(recipe.id, getSessionUserId(), Locale.EN)
        logger.info{"Recipe ${recipeInfo.id} (${recipeInfo.title}) created by user ${user.username}"}
        // After the ingredients, so the recipe a follower is sent to is a finished one.
        // Fans out in the background: see NotificationService.
        notificationService.onRecipeCreated(recipeService.getEntityById(recipe.id))
        call.respond(HttpStatusCode.Created, recipeInfo)
    }

    /**
     * Reads a [Cooklang](https://cooklang.org) file and answers with the recipe it describes,
     * **without saving anything**.
     *
     * The response is the same [RecipeDTO] the editor would post back to create a recipe, so
     * an import ends where a scan does: filled into the editor, in front of the cook who
     * brought the file, to be checked and saved by the ordinary route. Nothing else would be
     * safe to leave open — see below — and nothing else would be honest, since a `.cook` file
     * written by another app names ingredients this catalogue may not hold.
     *
     * **Signed in is the whole gate, deliberately.** The export is what a subscription pays
     * for; getting a recipe *in* is how somebody arrives with their collection, and charging
     * for that would be charging at the door. It is also the safer half to open: this route
     * writes no row, so it is not a way to fill the database, and the body it reads is bounded
     * ([CooklangService.MAX_IMPORT_BYTES]) rather than trusted — a declared `Content-Length`
     * is a claim, and a chunked body makes none at all.
     *
     * @param locale which language to look the ingredients up in, since the file names them in
     *   whatever it was written in. EN by default, as everywhere else.
     */
    private fun Route.importCooklang() = post("/import/cooklang") {
        val source = call.receiveStream().readBounded(CooklangService.MAX_IMPORT_BYTES, BadRequestCause.COOKLANG_FILE_TOO_LARGE)
            .toString(Charsets.UTF_8)
        val locale = getEnumQueryParam<Locale>("locale") ?: Locale.EN
        val parsed = cooklangService.parse(source)
        // "There was nothing in it" is the only verdict a cook holding a file can act on, so
        // it is the only one this refuses for. Anything it could read at all comes back.
        if (parsed.isEmpty) throw BadRequestException(BadRequestCause.COOKLANG_FILE_EMPTY)
        call.respond(HttpStatusCode.OK, cooklangService.toRecipe(parsed, locale))
    }

    private fun Route.updateRecipe() = put("/{id}") {
        val recipeId = getPathId()
        val recipe = recipeService.getRawById(recipeId, getSessionUserId(), Locale.EN)
        checkRecipeEditionRights(recipe.owner.id)
        val recipeDto = call.receive<RecipeDTO>()
        // Validated before anything is written, as on create: a rejected ingredient must not
        // leave a half-edited recipe behind.
        val ingredients = recipeIngredientService.validateIngredients(recipeDto)
        recipeIngredientService.validateStepIngredients(recipeDto)
        // Steps before ingredients, and not the other way round. Replacing the steps takes
        // their ingredient links with them, which is what frees the ingredient rows to be
        // deleted - recipe_step_ingredients is ON DELETE RESTRICT at both ends, and the
        // ingredients are cleared with a bulk query-bean delete that cascades nothing. The
        // old order also linked the steps that were about to be replaced, so every link was
        // written and then immediately thrown away.
        recipeService.updateRecipe(recipeId, recipeDto)
        recipeIngredientService.replaceRecipeIngredients(recipeId, ingredients)
        recipeIngredientService.linkStepIngredients(recipeId, recipeDto.steps)
        val recipeInfo = recipeService.getRawById(recipeId, getSessionUserId(), Locale.EN)
        logger.info{"Recipe ${recipeInfo.id} (${recipeInfo.title}) edited by user ${recipe.owner.username}"}
        call.respond(HttpStatusCode.OK, recipeInfo)
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