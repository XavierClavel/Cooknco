package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.ExportRepository
import com.xavierclavel.cooknco.data.MealPlanRepository
import com.xavierclavel.cooknco.data.OfflineStore
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.deleteTestStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.CookbookApi
import com.xavierclavel.cooknco.network.ExportApi
import com.xavierclavel.cooknco.network.MealPlanApi
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import com.xavierclavel.cooknco.platform.PickedImage
import com.xavierclavel.cooknco.data.UserRepository
import com.xavierclavel.cooknco.network.UserApi
import com.xavierclavel.cooknco.network.dto.CookbookRecipeInfo
import com.xavierclavel.cooknco.ui.cookbook.CookbookViewModel
import com.xavierclavel.cooknco.ui.user.UserProfileViewModel
import com.xavierclavel.cooknco.ui.home.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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

/**
 * What a screen read before a recipe was written shows once it comes back on screen.
 *
 * The case that found it: a picture given to a recipe from its editor. Saving went to a new
 * copy of the recipe screen, the one underneath still showed the recipe without its picture,
 * and the feed behind both showed the old picture — or, for a recipe just created, no recipe
 * at all — until it was pulled. Both are read once and kept, so coming back has to ask
 * whether anything was written in the meantime.
 */
class ReloadAfterWriteTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-reload-${Random.nextLong()}.preferences_pb"

    private val owner = RecipeOwner(id = 7L, version = 1L, username = "aya")

    /** The recipe as the server has it; tests move its picture's version and yield. */
    private var recipe = RecipeInfo(
        id = 12L,
        version = 1L,
        title = "Harcha",
        dishClass = "MAIN",
        owner = owner,
        yield = 4,
        creationDate = 0L,
        likesCount = 0,
    )

    /** What the feed holds besides [recipe] — where a newly created recipe appears. */
    private var others: List<RecipeOverview> = emptyList()

    private var uploadFails = false

    /** Every feed page and every recipe read, in order. */
    private val reads = mutableListOf<String>()

    private val feed: List<RecipeOverview>
        get() = others + RecipeOverview(
            id = recipe.id,
            version = recipe.version,
            title = recipe.title,
            owner = owner,
            likesCount = 0,
            creationDate = 0L,
        )

    private data class Subject<T : ViewModel>(val viewModel: T, val repo: RecipeRepository)

    /** What a view model is built over: one of each, as [com.xavierclavel.cooknco.di.AppGraph] holds one. */
    private class Graph(val repo: RecipeRepository, val client: HttpClient, val tokens: TokenDataStore, val store: OfflineStore)

    /**
     * One repository shared by the view model and the test, as the app shares one: the write
     * is counted where it is made and read where it is shown.
     */
    private fun <T : ViewModel> reloadTest(
        build: (Graph) -> T,
        body: suspend TestScope.(Subject<T>) -> Unit,
    ): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (store, root) = testOfflineStore()
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                request.method == HttpMethod.Post && path.endsWith("/recipes/${recipe.id}") ->
                    if (uploadFails) {
                        respond("refused", HttpStatusCode.InternalServerError)
                    } else {
                        recipe = recipe.copy(version = recipe.version + 1)
                        respond("", HttpStatusCode.OK)
                    }
                path.endsWith("/recipe") -> {
                    reads += "feed ${request.url.parameters["page"]}"
                    json(ApiClient.json.encodeToString(if (request.url.parameters["page"] == "0") feed else emptyList()))
                }
                path.endsWith("/cookbook/$COOKBOOK/recipes") -> {
                    reads += "cookbook"
                    json(ApiClient.json.encodeToString(listOf(
                        CookbookRecipeInfo(
                            id = recipe.id,
                            title = recipe.title,
                            version = recipe.version,
                            addedById = owner.id,
                            addedByUsername = owner.username,
                            additionDate = 0L,
                        ),
                    )))
                }
                request.method == HttpMethod.Get && path.endsWith("/recipe/${recipe.id}") -> {
                    reads += "recipe"
                    json(ApiClient.json.encodeToString(recipe))
                }
                // The like, the notes and the cookbooks: each fails quietly on its own screen.
                else -> respond("not here", HttpStatusCode.NotFound)
            }
        }) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        val tokens = TokenDataStore(createPreferencesDataStore(tokenFile.toString()))
        tokens.saveToken("session-token")
        val repo = RecipeRepository(RecipeApi(client), tokens, store)
        val viewModel = build(Graph(repo, client, tokens, store))
        try {
            body(Subject(viewModel, repo))
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(tokenFile, mustExist = false)
            deleteTestStore(root)
        }
    }

    private fun MockRequestHandleScope.json(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun feedTest(body: suspend TestScope.(Subject<HomeViewModel>) -> Unit) =
        reloadTest({ HomeViewModel(it.repo, userId = owner.id) }, body)

    private fun recipeTest(body: suspend TestScope.(Subject<RecipeViewModel>) -> Unit) =
        reloadTest({ graph ->
            RecipeViewModel(
                repo = graph.repo,
                cookbookRepo = CookbookRepository(CookbookApi(graph.client), graph.tokens, graph.store),
                exportRepo = ExportRepository(ExportApi(graph.client), graph.tokens),
                mealPlanRepo = MealPlanRepository(MealPlanApi(graph.client), graph.tokens),
                recipeId = recipe.id,
                currentUserId = owner.id,
            )
        }, body)

    private fun profileTest(body: suspend TestScope.(Subject<UserProfileViewModel>) -> Unit) =
        reloadTest({ graph ->
            UserProfileViewModel(
                UserRepository(UserApi(graph.client), graph.tokens, graph.store),
                recipeWrites = graph.repo.writes,
                profileUserId = owner.id,
                currentUserId = owner.id,
            )
        }, body)

    private fun cookbookTest(body: suspend TestScope.(Subject<CookbookViewModel>) -> Unit) =
        reloadTest({ graph ->
            CookbookViewModel(
                CookbookRepository(CookbookApi(graph.client), graph.tokens, graph.store),
                ExportRepository(ExportApi(graph.client), graph.tokens),
                recipeWrites = graph.repo.writes,
                cookbookId = COOKBOOK,
                currentUserId = owner.id,
            )
        }, body)

    private fun searchTest(body: suspend TestScope.(Subject<RecipesViewModel>) -> Unit) =
        reloadTest({ graph ->
            RecipesViewModel(
                RecipeApi(graph.client),
                graph.repo,
                CookbookRepository(CookbookApi(graph.client), graph.tokens, graph.store),
                graph.tokens,
            )
        }, body)

    /** A real wait: requests go through DataStore and Ktor, on dispatchers of their own. */
    private suspend fun ViewModel.awaitIdle() =
        viewModelScope.coroutineContext.job.children.toList().forEach { it.join() }

    private val HomeViewModel.versions
        get() = uiState.value.dateGroups.flatMap { group -> group.recipes.map { it.id to it.version } }

    private fun picture() = PickedImage(byteArrayOf(1, 2, 3), "image/jpeg")

    @Test
    fun the_feed_shows_a_new_picture_when_it_comes_back() = feedTest { (feed, repo) ->
        feed.awaitIdle()
        assertEquals(listOf(12L to 1L), feed.versions)

        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        feed.reloadIfStale()
        feed.awaitIdle()

        assertEquals(listOf(12L to 2L), feed.versions)
        assertFalse(feed.uiState.value.isRefreshing, "quietly: the pull's spinner is for a pull")
    }

    @Test
    fun the_feed_shows_a_recipe_created_while_it_was_away() = feedTest { (feed, repo) ->
        feed.awaitIdle()
        others = listOf(
            RecipeOverview(id = 13L, version = 1L, title = "Msemen", owner = owner, likesCount = 0, creationDate = 0L),
        )

        // Any write moves the count; the upload is the one this mock answers without a body.
        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        feed.reloadIfStale()
        feed.awaitIdle()

        assertEquals(listOf(13L, 12L), feed.versions.map { it.first })
    }

    @Test
    fun the_feed_asks_nothing_when_nothing_was_written() = feedTest { (feed, _) ->
        feed.awaitIdle()
        val asked = reads.size

        feed.reloadIfStale()
        feed.awaitIdle()

        assertEquals(asked, reads.size)
    }

    @Test
    fun a_write_that_failed_is_not_one() = feedTest { (feed, repo) ->
        feed.awaitIdle()
        uploadFails = true
        val asked = reads.size

        assertFalse(repo.uploadRecipeImage(recipe.id, picture()).isSuccess)
        feed.reloadIfStale()
        feed.awaitIdle()

        assertEquals(asked, reads.size)
    }

    /** Coming back twice after one write reads once: the second return has seen it. */
    @Test
    fun the_feed_reads_once_per_write() = feedTest { (feed, repo) ->
        feed.awaitIdle()
        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()

        feed.reloadIfStale()
        feed.awaitIdle()
        val asked = reads.size
        feed.reloadIfStale()
        feed.awaitIdle()

        assertEquals(asked, reads.size)
    }

    /**
     * The screen an edit pops back to. It is the same view model that was drawing the recipe
     * before the editor opened, so it has to read the recipe again or show it as it was.
     */
    @Test
    fun the_recipe_screen_shows_the_new_picture_when_the_edit_returns_to_it() = recipeTest { (screen, repo) ->
        screen.uiState.first { it.recipe != null }
        assertEquals(1L, screen.uiState.value.recipe?.version)

        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        screen.reloadIfStale()
        screen.uiState.first { it.recipe?.version == 2L }

        assertFalse(screen.uiState.value.isLoading, "no spinner over a recipe already on screen")
    }

    @Test
    fun the_portions_dialled_in_survive_a_reload_that_did_not_change_the_yield() = recipeTest { (screen, repo) ->
        screen.uiState.first { it.recipe != null }
        screen.setYield(8)

        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        screen.reloadIfStale()
        screen.uiState.first { it.recipe?.version == 2L }

        assertEquals(8, screen.uiState.value.selectedYield)
    }

    @Test
    fun a_new_yield_resets_the_portions_to_it() = recipeTest { (screen, repo) ->
        screen.uiState.first { it.recipe != null }
        screen.setYield(8)
        recipe = recipe.copy(yield = 6)

        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        screen.reloadIfStale()
        screen.uiState.first { it.recipe?.version == 2L }

        assertEquals(6, screen.uiState.value.selectedYield)
    }

    @Test
    fun the_recipe_screen_asks_nothing_when_nothing_was_written() = recipeTest { (screen, _) ->
        screen.uiState.first { it.recipe != null }
        screen.awaitIdle()
        val asked = reads.size

        screen.reloadIfStale()
        screen.awaitIdle()

        assertEquals(asked, reads.size)
    }

    @Test
    fun the_profile_shows_a_new_picture_when_it_comes_back() = profileTest { (profile, repo) ->
        profile.uiState.first { !it.isLoading }
        profile.awaitIdle()
        assertEquals(listOf(12L to 1L), profile.uiState.value.recipes.map { it.id to it.version })

        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        profile.reloadIfStale()
        profile.awaitIdle()

        assertEquals(listOf(12L to 2L), profile.uiState.value.recipes.map { it.id to it.version })
        assertFalse(profile.uiState.value.isLoading, "no spinner over a grid already on screen")
    }

    @Test
    fun the_profile_asks_nothing_when_nothing_was_written() = profileTest { (profile, _) ->
        profile.uiState.first { !it.isLoading }
        profile.awaitIdle()
        val asked = reads.size

        profile.reloadIfStale()
        profile.awaitIdle()

        assertEquals(asked, reads.size)
    }

    @Test
    fun a_cookbook_shows_a_new_picture_when_it_comes_back() = cookbookTest { (cookbook, repo) ->
        cookbook.uiState.first { it.recipes.isNotEmpty() }
        assertEquals(1L, cookbook.uiState.value.recipes.single().version)

        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        cookbook.reloadIfStale()
        cookbook.uiState.first { it.recipes.single().version == 2L }
    }

    /** The search list runs collectors for as long as it lives, so it is waited on by its state. */
    @Test
    fun the_search_list_shows_a_new_picture_when_it_comes_back() = searchTest { (search, repo) ->
        search.uiState.first { it.recipes.isNotEmpty() }
        assertEquals(1L, search.uiState.value.recipes.single().version)

        repo.uploadRecipeImage(recipe.id, picture()).getOrThrow()
        search.reloadIfStale()
        search.uiState.first { it.recipes.single().version == 2L }

        assertFalse(search.uiState.value.isLoading, "quietly: the paging spinner is for paging")
    }

    private companion object {
        const val COOKBOOK = 5L
    }
}
