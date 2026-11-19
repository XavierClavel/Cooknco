package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.CookbookRepository
import com.xavierclavel.cooknco.data.ExportRepository
import com.xavierclavel.cooknco.data.MealPlanRepository
import com.xavierclavel.cooknco.data.MealSlot
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
import com.xavierclavel.cooknco.network.dto.MealPlanEntry
import com.xavierclavel.cooknco.network.dto.MealPlanEntrySaveDto
import com.xavierclavel.cooknco.ui.plan.MealEntryDraft
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import kotlinx.datetime.LocalDate
import okio.FileSystem
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Planning a recipe from its own page: the meals picked, and what a retry sends. */
@OptIn(ExperimentalAtomicApi::class)
class PlanRecipeTest {

    private val tokenFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-plan-recipe-${Random.nextLong()}.preferences_pb"

    private val recipeId = 12L

    /** Meals whose creation the server refuses, by slot name. */
    private var failingSlots: Set<String> = emptySet()

    /** What the meal plan was sent, appended from Ktor's worker threads. */
    private val sentLog = AtomicReference(emptyList<MealPlanEntrySaveDto>())
    private val sent: List<MealPlanEntrySaveDto> get() = sentLog.load()

    private fun planTest(body: suspend (RecipeViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val (store, root) = testOfflineStore()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/meal-plan")) {
                val dto = ApiClient.json.decodeFromString<MealPlanEntrySaveDto>(request.body.toByteArray().decodeToString())
                while (true) {
                    val before = sentLog.load()
                    if (sentLog.compareAndSet(before, before + dto)) break
                }
                if (dto.slot in failingSlots) {
                    respond("down", HttpStatusCode.InternalServerError)
                } else {
                    respond(
                        ApiClient.json.encodeToString(MealPlanEntry(id = sent.size.toLong(), date = dto.date, slot = dto.slot, title = "Stew")),
                        HttpStatusCode.Created,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
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
            recipeId = recipeId,
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

    private val draft = MealEntryDraft(
        date = LocalDate(2026, 10, 14),
        slots = setOf(MealSlot.LUNCH, MealSlot.DINNER),
        servings = 2,
        title = "Stew",
    )

    @Test
    fun the_recipe_is_planned_at_every_meal_picked() = planTest { viewModel ->
        viewModel.openPlanSheet()
        viewModel.planRecipe(draft)
        viewModel.awaitIdle()

        val expected = draft.slots.map { MealPlanEntrySaveDto(date = draft.date.toString(), slot = it.name, recipeId = recipeId, servings = draft.servings) }
        assertEquals(expected.toSet(), sent.toSet())
        assertEquals(expected.size, sent.size)
        assertNull(viewModel.uiState.value.planSheet)
    }

    @Test
    fun trying_again_plans_only_the_meals_that_failed() = planTest { viewModel ->
        failingSlots = setOf(MealSlot.DINNER.name)
        viewModel.openPlanSheet()
        viewModel.planRecipe(draft)
        viewModel.awaitIdle()
        assertTrue(viewModel.uiState.value.planSheet!!.saveFailed)

        failingSlots = emptySet()
        val before = sent.size
        viewModel.planRecipe(draft)
        viewModel.awaitIdle()

        assertEquals(listOf(MealSlot.DINNER.name), sent.drop(before).map { it.slot }, "lunch went through the first time")
        assertNull(viewModel.uiState.value.planSheet)
    }
}
