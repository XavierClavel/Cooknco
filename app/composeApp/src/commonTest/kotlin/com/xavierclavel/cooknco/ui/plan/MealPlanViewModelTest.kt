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
import com.xavierclavel.cooknco.network.dto.RecipeOverview
import com.xavierclavel.cooknco.network.dto.RecipeOwner
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
    /** Days whose creation the server refuses, the others going through. */
    private var failingDates: Set<String> = emptySet()
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
                request.method == HttpMethod.Post && failingDates.any { text.contains("\"$it\"") } ->
                    Triple("down", HttpStatusCode.InternalServerError, headersOf())
                path.endsWith("/meal-plan/suggestions") -> json("[]")
                path.endsWith("/recipe") -> json("[]")
                path.endsWith("/meal-plan") && request.method == HttpMethod.Get -> json(ApiClient.json.encodeToString(week))
                path.endsWith("/meal-plan") && request.method == HttpMethod.Post -> {
                    val sent = ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(text)
                    json(ApiClient.json.encodeToString(
                        if (!sent.hasCooked) MealPlanEntry(id = nextId++, date = sent.date, slot = sent.slot, title = "", hasCooked = false)
                        else MealPlanEntry(id = nextId++, date = sent.date, slot = sent.slot, title = sent.title ?: "Recipe ${sent.recipeId}", servings = sent.servings)
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

    /** The weeks asked for, by their Monday, in the order they were asked. */
    private fun weeksAsked() = weekRequests().map { it.url.parameters["from"] }

    @Test
    fun the_tab_asks_for_this_week_from_monday_to_sunday() = planTest { viewModel ->
        week = listOf(MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup"))
        viewModel.load()
        val state = viewModel.awaitLoaded()

        val asked = weekRequests().single { it.url.parameters["from"] == "2026-10-12" }.url.parameters
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
        assertTrue(viewModel.uiState.value.days.all { it.meals.isEmpty() }, "nothing of last week while this one loads")
        val state = viewModel.awaitLoaded()

        assertTrue("2026-10-19" in weeksAsked())
        assertFalse(state.isCurrentWeek)
        viewModel.awaitIdle()
        val before = weeksAsked().size
        viewModel.thisWeek()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()
        assertTrue("2026-10-12" in weeksAsked().drop(before), "asked again, not only shown from what was known")
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
        assertEquals(MealPlanEntrySaveDto(date = "2026-10-14", slot = "DINNER", title = "Leftovers", servings = 1), sent)
        assertEquals(listOf("Leftovers"), state.days[2].meals.single { it.slot == MealSlot.DINNER }.dishes.map { it.title })
    }

    /**
     * Sent as one rather than left out: a recipe sent with no servings is put down by the
     * backend for its whole yield, which is a family's dinner for somebody cooking for one.
     */
    @Test
    fun everything_added_from_the_tab_is_for_one() = planTest { viewModel ->
        viewModel.load()
        val loaded = viewModel.awaitLoaded()
        val recipe = RecipeOverview(
            id = 5,
            version = 0,
            title = "Stew",
            owner = RecipeOwner(id = 7, version = 0, username = "xavier"),
            likesCount = 0,
            creationDate = 0,
        )

        viewModel.openAdd(loaded.days[0])
        viewModel.addRecipe(recipe)
        viewModel.uiState.first { it.adding == null }
        viewModel.openAdd(loaded.days[1])
        viewModel.addSuggestion("Pizza night")
        viewModel.uiState.first { it.adding == null }

        val sent = bodies.map { ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(it) }
        assertEquals(listOf(5L, null), sent.map { it.recipeId })
        assertEquals(listOf<Int?>(1, 1), sent.map { it.servings })
    }

    @Test
    fun the_calendar_shows_the_week_of_whatever_day_it_picked() = planTest { viewModel ->
        viewModel.load()
        viewModel.awaitLoaded()

        viewModel.showWeekOf(LocalDate(2026, 12, 25))
        val state = viewModel.awaitLoaded()

        assertEquals(LocalDate(2026, 12, 21), state.weekStart, "the Monday before Christmas")
        assertEquals("2026-12-27", weekRequests().last { it.url.parameters["from"] == "2026-12-21" }.url.parameters["to"])
        viewModel.awaitIdle()
        val asked = weekRequests().size
        viewModel.showWeekOf(LocalDate(2026, 12, 23))
        viewModel.awaitIdle()
        assertEquals(asked, weekRequests().size, "a day of the week on screen asks for nothing")
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
        viewModel.awaitIdle()

        viewModel.openEntry(words)
        viewModel.awaitIdle()
        viewModel.setEntrySlot(MealSlot.LUNCH)
        viewModel.saveEntry()
        viewModel.awaitIdle()
        viewModel.openEntry(recipe)
        viewModel.awaitIdle()
        viewModel.setEntryTitle("Renamed")
        viewModel.setEntryServings(LocalDate(2026, 10, 14), 3)
        viewModel.saveEntry()
        viewModel.awaitIdle()
        viewModel.openEntry(words)
        viewModel.awaitIdle()
        viewModel.setEntryTitle(" Leek soup ")
        viewModel.saveEntry()
        viewModel.awaitIdle()

        val sent = bodies.map { Json.parseToJsonElement(it).jsonObject }
        assertEquals(3, sent.size)
        assertNull(sent[0]["title"], "moved, not renamed")
        assertEquals("\"LUNCH\"", sent[0]["slot"].toString())
        assertNull(sent[1]["title"], "a recipe is not renamed from here")
        assertEquals("3", sent[1]["servings"].toString())
        assertEquals("\"Leek soup\"", sent[2]["title"].toString())
        assertNull(viewModel.uiState.value.editing)
    }

    @Test
    fun the_sheet_ticks_every_day_the_dish_is_served_at_its_meal() = planTest { viewModel ->
        val soup = MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup")
        week = listOf(
            soup,
            MealPlanEntry(id = 2, date = "2026-10-21", slot = "DINNER", title = "soup "),
            MealPlanEntry(id = 3, date = "2026-10-15", slot = "LUNCH", title = "Soup"),
            MealPlanEntry(id = 4, date = "2026-10-16", slot = "DINNER", title = "Pizza"),
        )
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openEntry(soup)
        viewModel.awaitIdle()

        assertEquals(setOf(LocalDate(2026, 10, 14), LocalDate(2026, 10, 21)), viewModel.uiState.value.editing!!.days)
        val asked = weekRequests().last().url.parameters
        assertEquals("2026-09-28", asked["from"], "the six weeks the calendar draws for October")
        assertEquals("2026-11-08", asked["to"])
    }

    @Test
    fun days_ticked_and_unticked_are_planned_and_taken_off() = planTest { viewModel ->
        val soup = MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup", servings = 2)
        week = listOf(soup, MealPlanEntry(id = 2, date = "2026-10-21", slot = "DINNER", title = "Soup", servings = 2))
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openEntry(soup)
        viewModel.awaitIdle()
        viewModel.toggleEntryDate(LocalDate(2026, 10, 21))
        viewModel.toggleEntryDate(LocalDate(2026, 11, 4))
        viewModel.saveEntry()
        viewModel.awaitIdle()

        assertTrue(requests.any { it.method == HttpMethod.Delete && it.url.encodedPath.endsWith("/meal-plan/2") })
        assertTrue(requests.none { it.method == HttpMethod.Put }, "a day left as it was is not sent")
        val created = ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(bodies.single())
        assertEquals(MealPlanEntrySaveDto(date = "2026-11-04", slot = "DINNER", title = "Soup", servings = 2), created)
        assertNull(viewModel.uiState.value.editing)
    }

    @Test
    fun a_removed_dish_leaves_every_day_it_was_on() = planTest { viewModel ->
        val soup = MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup")
        week = listOf(soup, MealPlanEntry(id = 2, date = "2026-10-16", slot = "DINNER", title = "Soup"))
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openEntry(soup)
        viewModel.awaitIdle()
        week = emptyList()
        viewModel.removeEntry()
        viewModel.awaitIdle()

        val deleted = requests.filter { it.method == HttpMethod.Delete }.map { it.url.encodedPath.substringAfterLast('/') }.toSet()
        assertEquals(setOf("1", "2"), deleted)
        val state = viewModel.uiState.first { it.editing == null && it.hasLoaded && !it.isLoading }
        assertTrue(state.days.all { it.meals.isEmpty() })
    }

    @Test
    fun a_meal_is_planned_on_every_day_picked_however_many_weeks_apart() = planTest { viewModel ->
        viewModel.load()
        viewModel.awaitLoaded()

        viewModel.openAddMeal()
        val opened = viewModel.uiState.value.adding!!
        assertTrue(opened.picksDays)
        assertTrue(opened.dates.isEmpty(), "no day is picked for the cook")

        viewModel.toggleAddDate(LocalDate(2026, 10, 16))
        viewModel.toggleAddDate(LocalDate(2026, 10, 23))
        viewModel.toggleAddDate(LocalDate(2026, 11, 6))
        viewModel.toggleAddDate(today)
        viewModel.toggleAddDate(today)
        viewModel.setAddSlot(MealSlot.DINNER)
        viewModel.addSuggestion("Pizza night")
        val state = viewModel.uiState.first { it.adding == null }

        val sent = bodies.map { ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(it) }
        assertEquals(listOf("2026-10-16", "2026-10-23", "2026-11-06"), sent.map { it.date }.sorted())
        assertTrue(sent.all { it.slot == "DINNER" && it.title == "Pizza night" && it.servings == 1 })
        assertEquals(listOf("2026-10-16"), state.entries.map { it.date }, "only the week on screen is drawn")
    }

    @Test
    fun days_that_failed_stay_picked_and_the_others_stay_planned() = planTest { viewModel ->
        viewModel.load()
        viewModel.awaitLoaded()
        failingDates = setOf("2026-10-23")

        viewModel.openAddMeal()
        viewModel.toggleAddDate(today)
        viewModel.toggleAddDate(LocalDate(2026, 10, 23))
        viewModel.addSuggestion("Soup")
        val state = viewModel.uiState.first { it.adding?.saveFailed == true }

        assertEquals(setOf(LocalDate(2026, 10, 23)), state.adding!!.dates, "trying again plans only what is missing")
        assertFalse(state.adding!!.isSaving)
        assertEquals(listOf("2026-10-14"), state.entries.map { it.date })
    }

    @Test
    fun no_day_picked_plans_nothing() = planTest { viewModel ->
        viewModel.load()
        viewModel.awaitLoaded()

        viewModel.openAddMeal()
        viewModel.addSuggestion("Soup")
        viewModel.awaitIdle()

        assertTrue(bodies.isEmpty())
        assertTrue(viewModel.uiState.value.adding!!.dates.isEmpty())
    }

    @Test
    fun clearing_takes_every_picked_day_back_out() = planTest { viewModel ->
        viewModel.load()
        viewModel.awaitLoaded()

        viewModel.openAddMeal()
        viewModel.toggleAddDate(LocalDate(2026, 10, 15))
        viewModel.toggleAddDate(LocalDate(2026, 11, 3))
        viewModel.clearAddDates()

        assertTrue(viewModel.uiState.value.adding!!.dates.isEmpty())
    }

    @Test
    fun the_weeks_either_side_are_known_before_the_cook_swipes_to_them() = planTest { viewModel ->
        week = listOf(MealPlanEntry(id = 1, date = "2026-10-21", slot = "DINNER", title = "Soup"))
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        assertTrue("2026-10-05" in weeksAsked() && "2026-10-19" in weeksAsked())
        val next = LocalDate(2026, 10, 19)
        assertEquals(listOf("Soup"), viewModel.uiState.value.entriesOf(next)?.map { it.title })

        viewModel.nextWeek()
        val shown = viewModel.uiState.value
        assertTrue(shown.hasLoaded, "shown from what was asked ahead, with no spinner")
        assertEquals(listOf("Soup"), shown.days[2].meals.single().dishes.map { it.title })
        assertTrue(shown.entriesOf(LocalDate(2026, 10, 12)) != null, "the week left behind is still a swipe away")
    }

    @Test
    fun a_dish_planned_next_week_shows_there_before_the_swipe() = planTest { viewModel ->
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openAddMeal()
        viewModel.toggleAddDate(LocalDate(2026, 10, 21))
        week = listOf(MealPlanEntry(id = 9, date = "2026-10-21", slot = "DINNER", title = "Soup"))
        viewModel.addSuggestion("Soup")
        viewModel.uiState.first { it.adding == null }
        viewModel.awaitIdle()

        assertEquals(listOf("Soup"), viewModel.uiState.value.entriesOf(LocalDate(2026, 10, 19))?.map { it.title })
    }

    @Test
    fun each_day_keeps_its_own_servings() = planTest { viewModel ->
        val soup = MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup", servings = 2)
        week = listOf(soup, MealPlanEntry(id = 2, date = "2026-10-21", slot = "DINNER", title = "Soup", servings = 4))
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openEntry(soup)
        viewModel.awaitIdle()
        val opened = viewModel.uiState.value.editing!!
        assertEquals(4, opened.servingsOn(LocalDate(2026, 10, 21)), "each day shows what it was planned for")

        viewModel.setEntryServings(LocalDate(2026, 10, 14), 6)
        viewModel.toggleEntryDate(LocalDate(2026, 10, 28))
        viewModel.saveEntry()
        viewModel.awaitIdle()

        val put = requests.filter { it.method == HttpMethod.Put }.map { it.url.encodedPath.substringAfterLast('/') }
        assertEquals(listOf("1"), put, "the day whose count is unchanged is left alone")
        val sent = bodies.map { Json.parseToJsonElement(it).jsonObject }
        assertTrue(sent.any { it["date"].toString() == "\"2026-10-14\"" && it["servings"].toString() == "6" })
        assertTrue(sent.any { it["date"].toString() == "\"2026-10-28\"" && it["servings"].toString() == "6" }, "a new day starts at the count last set")
    }

    @Test
    fun the_calendar_knows_which_meals_are_already_planned() = planTest { viewModel ->
        week = listOf(
            MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "Soup"),
            MealPlanEntry(id = 2, date = "2026-10-15", slot = "LUNCH", title = "Salad"),
        )
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openAddMeal()
        viewModel.loadAddMonth(LocalDate(2026, 10, 1))
        viewModel.awaitIdle()

        val taken = viewModel.uiState.value.adding!!.taken
        assertEquals(setOf(MealSlot.DINNER), taken[LocalDate(2026, 10, 14)])
        assertEquals(setOf(MealSlot.LUNCH), taken[LocalDate(2026, 10, 15)])
    }

    @Test
    fun a_meal_not_cooked_is_planned_on_every_picked_day_and_fills_it() = planTest { viewModel ->
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openAddMeal()
        viewModel.setAddSlot(MealSlot.LUNCH)
        viewModel.toggleAddDate(LocalDate(2026, 10, 14))
        viewModel.toggleAddDate(LocalDate(2026, 10, 15))
        viewModel.addNotCooked()
        viewModel.awaitIdle()

        val sent = bodies.map { ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(it) }
        assertEquals(
            setOf(
                MealPlanEntrySaveDto(date = "2026-10-14", slot = "LUNCH", hasCooked = false),
                MealPlanEntrySaveDto(date = "2026-10-15", slot = "LUNCH", hasCooked = false),
            ),
            sent.toSet(),
            "no name and no count: nothing was cooked",
        )
        val state = viewModel.uiState.value
        assertNull(state.adding)
        val wednesday = state.days.single { it.date == LocalDate(2026, 10, 14) }
        assertTrue(wednesday.meals.single { it.slot == MealSlot.LUNCH }.dishes.single().hasCooked.not(), "the lunch counts as filled")
    }

    @Test
    fun a_meal_not_cooked_is_its_own_dish_across_days() = planTest { viewModel ->
        val out = MealPlanEntry(id = 1, date = "2026-10-14", slot = "DINNER", title = "", hasCooked = false)
        week = listOf(
            out,
            MealPlanEntry(id = 2, date = "2026-10-16", slot = "DINNER", title = "", hasCooked = false),
            MealPlanEntry(id = 3, date = "2026-10-17", slot = "DINNER", title = "Soup"),
        )
        viewModel.load()
        viewModel.awaitLoaded()
        viewModel.awaitIdle()

        viewModel.openEntry(out)
        viewModel.awaitIdle()
        assertEquals(setOf(LocalDate(2026, 10, 14), LocalDate(2026, 10, 16)), viewModel.uiState.value.editing!!.days)

        viewModel.toggleEntryDate(LocalDate(2026, 10, 21))
        viewModel.saveEntry()
        viewModel.awaitIdle()
        assertEquals(
            MealPlanEntrySaveDto(date = "2026-10-21", slot = "DINNER", hasCooked = false),
            ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(bodies.single()),
        )
    }
}
