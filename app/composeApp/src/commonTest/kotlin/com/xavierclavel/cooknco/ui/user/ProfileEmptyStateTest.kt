package com.xavierclavel.cooknco.ui.user

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.deleteTestStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.UserApi
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.network.dto.UserInfo
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
 * When a profile's grid says it has nothing, and what brings it back.
 *
 * The same two rules as the feed and the cookbooks: "no recipes yet" only once the server has
 * said so, never for a list that could not be fetched — and the recipe written or liked from the
 * empty state shows up when the profile comes back on screen.
 */
class ProfileEmptyStateTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-profile-${Random.nextLong()}.preferences_pb"

    /** What the server answers with; tests change them between requests. */
    private var own: List<RecipeOverview> = emptyList()
    private var liked: List<RecipeOverview> = emptyList()
    private var recipesFailing = false
    private var recipeRequests = 0

    private val recipe = RecipeOverview(
        id = 1L,
        version = 1L,
        title = "Harcha",
        owner = RecipeOwner(id = 7L, version = 1L, username = "xavier"),
        likesCount = 0,
        creationDate = 0L,
    )

    /** One test, over a view model torn down before it returns — see `FeedEmptyStateTest`. */
    private fun profileTest(body: suspend TestScope.(UserProfileViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (store, root) = testOfflineStore()
        val client = HttpClient(MockEngine { request ->
            val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                request.url.encodedPath.endsWith("/user/7") -> respond(
                    content = ApiClient.json.encodeToString(user(recipesCount = own.size)),
                    status = HttpStatusCode.OK,
                    headers = json,
                )
                request.url.encodedPath.endsWith("/recipe") -> {
                    recipeRequests++
                    if (recipesFailing) {
                        respond("down", HttpStatusCode.InternalServerError)
                    } else {
                        val page = if (request.url.parameters["likedBy"] != null) liked else own
                        respond(ApiClient.json.encodeToString(page), HttpStatusCode.OK, json)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        val tokens = TokenDataStore(createPreferencesDataStore(tokenFile.toString()))
        tokens.saveToken("session-token")
        val viewModel = UserProfileViewModel(
            UserRepository(UserApi(client), tokens, store),
            profileUserId = 7L,
            currentUserId = 7L,
        )
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(tokenFile, mustExist = false)
            deleteTestStore(root)
        }
    }

    private fun user(recipesCount: Int) = UserInfo(
        id = 7L, version = 1L, username = "xavier",
        role = "USER", joinDate = 0L, bio = "",
        recipesCount = recipesCount, likesCount = 0, cookbooksCount = 0,
        followersCount = 0, followsCount = 0,
    )

    /** A real wait: the requests go through DataStore and Ktor, on dispatchers of their own. */
    private suspend fun UserProfileViewModel.awaitLoaded() = uiState.first { !it.isLoading }

    private suspend fun UserProfileViewModel.awaitIdle() =
        viewModelScope.coroutineContext.job.children.toList().forEach { it.join() }

    @Test
    fun a_profile_with_no_recipes_says_so_once_the_server_has_answered() = profileTest { viewModel ->
        assertFalse(viewModel.uiState.value.isShownEmpty, "not while the profile is on its way")

        assertTrue(viewModel.awaitLoaded().isShownEmpty)
    }

    @Test
    fun recipes_that_could_not_be_fetched_are_not_called_none() = profileTest { viewModel ->
        recipesFailing = true

        val state = viewModel.awaitLoaded()

        assertTrue(state.user != null, "the profile itself loaded")
        assertFalse(state.isShownEmpty, "the grid failed, which is not the same as having nothing in it")
    }

    @Test
    fun the_first_recipe_shows_up_when_the_profile_comes_back() = profileTest { viewModel ->
        viewModel.awaitLoaded()
        own = listOf(recipe)

        viewModel.reloadIfEmpty()
        val state = viewModel.uiState.first { it.recipes.isNotEmpty() && it.user?.recipesCount == 1 }

        assertFalse(state.isShownEmpty)
        assertEquals(listOf(1L), state.recipes.map { it.id })
    }

    @Test
    fun a_first_like_shows_up_when_the_likes_come_back() = profileTest { viewModel ->
        own = listOf(recipe.copy(id = 2L))
        viewModel.awaitLoaded()
        viewModel.selectTab(ProfileTab.LIKED)
        assertTrue(viewModel.uiState.first { it.likedLoaded }.isShownEmpty, "the likes, not the recipes, are what is shown")
        liked = listOf(recipe)

        viewModel.reloadIfEmpty()
        val state = viewModel.uiState.first { it.liked.isNotEmpty() }

        assertFalse(state.isShownEmpty)
        assertEquals(listOf(1L), state.liked.map { it.id })
    }

    @Test
    fun a_profile_with_recipes_is_not_asked_again_when_it_comes_back() = profileTest { viewModel ->
        own = listOf(recipe)
        viewModel.awaitLoaded()
        val asked = recipeRequests

        viewModel.reloadIfEmpty()
        viewModel.awaitIdle()

        assertEquals(asked, recipeRequests)
        assertEquals(1, viewModel.uiState.value.recipes.size, "and nothing doubled")
    }
}
