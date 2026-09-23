package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.network.RecipeApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a Cooklang import is allowed to do to the editor.
 *
 * The same rule a scan follows, and held here for the same reason: an import **adds**. It is
 * a file somebody picked, on top of a form they may have been typing into, and the one thing
 * it must never do is take something away — picking the wrong file has to be survivable by
 * deleting a few rows rather than by remembering what was there.
 *
 * What is *not* tested here is the parsing: that is the backend's, and `CooklangControllerTest`
 * drives it over a real round trip. This is only about what arrives in the form.
 */
class RecipeEditImportTest {

    /** A store per test: DataStore refuses two instances over one file. */
    private val storeFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-import-${Random.nextLong()}.preferences_pb"

    /** A parsed file as the backend answers with one. */
    private val pancakes = """
        {
          "recipe": {
            "title": "Pancakes",
            "description": "Light ones.",
            "dishClass": "DESERT",
            "yield": 4,
            "preparationTime": 10,
            "cookingTime": 15,
            "cookingTemperature": 180,
            "tips": "Rest the batter.",
            "ingredients": [
              {"id": 7, "customName": null, "unit": "GRAM", "amount": 125.0, "complement": null},
              {"id": null, "customName": "buttermilk", "unit": "MILLILITERS", "amount": 250.0, "complement": null}
            ],
            "steps": [
              {"text": "Whisk it all together", "durationSeconds": null, "ingredients": [{"index": 0, "amount": 125.0}]},
              {"text": "Fry each side", "durationSeconds": 120, "ingredients": []}
            ]
          },
          "ingredientNames": ["flour", "buttermilk"],
          "unmatchedIngredients": 1,
          "stepsWereSplit": false
        }
    """.trimIndent()

    // ── Nothing typed is lost ─────────────────────────────────────────────────

    @Test
    fun an_import_fills_the_fields_that_are_still_empty() = importTest(pancakes) { viewModel ->
        viewModel.importCooklang("ignored, the response is the fixture")
        viewModel.awaitImport()

        val state = viewModel.uiState.value
        assertEquals("Pancakes", state.title)
        assertEquals("Light ones.", state.description)
        assertEquals("4", state.yield)
        assertEquals("10", state.prepTime)
        assertEquals("15", state.cookTime)
        assertEquals("180", state.cookTemp)
        assertEquals("Rest the batter.", state.tips)
        assertEquals(listOf("Whisk it all together", "Fry each side"), state.steps.map { it.text })
    }

    @Test
    fun an_import_never_overwrites_a_field_already_filled_in() = importTest(pancakes) { viewModel ->
        viewModel.updateTitle("My pancakes")
        viewModel.updateDescription("Grandma's")
        viewModel.updateYield("2")
        viewModel.updatePrepTime("5")
        viewModel.updateCookTime("8")
        viewModel.updateCookTemp("200")
        viewModel.updateTips("Serve hot")

        viewModel.importCooklang("x")
        viewModel.awaitImport()

        val state = viewModel.uiState.value
        assertEquals("My pancakes", state.title)
        assertEquals("Grandma's", state.description)
        assertEquals("2", state.yield)
        assertEquals("5", state.prepTime)
        assertEquals("8", state.cookTime)
        assertEquals("200", state.cookTemp)
        assertEquals("Serve hot", state.tips)
    }

    /** Two files, or a file on top of a half-written recipe: both append. */
    @Test
    fun an_import_appends_its_ingredients_and_steps() = importTest(pancakes) { viewModel ->
        viewModel.addIngredient()
        viewModel.addStep()
        viewModel.updateStep(viewModel.uiState.value.steps.first().id, "Something I typed")

        viewModel.importCooklang("x")
        viewModel.awaitImport()

        val state = viewModel.uiState.value
        assertEquals(3, state.ingredients.size)
        assertEquals("Something I typed", state.steps.first().text)
        assertEquals(3, state.steps.size)
    }

    // ── What the rows carry ───────────────────────────────────────────────────

    /**
     * A row the catalogue placed keeps its id and carries no custom name; one it could not
     * place is the other way round. The save path refuses anything else, so this is the
     * shape that matters rather than a detail of the mapping.
     */
    @Test
    fun a_matched_row_carries_an_id_and_an_unmatched_one_carries_a_name() = importTest(pancakes) { viewModel ->
        viewModel.importCooklang("x")
        viewModel.awaitImport()

        val rows = viewModel.uiState.value.ingredients
        assertEquals(7L, rows[0].ingredientId)
        assertNull(rows[0].customName)
        assertEquals("flour", rows[0].ingredientName)
        assertEquals("GRAM", rows[0].unit)
        assertEquals(125f, rows[0].amount)

        assertNull(rows[1].ingredientId)
        assertEquals("buttermilk", rows[1].customName)
        assertEquals("MILLILITERS", rows[1].unit)
    }

    /** A timer the file stated is the file's, and is not re-read out of the wording. */
    @Test
    fun a_step_with_a_duration_arrives_with_its_timer_on() = importTest(pancakes) { viewModel ->
        viewModel.importCooklang("x")
        viewModel.awaitImport()

        val steps = viewModel.uiState.value.steps
        assertNull(steps[0].durationSeconds)
        assertEquals(120, steps[1].durationSeconds)
        assertTrue(steps[1].attachments.contains(StepAttachment.TIMER))
    }

    /** The one thing worth saying about an import that otherwise looks complete. */
    @Test
    fun the_ingredients_that_stayed_free_text_are_reported() = importTest(pancakes) { viewModel ->
        viewModel.importCooklang("x")
        viewModel.awaitImport()

        val message = viewModel.uiState.value.importMessage
        assertNotNull(message)
        assertTrue(message.contains("1"), "expected the count in: $message")
    }

    // ── Failure ───────────────────────────────────────────────────────────────

    /**
     * A file with no recipe in it says so, and — the part that matters — leaves the form
     * exactly as it was.
     */
    @Test
    fun a_file_with_no_recipe_in_it_says_so_and_changes_nothing() =
        importTest(body = """{"key":"cooklang_file_empty"}""", status = HttpStatusCode.BadRequest) { viewModel ->
            viewModel.updateTitle("My pancakes")

            viewModel.importCooklang("x")
            viewModel.awaitImport()

            val state = viewModel.uiState.value
            assertEquals("My pancakes", state.title)
            assertTrue(state.ingredients.isEmpty())
            assertNotNull(state.importMessage)
        }

    // ── Harness ───────────────────────────────────────────────────────────────

    /**
     * One test, over a view model that is fully torn down before the test returns — both
     * halves for the reasons `RecipeEditScanTest.scanTest` documents.
     *
     * The token is saved first: the repository refuses to call without one, so a store left
     * empty would make every one of these fail as "not authenticated" rather than for its
     * own reason.
     */
    private fun importTest(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        test: suspend TestScope.(RecipeEditViewModel) -> Unit,
    ): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val tokens = TokenDataStore(createPreferencesDataStore(storeFile.toString()))
        tokens.saveToken("test-token")
        val viewModel = viewModel(tokens, body, status)
        try {
            test(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(storeFile, mustExist = false)
        }
    }

    /**
     * A real wait on the state rather than advancing the scheduler: the import goes through
     * DataStore and an HTTP client, both of which work on dispatchers of their own, so there
     * is no amount of virtual time that makes it have happened.
     */
    private suspend fun RecipeEditViewModel.awaitImport() = uiState.first { !it.isImporting }

    private fun viewModel(
        tokens: TokenDataStore,
        body: String,
        status: HttpStatusCode,
    ): RecipeEditViewModel {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/recipe/import/cooklang")) {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                // The unit catalogue the view model loads in its `init`.
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val api = RecipeApi(HttpClient(engine))
        return RecipeEditViewModel(
            repo = RecipeRepository(api, tokens, testOfflineStore().first),
            unitRepo = UnitRepository(api),
            recipeId = null,
            userId = 1L,
        )
    }
}
