package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.ScannedIngredient
import com.xavierclavel.cooknco.data.ScannedRecipe
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.ApiClient
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.network.dto.RecipeSaveDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Moving through the editor's pages, and what the save sends once the cook gets to the end.
 *
 * The rule the pages follow is that **a problem is reported on the page it is fixed on**: Next
 * does not leave a page holding one, and PUBLISH sends the cook back to the first page that
 * does rather than reporting it from the last. [RecipeEditValidationTest] holds what counts as
 * a problem; this holds where the cook is when they hear about it.
 */
class RecipeEditPagesTest {

    /** A store per test: DataStore refuses two instances over one file. */
    private val storeFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-pages-${Random.nextLong()}.preferences_pb"

    /** What the view model posted to `/recipe`, in order. */
    private val saved = mutableListOf<RecipeSaveDto>()

    /**
     * One test, over a view model torn down before it returns — see `RecipeEditScanTest`,
     * whose harness this is, for why that cannot wait for a teardown.
     *
     * The save is answered with a refusal: what was sent is the thing under test, and a
     * refusal ends the save without the image uploads a success would go on to.
     */
    private fun pagesTest(body: suspend TestScope.(RecipeEditViewModel) -> Unit): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel()
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(storeFile, mustExist = false)
        }
    }

    private suspend fun RecipeEditViewModel.awaitSaveAttempt() = uiState.first { !it.isSaving && it.error != null }

    private suspend fun RecipeEditViewModel.awaitScan() = uiState.first { !it.isScanning }

    /** Adds a free-text ingredient through the search field, the way the cook does. */
    private fun RecipeEditViewModel.addCustom(name: String, unit: String = "NONE", amount: String? = null) {
        addIngredient()
        val index = uiState.value.ingredients.lastIndex
        updateIngredientQuery(index, name)
        selectCustomIngredient(index)
        updateIngredientUnit(index, unit)
        amount?.let { updateIngredientAmount(index, it) }
    }

    // ── Next ──────────────────────────────────────────────────────────────────

    @Test
    fun nothing_is_flagged_until_the_cook_tries_to_move_on() = pagesTest { viewModel ->
        val state = viewModel.uiState.value
        assertEquals(listOf(EditProblem.TitleMissing), state.problems())
        assertEquals(emptyList(), state.shownProblems())
    }

    @Test
    fun next_keeps_the_cook_on_a_page_with_a_problem_and_shows_it() = pagesTest { viewModel ->
        viewModel.next()

        val state = viewModel.uiState.value
        assertEquals(EditorStep.BASICS, state.page)
        assertEquals(listOf(EditProblem.TitleMissing), state.shownProblems())
        assertEquals(1, state.refusals)

        // Counted again, so the screen scrolls back to it after the cook scrolled away.
        viewModel.next()
        assertEquals(2, viewModel.uiState.value.refusals)
        assertEquals(EditorStep.BASICS, viewModel.uiState.value.page)
    }

    @Test
    fun fixing_the_field_clears_it_and_lets_the_cook_through() = pagesTest { viewModel ->
        viewModel.next()
        viewModel.updateTitle("Tarte aux pommes")
        assertEquals(emptyList(), viewModel.uiState.value.shownProblems())

        viewModel.next()

        val state = viewModel.uiState.value
        assertEquals(EditorStep.INGREDIENTS, state.page)
        assertEquals(emptySet(), state.checkedPages)
    }

    @Test
    fun a_problem_on_a_later_page_is_raised_there_and_not_before() = pagesTest { viewModel ->
        viewModel.updateTitle("Tarte aux pommes")
        viewModel.addCustom("farine", unit = "GRAM")

        viewModel.next()
        assertEquals(EditorStep.INGREDIENTS, viewModel.uiState.value.page)
        assertEquals(emptyList(), viewModel.uiState.value.shownProblems())

        viewModel.next()
        assertEquals(EditorStep.INGREDIENTS, viewModel.uiState.value.page)
        assertEquals(listOf(EditProblem.AmountMissing(0)), viewModel.uiState.value.shownProblems())
    }

    @Test
    fun back_is_never_refused() = pagesTest { viewModel ->
        viewModel.updateTitle("Tarte aux pommes")
        viewModel.addCustom("farine", unit = "GRAM")
        viewModel.next()
        viewModel.next()

        viewModel.back()

        assertEquals(EditorStep.BASICS, viewModel.uiState.value.page)
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    @Test
    fun publish_sends_the_cook_back_to_the_page_holding_the_problem_and_sends_nothing() = pagesTest { viewModel ->
        viewModel.updateTitle("Tarte aux pommes")
        repeat(3) { viewModel.next() }
        assertEquals(EditorStep.PHOTO, viewModel.uiState.value.page)

        // Added from the last page, as a scan or an import can.
        viewModel.addCustom("farine", unit = "GRAM")
        viewModel.next()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(EditorStep.INGREDIENTS, state.page)
        assertEquals(listOf(EditProblem.AmountMissing(0)), state.shownProblems())
        assertTrue(saved.isEmpty())
        assertNull(state.error)
    }

    // ── What is sent ──────────────────────────────────────────────────────────

    @Test
    fun a_step_points_at_its_ingredient_where_it_lands_in_what_is_sent() = pagesTest { viewModel ->
        viewModel.updateTitle("Tarte aux pommes")
        // The search row the ingredients page keeps open, which a scan appends after.
        viewModel.addIngredient()
        viewModel.prefillFromScan(
            ScannedRecipe(
                ingredients = listOf(ScannedIngredient("sucre", 100f, "GRAM"), ScannedIngredient("farine", 200f, "GRAM")),
                steps = listOf("Mélanger"),
            )
        )
        viewModel.awaitScan()
        val step = viewModel.uiState.value.steps.single().id
        viewModel.attachToStep(step, StepAttachment.INGREDIENTS)
        viewModel.toggleStepIngredient(step, 2)

        viewModel.save()
        viewModel.awaitSaveAttempt()

        val sent = saved.single()
        assertEquals(listOf("sucre", "farine"), sent.ingredients.map { it.customName })
        assertEquals(listOf(1), sent.steps.single().ingredients.map { it.index })
    }

    @Test
    fun removing_an_ingredient_moves_the_steps_that_use_the_ones_after_it() = pagesTest { viewModel ->
        viewModel.addCustom("sucre")
        viewModel.addCustom("farine")
        viewModel.addCustom("beurre")
        viewModel.addStep()
        val step = viewModel.uiState.value.steps.single().id
        viewModel.attachToStep(step, StepAttachment.INGREDIENTS)
        viewModel.toggleStepIngredient(step, 0)
        viewModel.toggleStepIngredient(step, 2)

        viewModel.removeIngredient(0)

        // The sugar is gone, and the butter is still the butter.
        assertEquals(listOf(1), viewModel.uiState.value.steps.single().ingredients.map { it.index })
        assertEquals("beurre", viewModel.uiState.value.ingredients[1].ingredientName)
    }

    @Test
    fun a_share_of_a_row_left_with_no_amount_is_not_sent() = pagesTest { viewModel ->
        viewModel.updateTitle("Tarte aux pommes")
        viewModel.addCustom("sel", unit = "GRAM", amount = "5")
        viewModel.addStep()
        val step = viewModel.uiState.value.steps.single().id
        viewModel.updateStep(step, "Saler")
        viewModel.attachToStep(step, StepAttachment.INGREDIENTS)
        viewModel.toggleStepIngredient(step, 0)
        viewModel.updateStepIngredientAmount(step, 0, "5")
        // "To taste" after all: the box for the share goes, and so must what was in it.
        viewModel.updateIngredientUnit(0, "NONE")

        viewModel.save()
        viewModel.awaitSaveAttempt()

        val used = saved.single().steps.single().ingredients.single()
        assertEquals(0, used.index)
        assertNull(used.amount)
    }

    private suspend fun viewModel(): RecipeEditViewModel {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/recipe")) {
                saved += ApiClient.json.decodeFromString<RecipeSaveDto>(request.body.toByteArray().decodeToString())
                return@MockEngine respond("refused", HttpStatusCode.BadRequest)
            }
            respond(
                content = """{"count":0,"page":0,"size":20,"items":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(ApiClient.json) } }
        val api = RecipeApi(client)
        val tokens = TokenDataStore(createPreferencesDataStore(storeFile.toString()))
        tokens.saveToken("session-token")
        return RecipeEditViewModel(
            repo = RecipeRepository(api, tokens, testOfflineStore().first),
            unitRepo = UnitRepository(api),
            recipeId = null,
            userId = 1L,
        )
    }
}
