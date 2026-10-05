package com.xavierclavel.cooknco.ui.cookbook

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.deleteTestStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.dto.CookbookInfo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the cookbooks tab says there are none, and what brings the first one in.
 *
 * Nothing on the tab creates a cookbook — its button leads to the editor, which saves and opens
 * the new cookbook — so the tab has to look again when it comes back, or the empty state outlives
 * the very thing it asked for.
 */
class CookbooksEmptyStateTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-cookbooks-${Random.nextLong()}.preferences_pb"

    /** What the server answers the list with; tests change it between requests. */
    private var cookbooks: List<CookbookInfo> = emptyList()
    private var failing = false
    private var requests = 0

    private val cookbook = CookbookInfo(
        id = 1L,
        version = 1L,
        title = "Sunday lunches",
        recipesCount = 0,
        usersCount = 1,
        members = listOf(RecipeOwner(id = 7L, version = 1L, username = "xavier")),
    )

    /** One test, over a view model torn down before it returns — see `FeedEmptyStateTest`. */
    private fun cookbooksTest(body: suspend TestScope.(CookbooksViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (store, root) = testOfflineStore()
        val client = HttpClient(MockEngine {
            requests++
            if (failing) {
                respond("down", HttpStatusCode.InternalServerError)
            } else {
                respond(
                    content = ApiClient.json.encodeToString(cookbooks),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        val tokens = TokenDataStore(createPreferencesDataStore(tokenFile.toString()))
        tokens.saveToken("session-token")
        val viewModel = CookbooksViewModel(CookbookRepository(CookbookApi(client), tokens, store), userId = 7L)
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(tokenFile, mustExist = false)
            deleteTestStore(root)
        }
    }

    /** A real wait: the request goes through DataStore and Ktor, on dispatchers of their own. */
    private suspend fun CookbooksViewModel.awaitLoaded() = uiState.first { it.hasLoaded && !it.isLoading }

    private suspend fun CookbooksViewModel.awaitIdle() =
        viewModelScope.coroutineContext.job.children.toList().forEach { it.join() }

    @Test
    fun a_cook_with_no_cookbooks_is_told_so_once_the_server_has_answered() = cookbooksTest { viewModel ->
        assertFalse(viewModel.uiState.value.isEmpty, "not before anything has been asked")

        assertTrue(viewModel.awaitLoaded().isEmpty)
    }

    @Test
    fun a_reload_that_fails_does_not_claim_there_are_none() = cookbooksTest { viewModel ->
        assertTrue(viewModel.awaitLoaded().isEmpty)
        failing = true

        viewModel.load()
        val state = viewModel.uiState.first { it.error != null }

        assertFalse(state.isEmpty, "nothing has said there are none, only that the list could not be read")
    }

    @Test
    fun the_first_cookbook_shows_up_when_the_tab_comes_back() = cookbooksTest { viewModel ->
        viewModel.awaitLoaded()
        cookbooks = listOf(cookbook)

        viewModel.load()
        // Up to where the request is out, which is where a spinner would have been raised.
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading, "quietly: the list stays put while it is asked again")
        val state = viewModel.uiState.first { it.cookbooks.isNotEmpty() }

        assertFalse(state.isEmpty)
        assertEquals(listOf(1L), state.cookbooks.map { it.id })
    }

    @Test
    fun arriving_on_the_tab_while_the_first_load_is_out_asks_once() = cookbooksTest { viewModel ->
        // What the screen does on arrival, while the load from `init` has not come back.
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        assertEquals(1, requests)
    }
}
