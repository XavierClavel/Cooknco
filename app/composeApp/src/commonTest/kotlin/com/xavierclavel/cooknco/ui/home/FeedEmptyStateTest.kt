package com.xavierclavel.cooknco.ui.home

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.deleteTestStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.RecipeApi
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When the feed says it has nothing, and what brings it back.
 *
 * The empty state is a claim about the cook's account — nothing of theirs, nothing from anybody
 * they follow — so it may only be made once the server has answered and answered with nothing.
 * And what it asks for happens on other screens, so coming back to it has to look again.
 */
class FeedEmptyStateTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-feed-${Random.nextLong()}.preferences_pb"

    /** What the server answers the feed with; tests change it between requests. */
    private var feed: List<RecipeOverview> = emptyList()
    private var failing = false

    /** The `page` of every feed request, in order. */
    private val pagesAsked = mutableListOf<String?>()

    private val recipe = RecipeOverview(
        id = 1L,
        version = 1L,
        title = "Harcha",
        owner = RecipeOwner(id = 2L, version = 1L, username = "aya"),
        likesCount = 0,
        creationDate = 0L,
    )

    /**
     * One test, over a view model torn down before it returns: its scope cancelled and joined
     * before the main dispatcher is put back, since the first page is requested from `init`
     * and a coroutine resuming onto a reset main dispatcher throws in whichever test is next.
     */
    private fun feedTest(body: suspend TestScope.(HomeViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (store, root) = testOfflineStore()
        val client = HttpClient(MockEngine { request ->
            pagesAsked += request.url.parameters["page"]
            if (failing) {
                respond("down", HttpStatusCode.InternalServerError)
            } else {
                respond(
                    content = ApiClient.json.encodeToString(feed),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        val tokens = TokenDataStore(createPreferencesDataStore(tokenFile.toString()))
        tokens.saveToken("session-token")
        val viewModel = HomeViewModel(RecipeRepository(RecipeApi(client), tokens, store), userId = 7L)
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
    private suspend fun HomeViewModel.awaitLoaded() = uiState.first { !it.isLoading && !it.isRefreshing }

    /**
     * Until whatever the view model launched has finished — the one way to wait for a request
     * that should not happen, since virtual time cannot see work on Ktor's own threads.
     */
    private suspend fun HomeViewModel.awaitIdle() =
        viewModelScope.coroutineContext.job.children.toList().forEach { it.join() }

    @Test
    fun a_feed_the_server_has_nothing_for_says_so_once_it_has_answered() = feedTest { viewModel ->
        assertFalse(viewModel.uiState.value.isEmpty, "not while the first page is on its way")

        val state = viewModel.awaitLoaded()

        assertTrue(state.isEmpty)
    }

    /** Pulling on an empty feed with no signal: the last answer was "nothing", this one is not. */
    @Test
    fun an_empty_feed_that_then_fails_to_load_shows_the_failure() = feedTest { viewModel ->
        assertTrue(viewModel.awaitLoaded().isEmpty)
        failing = true

        viewModel.refresh()
        val state = viewModel.awaitLoaded()

        assertTrue(state.error != null, "the failure is what the screen shows")
        assertFalse(state.isEmpty, "nothing has said the feed is empty, only that it could not be read")
    }

    @Test
    fun an_empty_feed_fills_in_when_it_comes_back_on_screen() = feedTest { viewModel ->
        viewModel.awaitLoaded()
        feed = listOf(recipe)

        viewModel.reloadIfEmpty()
        val state = viewModel.uiState.first { it.dateGroups.isNotEmpty() }

        assertFalse(state.isEmpty)
        assertFalse(state.isRefreshing, "quietly: the pull's spinner is for a pull")
        assertEquals(listOf(1L), state.dateGroups.flatMap { group -> group.recipes.map { it.id } })
    }

    @Test
    fun a_feed_with_recipes_in_it_is_not_asked_again_when_it_comes_back() = feedTest { viewModel ->
        feed = listOf(recipe)
        viewModel.refresh()
        viewModel.awaitLoaded()
        val asked = pagesAsked.size

        viewModel.reloadIfEmpty()
        viewModel.awaitIdle()

        assertEquals(asked, pagesAsked.size)
        assertEquals(1, viewModel.uiState.value.dateGroups.sumOf { it.recipes.size }, "and nothing doubled")
    }

    @Test
    fun the_page_after_what_a_reload_found_is_the_second() = feedTest { viewModel ->
        viewModel.awaitLoaded()
        feed = listOf(recipe)
        pagesAsked.clear()

        viewModel.reloadIfEmpty()
        viewModel.uiState.first { it.dateGroups.isNotEmpty() }
        feed = emptyList()
        viewModel.loadMore()
        viewModel.awaitLoaded()

        assertEquals(listOf<String?>("0", "1"), pagesAsked, "not the first page twice")
    }

    /**
     * A pull that comes back empty still marks the first page as read, so the reload has to
     * ask for it by number — and the page after what it found has to be the second.
     */
    @Test
    fun an_empty_pull_does_not_make_the_reload_skip_the_first_page() = feedTest { viewModel ->
        viewModel.awaitLoaded()
        viewModel.refresh()
        viewModel.awaitLoaded()
        feed = listOf(recipe)
        pagesAsked.clear()

        viewModel.reloadIfEmpty()
        viewModel.uiState.first { it.dateGroups.isNotEmpty() }
        feed = emptyList()
        viewModel.loadMore()
        viewModel.awaitLoaded()

        assertEquals(listOf<String?>("0", "1"), pagesAsked)
    }
}
