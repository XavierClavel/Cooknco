package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.IOException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * When a copy on this phone may answer for the server, and when it may not.
 *
 * Two conditions, and the second is the one that is easy to get wrong: a 404 is the server
 * saying the recipe is gone and a 403 is it saying this cook may not read it. Answering either
 * from the store would be showing somebody a recipe a moderator has hidden — so only a failure
 * that never reached a server falls back.
 */
class OfflineFallbackTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-auth-${Random.nextLong()}.preferences_pb"
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cooknco-offline-${Random.nextLong()}"
    private val store = OfflineStore(root)

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.delete(tokenFile, mustExist = false)
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }

    private val pinned = RecipeInfo(
        id = 1,
        version = 1,
        title = "Tarte Tatin",
        dishClass = "DESSERT",
        owner = RecipeOwner(id = 1, version = 0, username = "xavier"),
        creationDate = 0,
        likesCount = 3,
    )

    @Test
    fun a_pinned_recipe_is_answered_from_the_phone_when_there_is_no_server() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = pinned, notes = "35 min", isLiked = true))
        val repository = repositoryOver { throw IOException("offline") }

        val result = repository.getRecipe(1)

        assertEquals("Tarte Tatin", result.getOrNull()?.title)
        assertEquals("35 min", repository.getNotes(1).getOrNull(), "the notes come with it")
        assertEquals(true, repository.isLiked(1).getOrNull())
        assertTrue(OfflineState.isOffline.value, "and the app says where it came from")
    }

    @Test
    fun a_recipe_the_server_refuses_is_not_answered_from_the_phone() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = pinned))
        val repository = repositoryOver { respond("gone", HttpStatusCode.NotFound) }

        val result = repository.getRecipe(1)

        assertTrue(result.isFailure, "the server answered, and a copy must not overrule it")
        assertTrue(!OfflineState.isOffline.value, "there was a server")
    }

    @Test
    fun a_recipe_this_phone_never_pinned_still_fails_offline() = runTest {
        val repository = repositoryOver { throw IOException("offline") }

        assertTrue(repository.getRecipe(99).isFailure)
    }

    /**
     * "There are no notes" and "we hold no copy" are both null, and they are not the same
     * answer: a pinned recipe nobody has written on reads as having none, rather than as
     * unreachable.
     */
    @Test
    fun a_pinned_recipe_with_no_notes_says_so_rather_than_failing() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = pinned, notes = null))
        val repository = repositoryOver { throw IOException("offline") }

        val result = repository.getNotes(1)

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun a_recipe_fetched_from_the_server_updates_the_copy_without_losing_the_notes() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = pinned, notes = "35 min", isLiked = true))
        val repository = repositoryOver {
            respond(
                content = ApiClient.json.encodeToString(pinned.copy(title = "Tarte Tatin (fixed)")),
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(
                    io.ktor.http.HttpHeaders.ContentType,
                    io.ktor.http.ContentType.Application.Json.toString(),
                ),
            )
        }

        repository.getRecipe(1)

        val held = store.readRecipe(1)
        assertEquals("Tarte Tatin (fixed)", held?.recipe?.title, "written through, so it is there next time")
        assertEquals("35 min", held?.notes, "and the cook's own writing survived it")
    }

    /**
     * A signed-in repository over a mocked transport. The token matters: `isLiked` and
     * `getNotes` refuse without one, and a refusal is not the failure these tests are about.
     */
    private suspend fun repositoryOver(handler: MockRequestHandler): RecipeRepository {
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        val tokens = TokenDataStore(createPreferencesDataStore(tokenFile.toString()))
        tokens.saveToken("session-token")
        return RecipeRepository(RecipeApi(client), tokens, store)
    }
}
