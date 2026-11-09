package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.CookbookInfo
import com.xavierclavel.cooknco.network.dto.OfflineCookbookInfo
import com.xavierclavel.cooknco.network.dto.OfflineNotesInfo
import com.xavierclavel.cooknco.network.dto.OfflinePinInfo
import com.xavierclavel.cooknco.network.dto.OfflineSyncRequest
import com.xavierclavel.cooknco.network.dto.OfflineSyncResponse
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
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
 * What a sync leaves behind, over a mocked `POST /offline/sync`.
 *
 * The server decides what to hold and what to send; what is left here is keeping the index
 * honest about what is on disk. The one that matters is
 * [a_recipe_that_never_arrived_is_not_claimed]: an index naming a recipe whose file was never
 * written tells the server the phone holds it, so it is never sent again and the screen that
 * opens it finds nothing.
 */
class OfflineSyncTest {

    private companion object { const val TOKEN = "session-token"; const val USER = 7L }

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-auth-${Random.nextLong()}.preferences_pb"
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cooknco-offline-${Random.nextLong()}"
    private val store = OfflineStore(root)
    private val prefs by lazy { createPreferencesDataStore(tokenFile.toString()) }

    /** Every request body the mocked server received, in order. */
    private val requests = mutableListOf<OfflineSyncRequest>()

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.delete(tokenFile, mustExist = false)
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    private fun info(id: Long, revision: Long = 100) = RecipeInfo(
        id = id,
        version = 0,
        revision = revision,
        title = "Recipe $id",
        dishClass = "MAIN_DISH",
        owner = RecipeOwner(id = USER, version = 0, username = "xavier"),
        creationDate = 0,
        likesCount = 0,
    )

    private fun pin(id: Long, revision: Long = 100, sets: Set<String> = setOf("OWN"), isLiked: Boolean = false) =
        OfflinePinInfo(id = id, revision = revision, sets = sets, isLiked = isLiked)

    /** The answer a server gives a phone missing [missing] out of [pinned]. */
    private fun answer(
        pinned: List<OfflinePinInfo>,
        missing: List<RecipeInfo> = emptyList(),
        hasMore: Boolean = false,
        notes: Map<Long, String> = emptyMap(),
        cookbooks: List<OfflineCookbookInfo> = emptyList(),
    ) = OfflineSyncResponse(
        pinned = pinned,
        recipes = missing,
        hasMore = hasMore,
        notes = notes.map { (id, text) -> OfflineNotesInfo(id, text) },
        cookbooks = cookbooks,
    )

    @Test
    fun a_sync_writes_the_recipes_it_is_sent_and_indexes_them() = runTest {
        syncOver(answer(listOf(pin(1, sets = setOf("OWN", "COOKBOOK")), pin(2)), listOf(info(1), info(2))))

        assertEquals("Recipe 1", store.readRecipe(1)?.recipe?.title)
        assertEquals("Recipe 2", store.readRecipe(2)?.recipe?.title)
        val index = assertNotNull(store.readIndex())
        assertEquals(USER, index.userId)
        assertEquals(
            listOf(OfflineEntry(1, 100, setOf(PinSet.OWN, PinSet.COOKBOOK)), OfflineEntry(2, 100, setOf(PinSet.OWN))),
            index.recipes,
        )
        assertTrue(index.syncedAt > 0, "the banner prints this")
    }

    /** The whole point of the endpoint: a launch with nothing new is one request. */
    @Test
    fun a_launch_with_nothing_new_is_one_request_that_names_what_is_held() = runTest {
        syncOver(answer(listOf(pin(1), pin(2)), listOf(info(1), info(2))))
        requests.clear()

        syncOver(answer(listOf(pin(1), pin(2))))

        assertEquals(1, requests.size)
        assertEquals(mapOf(1L to 100L, 2L to 100L), requests.single().held.associate { it.id to it.revision })
    }

    /**
     * The regression this file exists for. The first page brings recipe 1 and the second
     * fails, so recipe 2 has no file — and an index naming it would tell the server, on every
     * later sync, that the phone already holds it.
     */
    @Test
    fun a_recipe_that_never_arrived_is_not_claimed() = runTest {
        syncOver(
            answer(listOf(pin(1), pin(2)), listOf(info(1)), hasMore = true),
            null,
        )

        assertNotNull(store.readRecipe(1))
        assertNull(store.readRecipe(2), "nothing was written for it")
        assertEquals(listOf(1L), assertNotNull(store.readIndex()).recipes.map { it.id })
    }

    /** And the next sync does not claim it either, which is what has the server send it. */
    @Test
    fun the_next_sync_asks_again_for_what_never_arrived() = runTest {
        syncOver(answer(listOf(pin(1), pin(2)), listOf(info(1)), hasMore = true), null)
        requests.clear()

        syncOver(answer(listOf(pin(1), pin(2)), listOf(info(2))))

        assertEquals(listOf(1L), requests.single().held.map { it.id })
        assertNotNull(store.readRecipe(2))
    }

    /** Each page asks only for the rest: what the previous one brought counts as held. */
    @Test
    fun a_second_page_counts_the_first_as_held() = runTest {
        syncOver(
            answer(listOf(pin(1), pin(2)), listOf(info(1)), hasMore = true),
            answer(listOf(pin(1), pin(2)), listOf(info(2))),
        )

        assertEquals(listOf(emptyList(), listOf(1L)), requests.map { request -> request.held.map { it.id } })
        assertEquals(listOf(1L, 2L), assertNotNull(store.readIndex()).recipes.map { it.id })
    }

    /** The revision recorded is the recipe's own, read with its content, not the pin's. */
    @Test
    fun a_copy_is_indexed_at_the_revision_it_was_read_at() = runTest {
        syncOver(answer(listOf(pin(1, revision = 100)), listOf(info(1, revision = 101))))

        assertEquals(101L, assertNotNull(store.readIndex()).recipes.single().revision)
    }

    @Test
    fun a_recipe_the_account_no_longer_keeps_is_deleted() = runTest {
        syncOver(answer(listOf(pin(1), pin(2)), listOf(info(1), info(2))))

        syncOver(answer(listOf(pin(1))))

        assertNotNull(store.readRecipe(1))
        assertNull(store.readRecipe(2))
        assertEquals(listOf(1L), assertNotNull(store.readIndex()).recipes.map { it.id })
    }

    /** Notes and likes change without the recipe's revision moving, so they ride every answer. */
    @Test
    fun notes_and_a_like_changed_elsewhere_reach_a_copy_that_is_otherwise_current() = runTest {
        syncOver(answer(listOf(pin(1)), listOf(info(1))))
        assertNull(store.readRecipe(1)?.notes)

        syncOver(answer(listOf(pin(1, isLiked = true)), notes = mapOf(1L to "less salt")))

        val copy = assertNotNull(store.readRecipe(1))
        assertEquals("less salt", copy.notes)
        assertTrue(copy.isLiked)
    }

    /** A copy from before revisions were indexed is sent as matching nothing, so it comes back whole. */
    @Test
    fun an_index_from_before_revisions_sends_its_copies_as_unknown() = runTest {
        store.writeRecipe(OfflineRecipe(info(1)))
        store.writeIndex(OfflineIndex(userId = USER, recipes = listOf(OfflineEntry(1, revision = null, sets = setOf(PinSet.OWN)))))

        syncOver(answer(listOf(pin(1)), listOf(info(1))))

        assertEquals(listOf(null), requests.single().held.map { it.revision })
        assertEquals(100L, assertNotNull(store.readIndex()).recipes.single().revision)
    }

    @Test
    fun a_collection_this_build_does_not_know_is_ignored_rather_than_failing_the_sync() = runTest {
        syncOver(answer(listOf(pin(1, sets = setOf("OWN", "SOMETHING_NEW"))), listOf(info(1))))

        assertEquals(setOf(PinSet.OWN), assertNotNull(store.readIndex()).recipes.single().sets)
    }

    @Test
    fun cookbooks_are_written_and_the_ones_left_are_forgotten() = runTest {
        fun book(id: Long) = OfflineCookbookInfo(
            cookbook = CookbookInfo(id = id, version = 0, title = "Book $id", recipesCount = 0, usersCount = 1, members = emptyList()),
        )
        syncOver(answer(emptyList(), cookbooks = listOf(book(10), book(11))))
        assertNotNull(store.readCookbook(11))

        syncOver(answer(emptyList(), cookbooks = listOf(book(10))))

        assertNotNull(store.readCookbook(10))
        assertNull(store.readCookbook(11))
        assertEquals(listOf(10L), assertNotNull(store.readIndex()).cookbookIds)
        assertEquals(listOf("Book 10"), assertNotNull(store.readLists()).cookbooks.map { it.title })
    }

    /**
     * A sync that reached nothing at all must not read an empty answer into it and wipe a
     * perfectly good copy.
     */
    @Test
    fun a_sync_with_no_network_leaves_the_store_alone() = runTest {
        syncOver(answer(listOf(pin(1)), listOf(info(1))))

        syncOver(offline = true)

        assertNotNull(store.readRecipe(1), "an unreachable server is not an empty account")
        assertEquals(listOf(1L), assertNotNull(store.readIndex()).recipes.map { it.id })
    }

    @Test
    fun turning_the_setting_off_stops_the_sync() = runTest {
        syncOver(answer(listOf(pin(1)), listOf(info(1))), enabled = false)

        assertEquals(0, requests.size, "nothing was asked for")
        assertNull(store.readIndex(), "and nothing was written")
    }

    /**
     * Syncs against a server answering [pages] in turn — a null page is a request that
     * fails — and records every request it is sent.
     */
    private suspend fun syncOver(
        vararg pages: OfflineSyncResponse?,
        offline: Boolean = false,
        enabled: Boolean = true,
    ) {
        var served = 0
        val engine = MockEngine { request ->
            if (offline) throw IOException("offline")
            if (!request.url.encodedPath.endsWith("/offline/sync")) return@MockEngine ok("[]")
            requests += ApiClient.json.decodeFromString<OfflineSyncRequest>(request.body.toByteArray().decodeToString())
            val page = pages.getOrNull(served++)
                ?: return@MockEngine respond("boom", HttpStatusCode.InternalServerError)
            ok(ApiClient.json.encodeToString(page))
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
            tokenDataStore = tokens,
            store = store,
            images = OfflineImages(store, client),
            preferences = preferences,
        ).sync(USER)
    }

    private fun MockRequestHandleScope.ok(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
