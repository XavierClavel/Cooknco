package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.DefaultImageService
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.ImageUploadTicketService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.checkRecipeEditionRights
import com.xavierclavel.utils.checkUserEditionRights
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.logger
import com.xavierclavel.utils.receiveImage
import shared.enums.ImageBucket
import shared.utils.Filepath.COOKBOOKS_IMG_PATH
import shared.utils.Filepath.DEFAULT_IMAGE
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import shared.utils.Filepath.USERS_IMG_PATH
import shared.utils.URL.IMAGE_URL
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject
import com.drew.metadata.Metadata
import java.awt.image.BufferedImage
import java.io.File

object ImageController: Controller(IMAGE_URL) {
    val imageService : ImageService by inject(ImageService::class.java)
    val defaultImageService: DefaultImageService by inject(DefaultImageService::class.java)
    val recipeService: RecipeService by inject(RecipeService::class.java)
    val cookbookService: CookbookService by inject(CookbookService::class.java)
    val userService: UserService by inject(UserService::class.java)
    val imageUploadTicketService: ImageUploadTicketService by inject(ImageUploadTicketService::class.java)

    private val WEBP = ContentType("image", "webp")

    override fun Route.routes() {
        ImageBucket.entries.forEach { serveBucket(it) }

        // Outside the authenticate block on purpose: the ticket in the URL is what authenticates
        // this one, and its holder has no session. See ImageUploadTicketService.
        redeemRecipeImageTicket()

        authenticate("auth-session", "bearer-auth") {
            uploadRecipeImage()
            uploadCookbookImage()
            uploadUserIcon()
            deleteRecipeImage()
            deleteCookbookImage()
            deleteUserImage()
        }
    }

    /**
     * The pictures of one bucket: `{id}-v{version}.webp` for the entities that have one,
     * and the backoffice-managed default for the ones that do not.
     *
     * The default gets a route of its own rather than being left to the static tree, which
     * can only fall back to a file that is already on the volume: until an operator
     * uploads one there is nothing there, and the app still has to have something to show.
     * A constant path outranks the static handler's tailcard, so this wins for that one name.
     */
    private fun Route.serveBucket(bucket: ImageBucket) {
        get("/${bucket.dir}/$DEFAULT_IMAGE") {
            val (bytes, lastModified) = defaultImageService.read(bucket)
            // Unlike a versioned filename this one is mutable, so it is only cached for as
            // long as an operator can be expected to wait after replacing it.
            call.response.cacheControl(CacheControl.MaxAge(300))
            lastModified?.let { call.response.headers.append(HttpHeaders.ETag, it.toString()) }
            call.respondBytes(bytes, WEBP)
        }

        staticFiles("/${bucket.dir}", File(bucket.path)) {
            default(DEFAULT_IMAGE)
            modify { file, call ->
                call.response.headers.append(HttpHeaders.ETag, file.lastModified().toString())
            }
        }
    }


    private fun Route.uploadRecipeImage() = post("/recipes/{id}") {
        val id = getPathId()
        checkRecipeEditionRights(recipeService.getRecipeOwner(id).id)
        val (image, metadata) = receiveImage()
        saveRecipeImage(id, image, metadata)
        call.respond(HttpStatusCode.OK)
    }

    /**
     * The same upload, for a caller holding a ticket instead of a session — an MCP client,
     * which cannot carry a picture through a tool call at all. What the ticket is worth, and
     * why the rights are checked again as it is spent, is in [ImageUploadTicketService].
     *
     * The size bound is passed here and nowhere else: this is the one image endpoint reachable
     * without an account behind it.
     */
    private fun Route.redeemRecipeImageTicket() = post("/upload/{ticket}") {
        val ticket = call.parameters["ticket"] ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val redeemed = imageUploadTicketService.redeemRecipeTicket(ticket)
        val (image, metadata) = receiveImage(maxBytes = imageUploadTicketService.maxUploadBytes)
        saveRecipeImage(redeemed.recipeId, image, metadata)
        logger.info { "Recipe ${redeemed.recipeId} was given a picture by user ${redeemed.userId} over an upload ticket" }
        call.respond(HttpStatusCode.OK)
    }

    /**
     * Writes both sizes a recipe is shown at, then drops the pair it replaced.
     *
     * Shared by the two ways in so that they cannot drift: the version bump is what every URL
     * to the picture is built from, and a caller that wrote the files without it would leave
     * every reader on the old one.
     */
    private fun saveRecipeImage(id: Long, image: BufferedImage, metadata: Metadata) {
        val recipe = recipeService.getEntityById(id)

        imageService.saveImage(RECIPES_IMG_PATH, id, recipe.imageVersion + 1, ImageBucket.RECIPE.size, image, metadata)
        imageService.saveImage(RECIPES_THUMBNAIL_PATH, id, recipe.imageVersion + 1, ImageBucket.RECIPE_THUMBNAIL.size, image, metadata)

        recipe.increaseVersion()
        imageService.deleteImage(RECIPES_IMG_PATH, id, recipe.imageVersion - 1)
        imageService.deleteImage(RECIPES_THUMBNAIL_PATH, id, recipe.imageVersion - 1)
    }

    private fun Route.uploadCookbookImage() = post("/cookbooks/{id}") {
        val id = getPathId()
        val (image, metadata) = receiveImage()
        val cookbook = cookbookService.getEntityById(id)
        imageService.saveImage(COOKBOOKS_IMG_PATH, id, cookbook.imageVersion + 1, ImageBucket.COOKBOOK.size, image, metadata)
        cookbook.increaseVersion()
        imageService.deleteImage(COOKBOOKS_IMG_PATH, id, cookbook.imageVersion - 1)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.uploadUserIcon() = post("/users/{id}") {
        val id = getPathId()
        checkUserEditionRights(id)
        val (image, metadata) = receiveImage()
        val user = userService.getEntityById(id)
        imageService.saveImage(USERS_IMG_PATH, id, user.imageVersion + 1, ImageBucket.USER.size, image, metadata)
        user.increaseVersion()
        imageService.deleteImage(USERS_IMG_PATH, id, user.imageVersion - 1)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteRecipeImage() = delete("/recipes/{id}") {
        val id = getPathId()
        val recipe = recipeService.getEntityById(id)
        checkRecipeEditionRights(recipeService.getRecipeOwner(id).id)
        imageService.deleteImage(RECIPES_IMG_PATH, id, recipe.imageVersion)
        recipe.increaseVersion()
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteCookbookImage() = delete("/cookbooks/{id}") {
        val id = getPathId()
        val cookbook = cookbookService.getEntityById(id)
        if (!cookbookService.isAdminOfCookbook(id, getSessionUserId())) throw ForbiddenException(ForbiddenCause.MUST_BE_COOKBOOK_ADMINISTRATOR)
        imageService.deleteImage(COOKBOOKS_IMG_PATH, id, cookbook.imageVersion)
        cookbook.increaseVersion()
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.deleteUserImage() = delete("/users/{id}") {
        val id = getPathId()
        checkUserEditionRights(id)
        val user = userService.getEntityById(id)
        imageService.deleteImage(USERS_IMG_PATH, id, user.imageVersion)
        user.increaseVersion()
        call.respond(HttpStatusCode.OK)
    }





}