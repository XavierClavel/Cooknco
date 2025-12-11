package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.checkRecipeEditionRights
import com.xavierclavel.utils.checkUserEditionRights
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.receiveImage
import shared.utils.Filepath.COOKBOOKS_IMG_PATH
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import shared.utils.Filepath.USERS_IMG_PATH
import shared.utils.URL.IMAGE_URL
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject
import java.io.File

object ImageController: Controller(IMAGE_URL) {
    val imageService : ImageService by inject(ImageService::class.java)
    val recipeService: RecipeService by inject(RecipeService::class.java)
    val cookbookService: CookbookService by inject(CookbookService::class.java)
    val userService: UserService by inject(UserService::class.java)

    /*
        Recipe full display : 800x600
        Recipe icon display : 240x180
        User icon display : 200x200
        Cookbook icon display : 250x250
     */
    val SIZE_IMG_USER = Pair(400,400)
    val SIZE_IMG_RECIPE = Pair(1600,1200)
    val SIZE_THUMBNAIL_RECIPE = Pair(480,360)
    val SIZE_IMG_COOKBOOK = Pair(500,500)

    override fun Route.routes() {
        //Serve static images for recipes
        staticFiles("/recipes", File(RECIPES_IMG_PATH)) {
            default("default.webp")
            modify { file, call ->
                call.response.headers.append(HttpHeaders.ETag, file.lastModified().toString())
            }
        }

        //Serve static images for recipes
        staticFiles("/recipes-thumbnails", File(RECIPES_THUMBNAIL_PATH)) {
            default("default.webp")
            modify { file, call ->
                call.response.headers.append(HttpHeaders.ETag, file.lastModified().toString())
            }
        }

        //Serve static images for users icon
        staticFiles("/users", File(USERS_IMG_PATH)) {
            default("default.webp")
            modify { file, call ->
                call.response.headers.append(HttpHeaders.ETag, file.lastModified().toString())
            }
        }

        //Serve static images for users icon
        staticFiles("/cookbooks", File(COOKBOOKS_IMG_PATH)) {
            default("default.webp")
            modify { file, call ->
                call.response.headers.append(HttpHeaders.ETag, file.lastModified().toString())
            }
        }

        authenticate("auth-session", "bearer-auth") {
            uploadRecipeImage()
            uploadCookbookImage()
            uploadUserIcon()
            deleteRecipeImage()
            deleteCookbookImage()
            deleteUserImage()
        }
    }


    private fun Route.uploadRecipeImage() = post("/recipes/{id}") {
        val id = getPathId()
        checkRecipeEditionRights(recipeService.getRecipeOwner(id).id)
        val (image, metadata) = receiveImage()
        val recipe = recipeService.getEntityById(id)

        imageService.saveImage(RECIPES_IMG_PATH, id, recipe.imageVersion + 1, SIZE_IMG_RECIPE, image, metadata)
        imageService.saveImage(RECIPES_THUMBNAIL_PATH, id, recipe.imageVersion + 1, SIZE_THUMBNAIL_RECIPE, image, metadata)

        recipe.increaseVersion()
        imageService.deleteImage(RECIPES_IMG_PATH, id, recipe.imageVersion - 1)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.uploadCookbookImage() = post("/cookbooks/{id}") {
        val id = getPathId()
        val (image, metadata) = receiveImage()
        val cookbook = cookbookService.getEntityById(id)
        imageService.saveImage(COOKBOOKS_IMG_PATH, id, cookbook.imageVersion + 1, SIZE_IMG_COOKBOOK, image, metadata)
        cookbook.increaseVersion()
        imageService.deleteImage(COOKBOOKS_IMG_PATH, id, cookbook.imageVersion - 1)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.uploadUserIcon() = post("/users/{id}") {
        val id = getPathId()
        checkUserEditionRights(id)
        val (image, metadata) = receiveImage()
        val user = userService.getEntityById(id)
        imageService.saveImage(USERS_IMG_PATH, id, user.imageVersion + 1, SIZE_IMG_USER, image, metadata)
        user.increaseVersion()
        imageService.deleteImage(USERS_IMG_PATH, id, user.imageVersion - 1)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteRecipeImage() = delete("/recipes/{id}") {
        val id = getPathId()
        val recipe = recipeService.getEntityById(id)
        checkRecipeEditionRights(recipeService.getRecipeOwner(id).id)
        imageService.deleteImage(RECIPES_IMG_PATH, id, recipe.imageVersion)
        recipe.resetVersion()
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteCookbookImage() = delete("/cookbooks/{id}") {
        val id = getPathId()
        val cookbook = cookbookService.getEntityById(id)
        if (!cookbookService.isAdminOfCookbook(id, getSessionUserId())) throw ForbiddenException(ForbiddenCause.MUST_BE_COOKBOOK_ADMINISTRATOR)
        imageService.deleteImage(COOKBOOKS_IMG_PATH, id, cookbook.imageVersion)
        cookbook.resetVersion()
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteUserImage() = delete("/users/{id}") {
        val id = getPathId()
        checkUserEditionRights(id)
        val user = userService.getEntityById(id)
        imageService.deleteImage(USERS_IMG_PATH, id, user.imageVersion)
        user.resetVersion()
        call.respond(HttpStatusCode.OK)
    }





}