package com.xavierclavel.cooknco.ui.plan

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.MealPlanRepository
import com.xavierclavel.cooknco.data.MealSlot
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.deleteTestStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.MealPlanApi
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.MealPlanEntrySaveDto
import com.xavierclavel.cooknco.network.dto.MealPlanRecipe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
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
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The meal plan tab, over a mock server: which week it asks for, what it sends when a dish is
 * added, moved or renamed, and what it shows when the server says no.
 */
class MealPlanViewModelTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-meal-plan-${Random.nextLong()}.preferences_pb"

    /** A Wednesday, so "this week" starts two days before it. */
    private val today = LocalDate(2026, 10, 14)

    /** What the server answers a week with; tests change it between requests. */
    private var week: List<MealPlanEntry> = emptyList()
    private var failingWrites = false
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()
    private var nextId = 100L

    private fun json(content: String) =
        Triple(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun planTest(body: suspend TestScope.(MealPlanViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (store, root) = testOfflineStore()
        val client = HttpClient(MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            val text = request.body.toByteArray().decodeToString()
            if (text.isNotEmpty()) bodies += text
            val (content, status, headers) = when {
                request.method != HttpMethod.Get && failingWrites -> Triple("down", HttpStatusCode.InternalServerError, headersOf())
                path.endsWith("/meal-plan/suggestions") -> json("[]")
                path.endsWith("/recipe") -> json("[]")
                path.endsWith("/meal-plan") && request.method == HttpMethod.Get -> json(ApiClient.json.encodeToString(week))
                path.endsWith("/meal-plan") && request.method == HttpMethod.Post -> {
                    val sent = ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(text)
                    json(ApiClient.json.encodeToString(
                        MealPlanEntry(id = nextId++, date = sent.date, slot = sent.slot, title = sent.title ?: "Recipe ${sent.recipeId}", servings = sent.servings)
                    ))
                }
                request.method == HttpMethod.Put -> {
                    val id = path.substringAfterLast('/').toLong()
                    val sent = Json.parseToJsonElement(text).jsonObject
                    val before = week.single { it.id == id }
                    json(ApiClient.json.encodeToString(
                        before.copy(
                            date = sent["date"].toString().trim('"'),
                            slot = sent["slot"].toString().trim('"'),
                            title = sent["title"]?.toString()?.trim('"') ?: before.title,
                        )
                    ))
                }
                request.method == HttpMethod.Delete -> Triple("", HttpStatusCode.OK, headersOf())
                else -> Triple("unexpected", HttpStatusCode.NotFound, headersOf())
            }
            respond(content, status, headers)
        }) {
            install(ContentNegotiation) { json(ApiClient.json) }
        }
        val tokens = TokenDataStore(createPreferencesDataStore(tokenFile.toString()))
        tokens.saveToken("session-token")
        val viewModel = MealPlanViewModel(
            mealPlanRepository = MealPlanRepository(MealPlanApi(client), tokens),
            recipeRepository = RecipeRepository(RecipeApi(client), tokens, store),
            userId = 7L,
            today = today,
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

    private suspend fun MealPlanViewModel.awaitLoaded() = uiState.first { it.hasLoaded && !it.isLoading }

    /** A real wait: requests go through DataStore and Ktor, on dispatchers of their own. */
    private suspend fun MealPlanViewModel.awaitIdle() =
        viewModelScope.coroutineContext.job.children.toList().forEach { it.join() }

    private fun weekRequests() = requests.filter { it.method == HttpMethod.Get && it.url.encodedPath.endsWith("/meal-plan") }

    @Test
    fun the_tab_asks_for_this_week_from_monday_to_sunday() = planTest { viewModel ->
        week = listOf(MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup"))
        viewModel.load()
        val state = viewModel.awaitLoaded()

        val asked = weekRequests().single().url.parameters
        assertEquals("2026-10-12", asked["from"])
        assertEquals("2026-10-18", asked["to"])
        assertEquals(listOf("Soup"), state.days[2].meals.single().dishes.map { it.title })
        assertTrue(state.isCurrentWeek)
    }

    @Test
    fun the_next_week_is_asked_for_and_the_last_one_is_not_left_on_screen() = planTest { viewModel ->
        week = listOf(MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup"))
        viewModel.load()
        viewModel.awaitLoaded()
        week = emptyList()

        viewModel.nextWeek()
        assertTrue(viewModel.uiState.value.entries.isEmpty(), "nothing of last week while this one loads")
        val state = viewModel.awaitLoaded()

        assertEquals("2026-10-19", weekRequests().last().url.parameters["from"])
        assertFalse(state.isCurrentWeek)
        viewModel.thisWeek()
        viewModel.awaitLoaded()
        assertEquals("2026-10-12", weekRequests().last().url.parameters["from"])
    }

    @Test
    fun a_typed_dish_is_planned_as_words_and_lands_on_its_day() = planTest { viewModel ->
        viewModel.load()
        val loaded = viewModel.awaitLoaded()
        viewModel.openAdd(loaded.days[2])
        assertEquals(MealSlot.LUNCH, viewModel.uiState.value.adding?.slot, "the first main meal with nothing in it")

        viewModel.setAddSlot(MealSlot.DINNER)
        viewModel.setQuery("  Leftovers ")
        viewModel.addTypedDish()
        val state = viewModel.uiState.first { it.adding == null }

        val sent = ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(bodies.single())
        assertEquals(MealPlanEntrySaveDto(date = "2026-10-14", slot = "DINNER", title = "Leftovers"), sent)
        assertEquals(listOf("Leftovers"), state.days[2].meals.single { it.slot == MealSlot.DINNER }.dishes.map { it.title })
    }

    @Test
    fun a_save_the_server_refuses_keeps_the_sheet_open_and_adds_nothing() = planTest { viewModel ->
        viewModel.load()
        val loaded = viewModel.awaitLoaded()
        failingWrites = true
        viewModel.openAdd(loaded.days[0])
        viewModel.addSuggestion("Pizza night")
        val state = viewModel.uiState.first { it.adding?.saveFailed == true }

        assertFalse(state.adding!!.isSaving)
        assertTrue(state.entries.isEmpty())
    }

    /**
     * The backend lets go of an out-of-reach recipe when a dish is renamed, so a save that only
     * moved the dish must not send a name — or moving it to Thursday would cut its link.
     */
    @Test
    fun a_name_is_only_sent_when_a_dish_shown_as_words_was_renamed() = planTest { viewModel ->
        val words = MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup", servings = 2)
        val recipe = MealPlanEntry(id = 2, date = "2026-10-14", slot = "LUNCH", title = "Stew", recipe = MealPlanRecipe(5, 1))
        week = listOf(words, recipe)
        viewModel.load()
        viewModel.awaitLoaded()

        fun draftOf(entry: MealPlanEntry, title: String = entry.title) =
            MealEntryDraft(date = LocalDate(2026, 10, 15), slot = MealSlot.DINNER, servings = entry.servings, title = title)

        viewModel.openEntry(words)
        viewModel.saveEntry(draftOf(words))
        viewModel.awaitIdle()
        viewModel.openEntry(recipe)
        viewModel.saveEntry(draftOf(recipe, title = "Renamed"))
        viewModel.awaitIdle()
        viewModel.openEntry(words)
        viewModel.saveEntry(draftOf(words, title = " Leek soup "))
        viewModel.awaitIdle()

        val sent = bodies.map { Json.parseToJsonElement(it).jsonObject }
        assertNull(sent[0]["title"], "moved, not renamed")
        assertNull(sent[1]["title"], "a recipe is not renamed from here")
        assertEquals("\"Leek soup\"", sent[2]["title"].toString())
        val state = viewModel.uiState.value
        assertNull(state.editing)
        val thursday = state.days[3]
        assertEquals(LocalDate(2026, 10, 15), thursday.date)
        assertEquals(listOf("Leek soup", "Stew"), thursday.meals.single().dishes.map { it.title })
    }

    @Test
    fun a_removed_dish_leaves_the_week() = planTest { viewModel ->
        val soup = MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup")
        week = listOf(soup)
        viewModel.load()
        viewModel.awaitLoaded()

        viewModel.openEntry(soup)
        viewModel.removeEntry()
        val state = viewModel.uiState.first { it.editing == null }

        assertTrue(requests.any { it.method == HttpMethod.Delete && it.url.encodedPath.endsWith("/meal-plan/1") })
        assertTrue(state.days.all { it.meals.isEmpty() })
    }
}
