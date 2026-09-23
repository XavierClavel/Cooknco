package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a sync leaves behind, over a mocked transport.
 *
 * The one that matters is [a_recipe_that_could_not_be_fetched_is_not_claimed]: the plan says
 * what *should* be held and only the files say what *is*, and an index that confuses the two
 * is permanent — the entry matches its own edition date for ever after, so the recipe is never
 * asked for again.
 */
class OfflineSyncTest {

    private companion object { const val TOKEN = "session-token"; const val USER = 7L }

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-auth-${Random.nextLong()}.preferences_pb"
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cooknco-offline-${Random.nextLong()}"
    private val store = OfflineStore(root)
    private val prefs by lazy { createPreferencesDataStore(tokenFile.toString()) }

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.delete(tokenFile, mustExist = false)
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    private fun overview(id: Long, edited: Long = 100) = RecipeOverview(
        id = id,
        version = 0,
        title = "Recipe $id",
        owner = RecipeOwner(id = USER, version = 0, username = "xavier"),
        likesCount = 0,
        creationDate = 0,
        editionDate = edited,
    )

    private fun info(id: Long) = RecipeInfo(
        id = id,
        version = 0,
        title = "Recipe $id",
        dishClass = "MAIN_DISH",
        owner = RecipeOwner(id = USER, version = 0, username = "xavier"),
        creationDate = 0,
        likesCount = 0,
    )

    @Test
    fun a_sync_writes_the_recipes_the_lists_named() = runTest {
        syncOver(own = listOf(overview(1), overview(2)))

        assertEquals("Recipe 1", store.readRecipe(1)?.recipe?.title)
        assertEquals("Recipe 2", store.readRecipe(2)?.recipe?.title)
        val index = assertNotNull(store.readIndex())
        assertEquals(USER, index.userId)
        assertEquals(setOf(1L, 2L), index.recipes.map { it.id }.toSet())
        assertEquals(setOf(PinSet.OWN), index.recipes.first().sets)
        assertTrue(index.syncedAt > 0, "the banner prints this")
    }

    /**
     * The regression this file exists for. Recipe 2 cannot be fetched, so it has no file — and
     * an index naming it would match its own edition date on every later sync, which means the
     * recipe is never fetched again and the screen that opens it finds nothing.
     */
    @Test
    fun a_recipe_that_could_not_be_fetched_is_not_claimed() = runTest {
        syncOver(own = listOf(overview(1), overview(2)), failRecipes = setOf(2L))

        assertNotNull(store.readRecipe(1))
        assertNull(store.readRecipe(2), "nothing was written for it")
        assertEquals(
            listOf(1L),
            assertNotNull(store.readIndex()).recipes.map { it.id },
            "so the index must not say we hold it",
        )
    }

    /** And the next sync asks for it again, which is what makes the failure self-healing. */
    @Test
    fun the_next_sync_asks_again_for_what_failed() = runTest {
        syncOver(own = listOf(overview(1), overview(2)), failRecipes = setOf(2L))

        val fetched = mutableListOf<Long>()
        syncOver(own = listOf(overview(1), overview(2)), record = fetched)

        assertEquals(listOf(2L), fetched, "recipe 1 is already held and unchanged")
        assertNotNull(store.readRecipe(2))
    }

    /**
     * A sync that reached nothing at all must not read three empty lists as "they deleted
     * everything" and wipe a perfectly good copy.
     */
    @Test
    fun a_sync_with_no_network_leaves_the_store_alone() = runTest {
        syncOver(own = listOf(overview(1)))
        assertNotNull(store.readRecipe(1))

        syncOver(offline = true)

        assertNotNull(store.readRecipe(1), "an unreachable server is not an empty account")
        assertEquals(listOf(1L), assertNotNull(store.readIndex()).recipes.map { it.id })
    }

    @Test
    fun turning_the_setting_off_stops_the_sync() = runTest {
        syncOver(own = listOf(overview(1)), enabled = false)

        assertNull(store.readIndex(), "nothing was asked for and nothing was written")
    }

    private suspend fun syncOver(
        own: List<RecipeOverview> = emptyList(),
        failRecipes: Set<Long> = emptySet(),
        offline: Boolean = false,
        enabled: Boolean = true,
        record: MutableList<Long>? = null,
    ) {
        val engine = MockEngine { request ->
            if (offline) throw IOException("offline")
            val path = request.url.encodedPath
            val listed = request.url.parameters["user"] != null ||
                request.url.parameters["likedBy"] != null ||
                request.url.parameters["cookbookUser"] != null
            when {
                path.endsWith("/cookbook") -> ok("[]")
                // Only the OWN scope has anything; the other two answer empty.
                path.endsWith("/recipe") && request.url.parameters["user"] != null ->
                    ok(ApiClient.json.encodeToString(if (request.url.parameters["page"] == "0") own else emptyList()))
                path.endsWith("/recipe") && listed -> ok("[]")
                path.contains("/recipe-notes/") -> respond("", HttpStatusCode.NotFound)
                path.contains("/like/") -> ok("false")
                path.contains("/recipe/") -> {
                    val id = path.substringAfterLast('/').toLong()
                    record?.add(id)
                    if (id in failRecipes) respond("boom", HttpStatusCode.InternalServerError)
                    else ok(ApiClient.json.encodeToString(info(id)))
                }
                else -> ok("[]")
            }
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(ApiClient.json) } }
        // One DataStore, read through both faces of it: it refuses a second instance over the
        // same file, and these tests sync twice.
        val preferences = DevicePreferences(prefs)
        preferences.setOfflineRecipes(enabled)
        val tokens = TokenDataStore(prefs)
        tokens.saveToken(TOKEN)
        OfflineSync(
            recipeApi = RecipeApi(client),
            cookbookApi = CookbookApi(client),
            tokenDataStore = tokens,
            store = store,
            images = OfflineImages(store, client),
            preferences = preferences,
        ).sync(USER)
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.ok(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
