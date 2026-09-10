package com.xavierclavel.mcp

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.services.CookbookService
import com.xavierclavel.services.IngredientService
import com.xavierclavel.services.LikeService
import com.xavierclavel.services.NotificationService
import com.xavierclavel.services.RecipeIngredientService
import com.xavierclavel.services.RecipeService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.logger
import io.ebean.Paging
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.error
import io.modelcontextprotocol.kotlin.sdk.types.success
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.koin.java.KoinJavaComponent.inject
import shared.RecipeFilter
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.DishClass
import shared.enums.Locale
import shared.enums.Sort
import shared.infodto.IngredientInfo
import shared.infodto.RecipeInfo
import java.util.Properties

/**
 * The tools behind `/mcp`.
 *
 * Everything here runs as one authenticated account — the one whose session token the client
 * sent — and reaches the same services the REST controllers call, so a tool is bound by the
 * same visibility and ownership rules a request from the app would be. Nothing is re-derived
 * here: where a controller checks a right before acting, the matching tool makes the same
 * check, and the few that are enforced by the controller rather than the service are
 * re-stated at the call site with a comment saying so.
 *
 * A [Server] is built per request ([forUser]) because the endpoint is stateless: the tools
 * close over the caller's user id, so there is no ambient session to get wrong, and nothing
 * survives the request that produced it. Registering twelve tools costs a handful of data
 * class allocations, the schemas being shared vals.
 */
object CookncoMcpServer {
    private val recipeService: RecipeService by inject(RecipeService::class.java)
    private val recipeIngredientService: RecipeIngredientService by inject(RecipeIngredientService::class.java)
    private val cookbookService: CookbookService by inject(CookbookService::class.java)
    private val ingredientService: IngredientService by inject(IngredientService::class.java)
    private val userService: UserService by inject(UserService::class.java)
    private val likeService: LikeService by inject(LikeService::class.java)
    private val notificationService: NotificationService by inject(NotificationService::class.java)

    const val SERVER_NAME = "cooknco"

    /** Keeps one greedy call from filling the model's context with a whole cookbook. */
    private const val MAX_PAGE_SIZE = 50
    private const val DEFAULT_PAGE_SIZE = 20

    /** Compact on purpose: a tool result is read as tokens, and the indentation buys nothing. */
    private val payload = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private val version: String by lazy {
        runCatching {
            Properties()
                .apply { load(CookncoMcpServer::class.java.getResourceAsStream("/version.properties")) }
                .getProperty("version")
        }.getOrNull() ?: "dev"
    }

    fun forUser(userId: Long): Server {
        val server = Server(
            Implementation(name = SERVER_NAME, version = version, title = "Cook&co"),
            ServerOptions(
                capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
            ),
        )

        server.tool(searchRecipes) { searchRecipes(userId, it) }
        server.tool(getRecipe) { getRecipe(userId, it) }
        server.tool(myFeed) { myFeed(userId, it) }
        server.tool(listCookbooks) { listCookbooks(userId, it) }
        server.tool(getCookbook) { getCookbook(userId, it) }
        server.tool(searchIngredients) { searchIngredients(it) }
        server.tool(getUser) { getUser(userId, it) }

        server.tool(createRecipe) { createRecipe(userId, it) }
        server.tool(updateRecipe) { updateRecipe(userId, it) }
        server.tool(deleteRecipe) { deleteRecipe(userId, it) }
        server.tool(likeRecipe) { likeRecipe(userId, it) }
        server.tool(addRecipeToCookbook) { addRecipeToCookbook(userId, it) }

        return server
    }

    /**
     * Registers one tool, turning what the domain throws into a result the caller can act on.
     *
     * A refusal is the *tool's* answer, not a transport failure: reported with `isError` and the
     * reason in the text, the model reads "not_allowed_to_edit_recipe" and stops, whereas a
     * JSON-RPC error would only tell it the call broke. The keys are the ones the REST API
     * already returns, so an operator reading a log sees the same vocabulary either way.
     *
     * An unexpected exception is logged and answered generically — a stack trace is for the
     * server's logs, not for a model's context.
     */
    private fun Server.tool(definition: Tool, handler: (JsonObject) -> String) =
        addTool(definition) { request ->
            val arguments = request.arguments ?: JsonObject(emptyMap())
            try {
                arguments.rejectUnknown(definition.inputSchema)
                CallToolResult.success(handler(arguments))
            } catch (e: InvalidToolArgument) {
                CallToolResult.error("Invalid arguments for ${definition.name}: ${e.message}")
            } catch (e: NotFoundException) {
                CallToolResult.error("Not found: ${e.message}")
            } catch (e: ForbiddenException) {
                CallToolResult.error("Not allowed: ${e.message}")
            } catch (e: BadRequestException) {
                CallToolResult.error("Rejected: ${e.message}")
            } catch (e: UnauthorizedException) {
                CallToolResult.error("Rejected: ${e.message}")
            } catch (e: ServiceUnavailableException) {
                CallToolResult.error("Temporarily unavailable: ${e.message}")
            } catch (e: Exception) {
                logger.error { "MCP tool ${definition.name} failed: ${e.stackTraceToString()}" }
                CallToolResult.error("${definition.name} failed unexpectedly. The failure has been logged.")
            }
        }

    // ---------------------------------------------------------------- read tools

    private val pageArg = "page" to intArg("Page to return, zero-based. Defaults to 0.")
    private val sizeArg = "size" to intArg("Results per page, at most $MAX_PAGE_SIZE. Defaults to $DEFAULT_PAGE_SIZE.")
    private val localeArg = "locale" to enumArg(
        "Language for ingredient names. Defaults to EN.",
        Locale.entries.toTypedArray(),
    )

    private val searchRecipesSchema = toolSchema(
        "query" to stringArg("Free-text search over recipe titles. Matches loosely, accents and case ignored."),
        "dish_classes" to arrayArg(
            "Only these kinds of dish.",
            enumArg("A kind of dish.", DishClass.entries.toTypedArray()),
        ),
        "ingredient_ids" to arrayArg(
            "Only recipes using every one of these ingredients. Ids come from search_ingredients.",
            intArg("An ingredient id."),
        ),
        "owner" to stringArg("Only recipes written by this username."),
        "liked_by_me" to boolArg("Only recipes the authenticated user has liked."),
        "cookbook_id" to intArg("Only recipes in this cookbook."),
        "sort" to enumArg("Ordering of the results. Defaults to NONE.", Sort.entries.toTypedArray()),
        pageArg,
        sizeArg,
    )

    private val searchRecipes = Tool(
        name = "search_recipes",
        title = "Search recipes",
        description = "Search the recipes the authenticated user is allowed to see. " +
            "Returns a page of summaries (id, title, kind of dish, author, likes); " +
            "call get_recipe with an id for the ingredients and steps. " +
            "Filters combine, so the result is the recipes matching all of them.",
        inputSchema = searchRecipesSchema,
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    private fun searchRecipes(userId: Long, arguments: JsonObject): String {
        val filter = RecipeFilter(
            user = arguments.optionalString("owner")?.let { usernameToId(it) },
            likedBy = userId.takeIf { arguments.optionalBoolean("liked_by_me") == true },
            cookbook = arguments.optionalLong("cookbook_id"),
            search = arguments.optionalString("query"),
            ingredient = arguments.optionalLongList("ingredient_ids").toSet(),
            dishClasses = arguments.optionalStringList("dish_classes")
                ?.map { dishClass -> parseEnum<DishClass>(dishClass, "dish_classes") }
                ?.toSet()
                ?: emptySet(),
        )
        return recipePage(userId, arguments, filter, arguments.optionalEnum<Sort>("sort") ?: Sort.NONE)
    }

    private val myFeedSchema = toolSchema(pageArg, sizeArg)

    private val myFeed = Tool(
        name = "my_feed",
        title = "My feed",
        description = "The recipes written by the users the authenticated user follows, newest first. " +
            "This is the same feed the app shows on its home page.",
        inputSchema = myFeedSchema,
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    private fun myFeed(userId: Long, arguments: JsonObject): String =
        recipePage(userId, arguments, RecipeFilter(followedBy = userId), Sort.DATE_DESCENDING)

    private fun recipePage(userId: Long, arguments: JsonObject, filter: RecipeFilter, sort: Sort): String {
        val paging = arguments.paging()
        val recipes = recipeService.findList(
            requestorId = userId,
            paging = paging,
            sort = sort,
            recipeFilter = filter,
        )
        return payload.encodeToString(
            PagedResult(page = paging.pageIndex(), size = paging.pageSize(), results = recipes),
        )
    }

    private val getRecipeSchema = toolSchema(
        "recipe_id" to intArg("Id of the recipe, as returned by search_recipes."),
        localeArg,
        required = listOf("recipe_id"),
    )

    private val getRecipe = Tool(
        name = "get_recipe",
        title = "Get a recipe",
        description = "One recipe in full: ingredients with their amounts and units, the steps, " +
            "timings, yield and tips. Fails if the recipe is private to someone else or has been hidden.",
        inputSchema = getRecipeSchema,
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    private fun getRecipe(userId: Long, arguments: JsonObject): String =
        payload.encodeToString(
            recipeService.getById(userId, arguments.requiredLong("recipe_id"), arguments.locale()),
        )

    private val listCookbooksSchema = toolSchema(
        "member" to stringArg("Only cookbooks this username belongs to."),
        "recipe_id" to intArg("Only cookbooks containing this recipe."),
        "query" to stringArg("Free-text search over cookbook titles."),
        pageArg,
        sizeArg,
    )

    private val listCookbooks = Tool(
        name = "list_cookbooks",
        title = "List cookbooks",
        description = "The cookbooks the authenticated user is allowed to see: their own, and the public " +
            "ones. Returns titles with recipe and member counts; call get_cookbook for what is inside one.",
        inputSchema = listCookbooksSchema,
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    private fun listCookbooks(userId: Long, arguments: JsonObject): String {
        val paging = arguments.paging()
        val cookbooks = cookbookService.listCookbooks(
            paging,
            Sort.NONE,
            user = arguments.optionalString("member")?.let { usernameToId(it) },
            recipe = arguments.optionalLong("recipe_id"),
            search = arguments.optionalString("query"),
            currentUser = userId,
        )
        return payload.encodeToString(
            PagedResult(page = paging.pageIndex(), size = paging.pageSize(), results = cookbooks),
        )
    }

    private val getCookbookSchema = toolSchema(
        "cookbook_id" to intArg("Id of the cookbook, as returned by list_cookbooks."),
        pageArg,
        sizeArg,
        required = listOf("cookbook_id"),
    )

    private val getCookbook = Tool(
        name = "get_cookbook",
        title = "Get a cookbook",
        description = "One cookbook with a page of the recipes in it, and who added each. " +
            "Fails if the cookbook is not visible to the authenticated user.",
        inputSchema = getCookbookSchema,
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    private fun getCookbook(userId: Long, arguments: JsonObject): String {
        val cookbookId = arguments.requiredLong("cookbook_id")
        val paging = arguments.paging()
        // getCookbook applies the visibility rules; the recipe listing below has none of its
        // own, so it must not run before that call has vetted the cookbook.
        val cookbook = cookbookService.getCookbook(cookbookId, userId)
        return payload.encodeToString(
            CookbookWithRecipes(
                cookbook = cookbook,
                page = paging.pageIndex(),
                size = paging.pageSize(),
                recipes = cookbookService.getCookbookRecipes(cookbookId, paging),
            ),
        )
    }

    private val searchIngredientsSchema = toolSchema(
        "query" to stringArg("Free-text search over ingredient names. Omitted, it browses the table."),
        localeArg,
        pageArg,
        sizeArg,
    )

    private val searchIngredients = Tool(
        name = "search_ingredients",
        title = "Search ingredients",
        description = "Look up ingredients from the shared table, to reference by id when writing a recipe. " +
            "Returns each ingredient's id, name, family, and which units it may be measured in — " +
            "create_recipe rejects a unit outside allowed_types. Nutrition data is not included.",
        inputSchema = searchIngredientsSchema,
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    private fun searchIngredients(arguments: JsonObject): String {
        val paging = arguments.paging()
        val locale = arguments.locale()
        val (total, ingredients) = ingredientService.search(
            arguments.optionalString("query") ?: "",
            paging,
            locale,
        )
        return payload.encodeToString(
            IngredientPage(
                page = paging.pageIndex(),
                size = paging.pageSize(),
                total = total,
                results = ingredients.map { it.toMcp(locale) },
            ),
        )
    }

    private val getUserSchema = toolSchema(
        "username" to stringArg("Whose profile to read. Defaults to the authenticated user."),
    )

    private val getUser = Tool(
        name = "get_user",
        title = "Get a user",
        description = "A member's public profile: username, bio, join date, and how many recipes, likes, " +
            "cookbooks, followers and follows they have. Called with no argument it returns the " +
            "authenticated user, which is how to find out whose account these tools are acting on.",
        inputSchema = getUserSchema,
        annotations = ToolAnnotations(readOnlyHint = true, openWorldHint = false),
    )

    private fun getUser(userId: Long, arguments: JsonObject): String {
        val username = arguments.optionalString("username")
        val user = if (username == null) {
            userService.getUser(userId)
        } else {
            userService.getUserByUsername(username) ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)
        }
        return payload.encodeToString(UserResult(user = user, isAuthenticatedUser = user.id == userId))
    }

    // --------------------------------------------------------------- write tools

    private val ingredientRowArg = objectArg(
        "One ingredient line. Give either ingredient_id (from search_ingredients) or custom_name, never both.",
        "ingredient_id" to intArg("Id of an ingredient in the shared table."),
        "custom_name" to stringArg("Free text, for an ingredient not in the table. At most 50 characters."),
        "amount" to numberArg("How much, in the given unit."),
        "unit" to enumArg(
            "Unit the amount is in. Defaults to NONE. Must be one the ingredient allows.",
            AmountUnit.entries.toTypedArray(),
        ),
        "complement" to stringArg("A note on this line, such as 'finely chopped'."),
    )

    private val recipeFields = arrayOf(
        "title" to stringArg("Title of the recipe."),
        "description" to stringArg("Short description of the dish."),
        "dish_class" to enumArg("What kind of dish this is. Defaults to MAIN_DISH.", DishClass.entries.toTypedArray()),
        "yield" to intArg("How many people it serves."),
        "preparation_time" to intArg("Preparation time in minutes."),
        "cooking_time" to intArg("Cooking time in minutes."),
        "cooking_temperature" to intArg("Oven temperature in degrees Celsius."),
        "ingredients" to arrayArg("The ingredient lines, in the order they should be read.", ingredientRowArg),
        "steps" to arrayArg("The steps, in order. One entry per step.", stringArg("One step.")),
        "tips" to stringArg("Closing tips or variations."),
    )

    private val createRecipeSchema = toolSchema(*recipeFields, required = listOf("title"))

    private val createRecipe = Tool(
        name = "create_recipe",
        title = "Create a recipe",
        description = "Write a new recipe into the authenticated user's account. It is published immediately: " +
            "the user's followers are notified, and it appears in their feed. Returns the created recipe.",
        inputSchema = createRecipeSchema,
        annotations = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false),
    )

    private fun createRecipe(userId: Long, arguments: JsonObject): String {
        val owner = userService.getEntityById(userId)
        val recipeDto = arguments.toRecipeDTO()
        // Ingredients are validated before the recipe row exists, so a rejected line leaves
        // nothing behind — same order as RecipeController.createRecipe.
        val ingredients = recipeIngredientService.validateIngredients(recipeDto)
        val recipe = recipeService.createRecipe(recipeDto, owner)
        recipeIngredientService.replaceRecipeIngredients(recipe.id, ingredients)
        val created = recipeService.getRawById(recipe.id, userId, Locale.EN)
        logger.info { "Recipe ${created.id} (${created.title}) created by user ${owner.username} over MCP" }
        // After the ingredients, so what a follower is sent points at a finished recipe.
        notificationService.onRecipeCreated(recipeService.getEntityById(recipe.id))
        return payload.encodeToString(created)
    }

    private val updateRecipeSchema = toolSchema(
        "recipe_id" to intArg("Id of the recipe to change. Must belong to the authenticated user."),
        *recipeFields,
        required = listOf("recipe_id"),
    )

    private val updateRecipe = Tool(
        name = "update_recipe",
        title = "Update a recipe",
        description = "Change a recipe the authenticated user owns. Only the fields given are changed; " +
            "anything omitted keeps its current value. Passing ingredients or steps replaces that whole " +
            "list, so send it complete. Returns the updated recipe.",
        inputSchema = updateRecipeSchema,
        annotations = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true),
    )

    private fun updateRecipe(userId: Long, arguments: JsonObject): String {
        val recipeId = arguments.requiredLong("recipe_id")
        val current = recipeService.getRawById(recipeId, userId, Locale.EN)
        // The REST layer checks ownership in checkRecipeEditionRights rather than in the
        // service, so the same check has to be made here.
        if (current.owner.id != userId) throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_EDIT_RECIPE)

        // A PUT on the REST API replaces the whole recipe. A model editing one field would then
        // blank every field it did not think to resend, so the current recipe is the base here
        // and the arguments are laid over it.
        val recipeDto = arguments.toRecipeDTO(current)
        recipeIngredientService.updateRecipeIngredients(recipeId, recipeDto)
        val updated = recipeService.updateRecipe(recipeId, recipeDto)
        logger.info { "Recipe ${updated.id} (${updated.title}) edited by user ${current.owner.username} over MCP" }
        return payload.encodeToString(recipeService.getRawById(recipeId, userId, Locale.EN))
    }

    private val deleteRecipeSchema = toolSchema(
        "recipe_id" to intArg("Id of the recipe to delete. Must belong to the authenticated user."),
        required = listOf("recipe_id"),
    )

    private val deleteRecipe = Tool(
        name = "delete_recipe",
        title = "Delete a recipe",
        description = "Delete a recipe the authenticated user owns. A recipe that others have liked or " +
            "saved into a cookbook is withdrawn rather than erased: it stops appearing anywhere, but the " +
            "row survives so those references still resolve. The result says which happened.",
        inputSchema = deleteRecipeSchema,
        annotations = ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true),
    )

    private fun deleteRecipe(userId: Long, arguments: JsonObject): String {
        val recipeId = arguments.requiredLong("recipe_id")
        val recipe = recipeService.getRawById(recipeId, userId, Locale.EN)
        if (recipe.owner.id != userId) throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_EDIT_RECIPE)

        recipeService.tagRecipeForDeletion(recipeId)
        recipeService.tryDelete(recipeId)
        val erased = recipeService.findEntityById(recipeId) == null
        logger.info { "Recipe $recipeId (${recipe.title}) deleted by user ${recipe.owner.username} over MCP" }
        return payload.encodeToString(
            DeletionResult(
                recipeId = recipeId,
                title = recipe.title,
                erased = erased,
                detail = if (erased) {
                    "Deleted."
                } else {
                    "Withdrawn: the recipe is no longer visible, but it is still referenced by a like " +
                        "or a cookbook, so the row was kept."
                },
            ),
        )
    }

    private val likeRecipeSchema = toolSchema(
        "recipe_id" to intArg("Id of the recipe."),
        "liked" to boolArg("true to like, false to remove an existing like. Defaults to true."),
        required = listOf("recipe_id"),
    )

    private val likeRecipe = Tool(
        name = "like_recipe",
        title = "Like a recipe",
        description = "Like a recipe as the authenticated user, or take that like back with liked=false. " +
            "Doing it twice is harmless: the result says whether anything changed.",
        inputSchema = likeRecipeSchema,
        annotations = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true),
    )

    private fun likeRecipe(userId: Long, arguments: JsonObject): String {
        val recipeId = arguments.requiredLong("recipe_id")
        val liked = arguments.optionalBoolean("liked") ?: true
        // Reading it first is both the visibility check and the existence check: liking a recipe
        // one cannot see has to fail the way reading it would.
        val recipe = recipeService.getById(userId, recipeId, Locale.EN)
        val alreadyLiked = likeService.exists(recipeId, userId)

        val changed = when {
            liked && !alreadyLiked -> {
                likeService.createLike(recipeId, userId)
                true
            }
            !liked && alreadyLiked -> {
                likeService.deleteLike(recipeId, userId)
                // Purging is only for a recipe whose owner asked for deletion and which was kept
                // alive by what still referenced it. The tag has to be checked here because
                // tryDelete does not check it itself, so calling it unconditionally — as
                // LikeController.deleteLike does — erases a live recipe whose last like was just
                // taken back.
                if (recipeService.getEntityById(recipeId).taggedForDeletion) {
                    recipeService.tryDelete(recipeId)
                }
                true
            }
            else -> false
        }

        return payload.encodeToString(
            LikeResult(
                recipeId = recipeId,
                title = recipe.title,
                liked = liked,
                changed = changed,
                detail = if (changed) {
                    if (liked) "Liked." else "Like removed."
                } else {
                    if (liked) "Already liked; nothing to do." else "Was not liked; nothing to do."
                },
            ),
        )
    }

    private val addRecipeToCookbookSchema = toolSchema(
        "cookbook_id" to intArg("Id of the cookbook. The authenticated user must be a member of it."),
        "recipe_id" to intArg("Id of the recipe to add."),
        required = listOf("cookbook_id", "recipe_id"),
    )

    private val addRecipeToCookbook = Tool(
        name = "add_recipe_to_cookbook",
        title = "Add a recipe to a cookbook",
        description = "Save a recipe into one of the cookbooks the authenticated user is a member of. " +
            "The recipe stays owned by whoever wrote it.",
        inputSchema = addRecipeToCookbookSchema,
        annotations = ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true),
    )

    private fun addRecipeToCookbook(userId: Long, arguments: JsonObject): String {
        val cookbookId = arguments.requiredLong("cookbook_id")
        val recipeId = arguments.requiredLong("recipe_id")
        // Same three checks as CookbookController.addCookbookRecipe, in the same order, plus the
        // visibility read: a recipe the caller cannot see is not one they can file away.
        val recipe = recipeService.getById(userId, recipeId, Locale.EN)
        if (cookbookService.doesCookbookHaveRecipe(cookbookId, recipeId)) {
            throw BadRequestException(BadRequestCause.RECIPE_ALREADY_IN_COOKBOOK)
        }
        if (!cookbookService.isMemberOfCookbook(cookbookId, userId)) {
            throw ForbiddenException(ForbiddenCause.NOT_MEMBER_OF_COOKBOOK)
        }
        cookbookService.addRecipeToCookbook(cookbookId, recipeId, userId)
        return payload.encodeToString(
            CookbookAdditionResult(
                cookbook = cookbookService.getCookbook(cookbookId, userId),
                recipeId = recipeId,
                title = recipe.title,
            ),
        )
    }

    // ------------------------------------------------------------------- helpers

    private fun JsonObject.paging(): Paging =
        Paging.of(
            optionalInt("page")?.also {
                if (it < 0) throw InvalidToolArgument("'page' must not be negative")
            } ?: 0,
            optionalInt("size")?.also {
                if (it < 1) throw InvalidToolArgument("'size' must be at least 1")
                if (it > MAX_PAGE_SIZE) throw InvalidToolArgument("'size' must be at most $MAX_PAGE_SIZE")
            } ?: DEFAULT_PAGE_SIZE,
        )

    private fun JsonObject.locale(): Locale = optionalEnum<Locale>("locale") ?: Locale.EN

    private inline fun <reified T : Enum<T>> parseEnum(value: String, argument: String): T =
        enumValues<T>().find { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw InvalidToolArgument(
                "'$argument' must contain only ${enumValues<T>().joinToString(", ") { it.name }}, got '$value'"
            )

    private fun usernameToId(username: String): Long =
        userService.findByUsername(username)?.id
            ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)

    /**
     * Builds the DTO the recipe services take.
     *
     * [current] is the recipe being edited, whose values stand in for every argument the caller
     * left out; on a create there is none and the DTO's own defaults apply.
     */
    private fun JsonObject.toRecipeDTO(current: RecipeInfo? = null): RecipeDTO {
        val ingredients = optionalObjectList("ingredients")?.map { it.toIngredientRow() }
            ?: current?.ingredients?.map { row ->
                RecipeDTO.RecipeIngredientDTO(
                    id = row.id,
                    customName = if (row.id == null) row.name else null,
                    unit = row.unit,
                    amount = row.amount,
                    complement = row.complement,
                )
            }

        return RecipeDTO(
            title = if (current == null) requiredString("title") else optionalString("title") ?: current.title,
            description = optionalString("description") ?: current?.description ?: "",
            dishClass = optionalEnum<DishClass>("dish_class") ?: current?.dishClass ?: DishClass.MAIN_DISH,
            yield = optionalInt("yield") ?: current?.yield,
            preparationTime = optionalInt("preparation_time") ?: current?.preparationTime,
            cookingTime = optionalInt("cooking_time") ?: current?.cookingTime,
            cookingTemperature = optionalInt("cooking_temperature") ?: current?.cookingTemperature,
            ingredients = ingredients?.toMutableList() ?: mutableListOf(),
            steps = (optionalStringList("steps") ?: current?.steps)?.toMutableList() ?: mutableListOf(),
            tips = optionalString("tips") ?: current?.tips ?: "",
        )
    }

    private fun JsonObject.toIngredientRow() = RecipeDTO.RecipeIngredientDTO(
        id = optionalLong("ingredient_id"),
        customName = optionalString("custom_name"),
        unit = optionalEnum<AmountUnit>("unit") ?: AmountUnit.NONE,
        amount = optionalFloat("amount"),
        complement = optionalString("complement"),
    )

    /**
     * The searchable half of an ingredient. The nutrition columns are left out: a page of them
     * would be twenty times data no caller writing a recipe reads, and the fields kept are the
     * ones create_recipe validates against.
     */
    private fun IngredientInfo.toMcp(locale: Locale) = McpIngredient(
        id = id,
        name = name[locale] ?: name[Locale.EN] ?: name.values.firstOrNull() ?: "",
        type = type.name,
        defaultUnit = defaultUnit?.name,
        allowedUnits = allowedTypes.flatMap { AmountUnit.of(it) }.map { it.name },
    )
}
