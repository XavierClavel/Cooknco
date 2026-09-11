package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import main.com.xavierclavel.utils.callTool
import main.com.xavierclavel.utils.callToolOk
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createLike
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.follow
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.getRecipeRaw
import main.com.xavierclavel.utils.uploadRecipeImage
import main.com.xavierclavel.utils.uploadToTicketUrl
import main.com.xavierclavel.utils.jsonRpc
import main.com.xavierclavel.utils.mcpPostRaw
import main.com.xavierclavel.utils.mcpResult
import io.ktor.client.HttpClient
import main.com.xavierclavel.utils.mcpToken
import org.junit.jupiter.api.Test
import shared.dto.IngredientDTO
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import shared.enums.DishClass
import shared.enums.IngredientType
import shared.enums.Locale
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import shared.utils.URL.MCP_URL
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The MCP endpoint, driven over HTTP exactly as a client drives it.
 *
 * These are also what holds the SDK to a version that works here (see `backend/build.gradle.kts`):
 * a release built against a newer Ktor still compiles against this one and then fails at runtime
 * inside the transport, so the handshake and a tool call are exercised for real rather than
 * trusted to compile.
 */
class McpControllerTest : ApplicationTest() {

    /**
     * A session token for one of the fixture users, which is what an MCP client is configured
     * with. Named here so the fixture credential appears once rather than at every call.
     */
    private suspend fun HttpClient.tokenFor(username: String): String = mcpToken(username, password)

    private val readTools = setOf(
        "search_recipes",
        "get_recipe",
        "my_feed",
        "list_cookbooks",
        "get_cookbook",
        "search_ingredients",
        "get_user",
    )

    private val writeTools = setOf(
        "create_recipe",
        "update_recipe",
        "delete_recipe",
        "like_recipe",
        "add_recipe_to_cookbook",
        "prepare_recipe_image_upload",
    )

    // ------------------------------------------------------------- the endpoint

    @Test
    fun `a call without a token is refused`() = runTest {
        client.mcpPostRaw(null, jsonRpc("tools/list")).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `a call with a token that is not a session is refused`() = runTest {
        client.mcpPostRaw("not-a-session-id", jsonRpc("tools/list")).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    /**
     * The cookie is deliberately not accepted here, unlike on every other authenticated route:
     * CORS is `anyHost()` with credentials, so a cookie-authenticated `/mcp` would be drivable
     * from any page a logged-in user visits. See McpController.
     */
    @Test
    fun `a session cookie does not authenticate the endpoint`() = runTestAsUser {
        client.post(MCP_URL) {
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            contentType(ContentType.Application.Json)
            setBody(jsonRpc("tools/list").toString())
        }.apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `the endpoint serves POST only`() = runTestAsUser {
        client.get(MCP_URL).apply {
            assertEquals(HttpStatusCode.MethodNotAllowed, status)
            assertEquals("POST", headers[HttpHeaders.Allow])
        }
        client.delete(MCP_URL).apply {
            assertEquals(HttpStatusCode.MethodNotAllowed, status)
        }
    }

    /** Streamable HTTP requires it, so a client that forgets is told rather than mis-served. */
    @Test
    fun `a POST that does not accept an event stream is refused`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        client.post(MCP_URL) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(jsonRpc("tools/list").toString())
        }.apply {
            assertEquals(HttpStatusCode.NotAcceptable, status)
        }
    }

    @Test
    fun `initialize announces the server and its tools`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val body = client.mcpPostRaw(
            token,
            jsonRpc(
                "initialize",
                buildJsonObject {
                    put("protocolVersion", "2025-06-18")
                    putJsonObject("capabilities") {}
                    putJsonObject("clientInfo") {
                        put("name", "test-client")
                        put("version", "1.0.0")
                    }
                },
            ),
        ).let {
            assertEquals(HttpStatusCode.OK, it.status)
            it.bodyAsText()
        }

        // The endpoint installs the MCP JSON settings on its own route; with the application's
        // default ones the reply carries `"error": null` beside the result, which strict clients
        // reject. Asserted on the raw body because that is the part a client sees.
        assertFalse(body.contains("\"error\""), "initialize reply carried an error field: $body")

        val result = client.mcpResult(
            token,
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2025-06-18")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "test-client")
                    put("version", "1.0.0")
                }
            },
        )
        assertEquals("cooknco", result["serverInfo"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertNotNull(result["protocolVersion"])
        assertNotNull(result["capabilities"]!!.jsonObject["tools"], "tools capability missing: $result")
    }

    /**
     * The sequence a real client runs: initialize, the notification that follows it, then a tool
     * call in a *separate* POST carrying the version it negotiated. Each POST builds its own
     * server here, so this is what says no call depends on the handshake having been seen — and
     * that the header a client always sends afterwards is accepted rather than rejected as an
     * unsupported version.
     */
    @Test
    fun `a client's handshake, notification and later call all land`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        client.createRecipe(RecipeDTO(title = "Ratatouille"))

        val negotiated = client.mcpResult(
            token,
            "initialize",
            buildJsonObject {
                put("protocolVersion", "2025-06-18")
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") {
                    put("name", "test-client")
                    put("version", "1.0.0")
                }
            },
        )["protocolVersion"]!!.jsonPrimitive.content

        // A notification carries no id, so it is acknowledged rather than answered.
        client.mcpPostRaw(token, jsonRpc("notifications/initialized", id = null), negotiated).apply {
            assertEquals(HttpStatusCode.Accepted, status)
        }

        val response = client.mcpPostRaw(
            token,
            jsonRpc(
                "tools/call",
                buildJsonObject {
                    put("name", "search_recipes")
                    putJsonObject("arguments") {}
                },
            ),
            negotiated,
        )
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertContains(response.bodyAsText(), "Ratatouille")
    }

    @Test
    fun `a call claiming a protocol version the server does not know is refused`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        client.mcpPostRaw(token, jsonRpc("tools/list"), "1999-01-01").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `tools are listed with schemas, and the reads are marked read-only`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val tools = client.mcpResult(token, "tools/list")["tools"]!!.jsonArray.map { it.jsonObject }

        assertEquals(
            readTools + writeTools,
            tools.map { it["name"]!!.jsonPrimitive.content }.toSet(),
        )

        tools.forEach { tool ->
            val name = tool["name"]!!.jsonPrimitive.content
            assertEquals("object", tool["inputSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content, name)
            assertNotNull(tool["description"], "$name has no description")
            val readOnly = tool["annotations"]?.jsonObject?.get("readOnlyHint")?.jsonPrimitive?.boolean ?: false
            assertEquals(name in readTools, readOnly, "$name is marked wrong: readOnlyHint=$readOnly")
        }
    }

    // ------------------------------------------------------------- the read tools

    @Test
    fun `search_recipes returns the recipes the caller can see`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        client.createRecipe(RecipeDTO(title = "Ratatouille"))
        client.createRecipe(RecipeDTO(title = "Tarte tatin", dishClass = DishClass.DESERT))

        val result = client.callToolOk(token, "search_recipes")
        assertEquals(0, result["page"]!!.jsonPrimitive.int)
        assertEquals(
            setOf("Ratatouille", "Tarte tatin"),
            result["results"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun `search_recipes filters by query, dish class and author`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        client.createRecipe(RecipeDTO(title = "Ratatouille"))
        client.createRecipe(RecipeDTO(title = "Tarte tatin", dishClass = DishClass.DESERT))
        val username = client.callToolOk(token, "get_user")["user"]!!.jsonObject["username"]!!.jsonPrimitive.content

        val byQuery = client.callToolOk(token, "search_recipes", buildJsonObject { put("query", "tatin") })
        assertEquals(
            listOf("Tarte tatin"),
            byQuery["results"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content },
        )

        val byDishClass = client.callToolOk(
            token,
            "search_recipes",
            buildJsonObject { putJsonArray("dish_classes") { add("DESERT") } },
        )
        assertEquals(
            listOf("Tarte tatin"),
            byDishClass["results"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content },
        )

        val byAuthor = client.callToolOk(token, "search_recipes", buildJsonObject { put("owner", username) })
        assertEquals(2, byAuthor["results"]!!.jsonArray.size)

        val byOtherAuthor = client.callTool(token, "search_recipes", buildJsonObject { put("owner", "nobody") })
        assertTrue(byOtherAuthor.isError)
        assertContains(byOtherAuthor.text, "user_not_found")
    }

    @Test
    fun `get_recipe returns the ingredients and the steps`() = runTest {
        var ingredientId = 0L
        runAsAdmin {
            ingredientId = client.createIngredient(
                IngredientDTO(
                    name = mapOf(Locale.EN to "aubergine"),
                    type = IngredientType.VEGETABLE,
                    gramsPerUnit = 300f,
                ),
            ).id
        }
        runAsUser1 {
            val token = client.tokenFor(USER1)
            val recipe = client.createRecipe(
                RecipeDTO(
                    title = "Ratatouille",
                    description = "Slowly",
                    steps = mutableListOf("cut", "cook"),
                    ingredients = mutableListOf(
                        RecipeDTO.RecipeIngredientDTO(id = ingredientId, amount = 2f, unit = AmountUnit.UNIT),
                        RecipeDTO.RecipeIngredientDTO(customName = "thyme", complement = "a sprig"),
                    ),
                ),
            )

            val result = client.callToolOk(
                token,
                "get_recipe",
                buildJsonObject { put("recipe_id", recipe.id) },
            )
            assertEquals("Ratatouille", result["title"]!!.jsonPrimitive.content)
            assertEquals(listOf("cut", "cook"), result["steps"]!!.jsonArray.map { it.jsonPrimitive.content })
            val ingredients = result["ingredients"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf("aubergine", "thyme"), ingredients.map { it["name"]!!.jsonPrimitive.content })
            assertEquals(2f, ingredients.first()["amount"]!!.jsonPrimitive.float)
        }
    }

    @Test
    fun `get_recipe reports a missing recipe as a failed tool call, not a broken one`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val outcome = client.callTool(token, "get_recipe", buildJsonObject { put("recipe_id", 99999) })
        assertTrue(outcome.isError)
        assertContains(outcome.text, "recipe_not_found")
    }

    @Test
    fun `my_feed returns the recipes of followed users only`() = runTest {
        var user2Id = 0L
        var followedRecipe = ""
        runAsUser2 {
            user2Id = client.callToolOk(client.tokenFor(USER2), "get_user")["user"]!!
                .jsonObject["id"]!!.jsonPrimitive.long
            followedRecipe = client.createRecipe(RecipeDTO(title = "Followed recipe")).title
        }
        runAsUser1 {
            val token = client.tokenFor(USER1)
            client.createRecipe(RecipeDTO(title = "My own recipe"))

            assertTrue(client.callToolOk(token, "my_feed")["results"]!!.jsonArray.isEmpty())

            client.follow(user2Id)
            assertEquals(
                listOf(followedRecipe),
                client.callToolOk(token, "my_feed")["results"]!!.jsonArray
                    .map { it.jsonObject["title"]!!.jsonPrimitive.content },
            )
        }
    }

    @Test
    fun `list_cookbooks and get_cookbook return a cookbook and what is in it`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val cookbook = client.createCookbook()
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        client.callToolOk(
            token,
            "add_recipe_to_cookbook",
            buildJsonObject {
                put("cookbook_id", cookbook.id)
                put("recipe_id", recipe.id)
            },
        )

        val listed = client.callToolOk(token, "list_cookbooks")["results"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(cookbook.title), listed.map { it["title"]!!.jsonPrimitive.content })

        val result = client.callToolOk(token, "get_cookbook", buildJsonObject { put("cookbook_id", cookbook.id) })
        assertEquals(cookbook.title, result["cookbook"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("Ratatouille"),
            result["recipes"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `search_ingredients returns ids and the units the ingredient allows`() = runTest {
        runAsAdmin {
            client.createIngredient(
                IngredientDTO(
                    name = mapOf(Locale.EN to "aubergine", Locale.FR to "aubergine"),
                    type = IngredientType.VEGETABLE,
                    gramsPerUnit = 300f,
                ),
            )
        }
        runAsUser1 {
            val result = client.callToolOk(
                client.tokenFor(USER1),
                "search_ingredients",
                buildJsonObject { put("query", "aubergine") },
            )
            val found = result["results"]!!.jsonArray.single().jsonObject
            assertEquals("aubergine", found["name"]!!.jsonPrimitive.content)
            assertEquals("VEGETABLE", found["type"]!!.jsonPrimitive.content)
            val allowed = found["allowedUnits"]!!.jsonArray.map { it.jsonPrimitive.content }
            // Countable and measurable by weight, so both families are offered and volume is not.
            assertContains(allowed, AmountUnit.UNIT.name)
            assertContains(allowed, AmountUnit.GRAM.name)
            assertFalse(allowed.contains(AmountUnit.LITER.name), "volume should not be allowed: $allowed")
        }
    }

    @Test
    fun `get_user answers for the calling token, and for a named member`() = runTest {
        var user2Name = ""
        runAsUser2 {
            user2Name = client.callToolOk(client.tokenFor(USER2), "get_user")["user"]!!
                .jsonObject["username"]!!.jsonPrimitive.content
        }
        runAsUser1 {
            val token = client.tokenFor(USER1)
            val me = client.callToolOk(token, "get_user")
            assertTrue(me["isAuthenticatedUser"]!!.jsonPrimitive.boolean)

            val other = client.callToolOk(token, "get_user", buildJsonObject { put("username", user2Name) })
            assertEquals(user2Name, other["user"]!!.jsonObject["username"]!!.jsonPrimitive.content)
            assertFalse(other["isAuthenticatedUser"]!!.jsonPrimitive.boolean)
        }
    }

    // ------------------------------------------------------------ the write tools

    @Test
    fun `create_recipe writes a recipe owned by the calling account`() = runTest {
        var ingredientId = 0L
        runAsAdmin {
            ingredientId = client.createIngredient(
                IngredientDTO(name = mapOf(Locale.EN to "tomato"), type = IngredientType.VEGETABLE),
            ).id
        }
        runAsUser1 {
            val token = client.tokenFor(USER1)
            val username = client.callToolOk(token, "get_user")["user"]!!
                .jsonObject["username"]!!.jsonPrimitive.content

            val created = client.callToolOk(
                token,
                "create_recipe",
                buildJsonObject {
                    put("title", "Tomato soup")
                    put("description", "Warming")
                    put("dish_class", "ENTREE")
                    put("yield", 4)
                    putJsonArray("steps") {
                        add("chop")
                        add("simmer")
                    }
                    putJsonArray("ingredients") {
                        add(
                            buildJsonObject {
                                put("ingredient_id", ingredientId)
                                put("amount", 500)
                                put("unit", "GRAM")
                            },
                        )
                        add(buildJsonObject { put("custom_name", "basil") })
                    }
                },
            )

            val recipe = created["recipe"]!!.jsonObject
            val recipeId = recipe["id"]!!.jsonPrimitive.long
            assertEquals(username, recipe["owner"]!!.jsonObject["username"]!!.jsonPrimitive.content)
            // What the caller is told to do next: where the recipe opens, and that it is still
            // on the default picture.
            assertContains(created["url"]!!.jsonPrimitive.content, "id=$recipeId")
            assertFalse(created["hasImage"]!!.jsonPrimitive.boolean)

            // Read it back over the REST API: what the tool wrote has to be the same recipe the
            // app serves, not just what the tool chose to echo.
            val persisted = client.getRecipe(recipeId)
            assertEquals("Tomato soup", persisted.title)
            assertEquals(DishClass.ENTREE, persisted.dishClass)
            assertEquals(4, persisted.yield)
            assertEquals(listOf("chop", "simmer"), persisted.steps)
            assertEquals(listOf("tomato", "basil"), persisted.ingredients.map { it.name })
            assertEquals(500f, persisted.ingredients.first().amount)
            assertEquals(AmountUnit.GRAM, persisted.ingredients.first().unit)
        }
    }

    @Test
    fun `create_recipe rejects an ingredient line naming both a row and free text`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val outcome = client.callTool(
            token,
            "create_recipe",
            buildJsonObject {
                put("title", "Confused soup")
                putJsonArray("ingredients") {
                    add(
                        buildJsonObject {
                            put("ingredient_id", 1)
                            put("custom_name", "tomato")
                        },
                    )
                }
            },
        )
        assertTrue(outcome.isError)
        assertContains(outcome.text, "invalid_ingredient_row")
        // Rejected before anything was written, as on the REST API.
        assertTrue(client.callToolOk(token, "search_recipes")["results"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `update_recipe changes only what it is given`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(
            RecipeDTO(
                title = "Ratatouille",
                description = "Slowly",
                yield = 4,
                steps = mutableListOf("cut", "cook"),
                tips = "Serve warm",
                ingredients = mutableListOf(RecipeDTO.RecipeIngredientDTO(customName = "thyme")),
            ),
        )

        client.callToolOk(
            token,
            "update_recipe",
            buildJsonObject {
                put("recipe_id", recipe.id)
                put("title", "Ratatouille niçoise")
            },
        )

        val updated = client.getRecipe(recipe.id)
        assertEquals("Ratatouille niçoise", updated.title)
        // Everything the caller did not mention survived the edit, which a PUT would have blanked.
        assertEquals("Slowly", updated.description)
        assertEquals(4, updated.yield)
        assertEquals(listOf("cut", "cook"), updated.steps)
        assertEquals("Serve warm", updated.tips)
        assertEquals(listOf("thyme"), updated.ingredients.map { it.name })
    }

    @Test
    fun `update_recipe replaces the whole list when given ingredients or steps`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(
            RecipeDTO(
                title = "Ratatouille",
                steps = mutableListOf("cut", "cook"),
                ingredients = mutableListOf(
                    RecipeDTO.RecipeIngredientDTO(customName = "thyme"),
                    RecipeDTO.RecipeIngredientDTO(customName = "aubergine"),
                ),
            ),
        )

        client.callToolOk(
            token,
            "update_recipe",
            buildJsonObject {
                put("recipe_id", recipe.id)
                putJsonArray("steps") { add("cut") }
                putJsonArray("ingredients") { add(buildJsonObject { put("custom_name", "courgette") }) }
            },
        )

        val updated = client.getRecipe(recipe.id)
        assertEquals(listOf("cut"), updated.steps)
        assertEquals(listOf("courgette"), updated.ingredients.map { it.name })
    }

    @Test
    fun `update_recipe and delete_recipe refuse someone else's recipe`() = runTest {
        var recipeId = 0L
        runAsUser2 {
            recipeId = client.createRecipe(RecipeDTO(title = "Not yours")).id
        }
        runAsUser1 {
            val token = client.tokenFor(USER1)

            val update = client.callTool(
                token,
                "update_recipe",
                buildJsonObject {
                    put("recipe_id", recipeId)
                    put("title", "Mine now")
                },
            )
            assertTrue(update.isError)
            assertContains(update.text, "not_allowed_to_edit_recipe")

            val delete = client.callTool(token, "delete_recipe", buildJsonObject { put("recipe_id", recipeId) })
            assertTrue(delete.isError)
            assertContains(delete.text, "not_allowed_to_edit_recipe")
        }
        runAsUser2 {
            assertEquals("Not yours", client.getRecipe(recipeId).title)
        }
    }

    @Test
    fun `delete_recipe erases an unreferenced recipe`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))

        val result = client.callToolOk(token, "delete_recipe", buildJsonObject { put("recipe_id", recipe.id) })
        assertTrue(result["erased"]!!.jsonPrimitive.boolean)
        assertEquals(HttpStatusCode.NotFound, client.getRecipeRaw(recipe.id).status)
    }

    /** A recipe others have liked keeps its row, so their like still resolves. */
    @Test
    fun `delete_recipe withdraws a recipe someone else has liked`() = runTest {
        var recipeId = 0L
        runAsUser1 {
            recipeId = client.createRecipe(RecipeDTO(title = "Ratatouille")).id
        }
        runAsUser2 {
            client.createLike(recipeId)
        }
        runAsUser1 {
            val result = client.callToolOk(
                client.tokenFor(USER1),
                "delete_recipe",
                buildJsonObject { put("recipe_id", recipeId) },
            )
            assertFalse(result["erased"]!!.jsonPrimitive.boolean)
            assertContains(result["detail"]!!.jsonPrimitive.content, "Withdrawn")
        }
    }

    @Test
    fun `like_recipe says whether it changed anything, in both directions`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        val arguments = buildJsonObject { put("recipe_id", recipe.id) }

        val liked = client.callToolOk(token, "like_recipe", arguments)
        assertTrue(liked["changed"]!!.jsonPrimitive.boolean)
        assertTrue(liked["liked"]!!.jsonPrimitive.boolean)

        val again = client.callToolOk(token, "like_recipe", arguments)
        assertFalse(again["changed"]!!.jsonPrimitive.boolean, "liking twice should be a no-op: $again")
        assertEquals(1, client.getRecipe(recipe.id).likesCount)

        val unliked = client.callToolOk(
            token,
            "like_recipe",
            buildJsonObject {
                put("recipe_id", recipe.id)
                put("liked", false)
            },
        )
        assertTrue(unliked["changed"]!!.jsonPrimitive.boolean)
        assertEquals(0, client.getRecipe(recipe.id).likesCount)
    }

    /**
     * Taking a like back must not take the recipe with it. `RecipeService.tryDelete` does not check
     * whether the owner ever asked for the recipe to go, so calling it after every unlike — which
     * is what `DELETE /api/v1/like/{id}` does — erases a live recipe that had one like. The tool
     * checks the tag first; this is what says so.
     */
    @Test
    fun `unliking leaves the recipe alone`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        val arguments = buildJsonObject {
            put("recipe_id", recipe.id)
            put("liked", false)
        }

        client.callToolOk(token, "like_recipe", buildJsonObject { put("recipe_id", recipe.id) })
        client.callToolOk(token, "like_recipe", arguments)

        assertEquals("Ratatouille", client.getRecipe(recipe.id).title)
    }

    @Test
    fun `add_recipe_to_cookbook refuses a cookbook the caller is not a member of`() = runTest {
        var cookbookId = 0L
        runAsUser2 {
            cookbookId = client.createCookbook().id
        }
        runAsUser1 {
            val token = client.tokenFor(USER1)
            val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
            val outcome = client.callTool(
                token,
                "add_recipe_to_cookbook",
                buildJsonObject {
                    put("cookbook_id", cookbookId)
                    put("recipe_id", recipe.id)
                },
            )
            assertTrue(outcome.isError)
            assertContains(outcome.text, "not_member_of_cookbook")
        }
    }

    @Test
    fun `add_recipe_to_cookbook refuses a recipe that is already in it`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val cookbook = client.createCookbook()
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        val arguments = buildJsonObject {
            put("cookbook_id", cookbook.id)
            put("recipe_id", recipe.id)
        }

        client.callToolOk(token, "add_recipe_to_cookbook", arguments)
        val outcome = client.callTool(token, "add_recipe_to_cookbook", arguments)
        assertTrue(outcome.isError)
        assertContains(outcome.text, "recipe_already_in_cookbook")
    }

    // --------------------------------------------------------- the picture upload

    /**
     * The whole point of the ticket: a client that cannot put a photograph in a tool call posts
     * it to the URL the tool answered with, and the recipe is wearing it afterwards.
     */
    @Test
    fun `prepare_recipe_image_upload returns a URL that accepts one picture`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        assertEquals(0, recipe.version)

        val ticket = client.callToolOk(
            token,
            "prepare_recipe_image_upload",
            buildJsonObject { put("recipe_id", recipe.id) },
        )
        assertEquals(recipe.id, ticket["recipeId"]!!.jsonPrimitive.long)
        assertEquals("Ratatouille", ticket["title"]!!.jsonPrimitive.content)
        assertFalse(ticket["replacesExistingImage"]!!.jsonPrimitive.boolean)
        // The instructions are what the model acts on, so they have to name the URL it must post to.
        val uploadUrl = ticket["uploadUrl"]!!.jsonPrimitive.content
        assertContains(ticket["instructions"]!!.jsonPrimitive.content, uploadUrl)

        assertEquals(HttpStatusCode.OK, client.uploadToTicketUrl(uploadUrl).status)

        // The version bump is what every URL to the picture is built from.
        assertEquals(1, client.getRecipe(recipe.id).version)
        assertImageExists(RECIPES_IMG_PATH, recipe.id, 1)
        assertImageExists(RECIPES_THUMBNAIL_PATH, recipe.id, 1)
    }

    @Test
    fun `an upload URL is spent by the first post to it`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        val uploadUrl = client.callToolOk(
            token,
            "prepare_recipe_image_upload",
            buildJsonObject { put("recipe_id", recipe.id) },
        )["uploadUrl"]!!.jsonPrimitive.content

        assertEquals(HttpStatusCode.OK, client.uploadToTicketUrl(uploadUrl).status)

        client.uploadToTicketUrl(uploadUrl).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertContains(bodyAsText(), "invalid_upload_ticket")
        }
        // And the second post changed nothing.
        assertEquals(1, client.getRecipe(recipe.id).version)
    }

    @Test
    fun `a ticket says the recipe already has a picture when it does`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        client.uploadRecipeImage(recipe.id)

        val ticket = client.callToolOk(
            token,
            "prepare_recipe_image_upload",
            buildJsonObject { put("recipe_id", recipe.id) },
        )
        assertTrue(ticket["replacesExistingImage"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `prepare_recipe_image_upload refuses someone else's recipe`() = runTest {
        var recipeId = 0L
        runAsUser2 {
            recipeId = client.createRecipe(RecipeDTO(title = "Not yours")).id
        }
        runAsUser1 {
            val outcome = client.callTool(
                client.tokenFor(USER1),
                "prepare_recipe_image_upload",
                buildJsonObject { put("recipe_id", recipeId) },
            )
            assertTrue(outcome.isError)
            assertContains(outcome.text, "not_allowed_to_edit_recipe")
        }
    }

    /**
     * A ticket outlives the request that minted it, so what it was worth then is not what it is
     * worth when it is spent.
     */
    @Test
    fun `a ticket for a recipe that has since been deleted is refused`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        val uploadUrl = client.callToolOk(
            token,
            "prepare_recipe_image_upload",
            buildJsonObject { put("recipe_id", recipe.id) },
        )["uploadUrl"]!!.jsonPrimitive.content

        client.callToolOk(token, "delete_recipe", buildJsonObject { put("recipe_id", recipe.id) })

        client.uploadToTicketUrl(uploadUrl).apply {
            assertEquals(HttpStatusCode.NotFound, status)
            assertContains(bodyAsText(), "recipe_not_found")
        }
    }

    private fun assertImageExists(path: String, id: Long, version: Long) {
        val file = Path("$path/$id-v$version.webp")
        assertTrue(file.exists(), "expected an image at $file")
        assertTrue(file.fileSize() > 0, "expected $file not to be empty")
    }

    // ------------------------------------------------------------ bad arguments

    @Test
    fun `a missing required argument is reported by name`() = runTestAsUser {
        val outcome = client.callTool(client.tokenFor(USER1), "get_recipe")
        assertTrue(outcome.isError)
        assertContains(outcome.text, "recipe_id")
    }

    @Test
    fun `an argument the tool does not have is reported instead of ignored`() = runTestAsUser {
        val outcome = client.callTool(
            client.tokenFor(USER1),
            "search_recipes",
            buildJsonObject { put("querry", "tatin") },
        )
        assertTrue(outcome.isError)
        assertContains(outcome.text, "querry")
    }

    @Test
    fun `a value outside an enum is reported with the accepted ones`() = runTestAsUser {
        val outcome = client.callTool(
            client.tokenFor(USER1),
            "search_recipes",
            buildJsonObject { put("sort", "SIDEWAYS") },
        )
        assertTrue(outcome.isError)
        assertContains(outcome.text, "LIKES_DESCENDING")
    }

    @Test
    fun `a page size beyond the cap is refused`() = runTestAsUser {
        val outcome = client.callTool(
            client.tokenFor(USER1),
            "search_recipes",
            buildJsonObject { put("size", 500) },
        )
        assertTrue(outcome.isError)
        assertContains(outcome.text, "size")
    }

    /** Models quote numbers; refusing would cost a round trip to learn nothing. */
    @Test
    fun `a number sent as a string is accepted`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        val result = client.callToolOk(
            token,
            "get_recipe",
            buildJsonObject { put("recipe_id", recipe.id.toString()) },
        )
        assertEquals("Ratatouille", result["title"]!!.jsonPrimitive.content)
    }
}
