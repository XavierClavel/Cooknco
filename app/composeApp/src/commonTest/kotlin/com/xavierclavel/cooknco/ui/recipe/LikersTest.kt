package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.ExportRepository
import com.xavierclavel.cooknco.data.LIKERS_PAGE_SIZE
import com.xavierclavel.cooknco.data.MealPlanRepository
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
import com.xavierclavel.cooknco.network.dto.LikeInfoDto
import com.xavierclavel.cooknco.network.dto.LikeUserDto
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
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "liked by" sheet: who liked a recipe, a page at a time. */
class LikersTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-likers-${Random.nextLong()}.preferences_pb"

    /** Every liker the server holds, newest first. */
    private var likers: List<LikeInfoDto> = emptyList()

    private val asked = mutableListOf<Pair<String?, String?>>()

    private fun liker(id: Long) = LikeInfoDto(LikeUserDto(id = id, username = "cook$id", version = 2), creationDate = 0L)

    private fun likersTest(body: suspend (RecipeViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (store, root) = testOfflineStore()
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/like")) {
                val page = request.url.parameters["page"]!!.toInt()
                val size = request.url.parameters["size"]!!.toInt()
                asked += request.url.parameters["recipe"] to request.url.parameters["page"]
                respond(
                    ApiClient.json.encodeToString(likers.drop(page * size).take(size)),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("not here", HttpStatusCode.NotFound)
            }
        }) { install(ContentNegotiation) { json(ApiClient.json) } }
        val tokens = TokenDataStore(createPreferencesDataStore(tokenFile.toString()))
        tokens.saveToken("session-token")
        val viewModel = RecipeViewModel(
            repo = RecipeRepository(RecipeApi(client), tokens, store),
            cookbookRepo = CookbookRepository(CookbookApi(client), tokens, store),
            exportRepo = ExportRepository(ExportApi(client), tokens),
            mealPlanRepo = MealPlanRepository(MealPlanApi(client), tokens),
            recipeId = 12L,
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

    /** A real wait: requests go through DataStore and Ktor, on dispatchers of their own. */
    private suspend fun RecipeViewModel.awaitIdle() {
        while (true) {
            val running = viewModelScope.coroutineContext.job.children.filter { it.isActive }.toList()
            if (running.isEmpty()) return
            running.forEach { it.join() }
        }
    }

    @Test
    fun the_sheet_pages_through_every_liker_and_stops_at_the_last() = likersTest { viewModel ->
        likers = (1L..LIKERS_PAGE_SIZE + 3L).map(::liker)

        viewModel.openLikers()
        viewModel.awaitIdle()
        assertEquals(LIKERS_PAGE_SIZE, viewModel.uiState.value.likers!!.likers.size)

        viewModel.loadMoreLikers()
        viewModel.awaitIdle()
        val state = viewModel.uiState.value.likers!!
        assertEquals(likers.map { it.user.id }, state.likers.map { it.user.id })
        assertTrue(state.allLoaded)

        viewModel.loadMoreLikers()
        viewModel.awaitIdle()
        assertEquals(listOf("12" to "0", "12" to "1"), asked.filter { it.first != null }, "nothing asked past the last page")
    }

    @Test
    fun closing_the_sheet_forgets_it() = likersTest { viewModel ->
        likers = listOf(liker(1))
        viewModel.openLikers()
        viewModel.awaitIdle()
        viewModel.closeLikers()
        assertNull(viewModel.uiState.value.likers)
    }
}
