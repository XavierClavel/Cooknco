package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.data.ScannedIngredient
import com.xavierclavel.cooknco.data.ScannedRecipe
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
import androidx.lifecycle.viewModelScope
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a scan is allowed to do to the editor.
 *
 * The rule under all of it is that a scan **adds**. It is a guess made from a photograph, on
 * top of a form somebody may have been typing into for half an hour, and the one thing it
 * must never be able to do is take something away — a mis-aimed scan has to be survivable by
 * deleting a few rows, not by remembering what was there.
 */
class RecipeEditScanTest {

    /** A store per test: DataStore refuses two instances over one file. */
    private val storeFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-scan-${Random.nextLong()}.preferences_pb"

    /**
     * One test, over a view model that is fully torn down before the test returns.
     *
     * Both halves of that matter and neither belongs in an `@AfterTest`. `viewModelScope`
     * runs on the main dispatcher, so it is pointed at *this test's* scheduler — and the
     * scope is then cancelled **and joined** before the dispatcher is put back, because the
     * view model loads the unit catalogue in its `init` and a coroutine resuming onto a main
     * dispatcher that has just been reset throws. Left to a teardown, that lands in whichever
     * test is running by then, including one in another file.
     *
     * [search] is handed the query and returns the catalogue rows to answer it with.
     */
    private fun scanTest(
        search: (String) -> List<String> = { emptyList() },
        body: suspend TestScope.(RecipeEditViewModel) -> Unit,
    ): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = viewModel(search)
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(storeFile, mustExist = false)
        }
    }

    /**
     * Waits for the catalogue match a prefill starts.
     *
     * A real wait on the state rather than advancing the scheduler: the match goes through
     * DataStore and an HTTP client, both of which do their work on dispatchers of their own,
     * so there is no amount of virtual time that makes it have happened.
     */
    private suspend fun RecipeEditViewModel.awaitScan() = uiState.first { !it.isScanning }

    // ── Nothing typed is lost ─────────────────────────────────────────────────

    @Test
    fun a_scan_fills_the_fields_that_are_still_empty() = scanTest { viewModel ->

        viewModel.prefillFromScan(
            ScannedRecipe(
                title = "Tarte aux pommes",
                description = "Un grand classique.",
                yield = 6,
                prepMinutes = 20,
                cookMinutes = 40,
                temperatureCelsius = 180,
                ingredients = listOf(ScannedIngredient("sucre", 100f, "GRAM")),
                steps = listOf("Éplucher les pommes."),
                tips = "Se mange tiède.",
            )
        )

        val state = viewModel.uiState.value
        assertEquals("Tarte aux pommes", state.title)
        assertEquals("Un grand classique.", state.description)
        assertEquals("6", state.yield)
        assertEquals("20", state.prepTime)
        assertEquals("40", state.cookTime)
        assertEquals("180", state.cookTemp)
        assertEquals("Se mange tiède.", state.tips)
        assertEquals(listOf("sucre"), state.ingredients.map { it.ingredientName })
        assertEquals(listOf("Éplucher les pommes."), state.steps.map { it.text })
    }

    @Test
    fun a_scan_never_overwrites_a_field_already_filled_in() = scanTest { viewModel ->
        viewModel.updateTitle("Ma tarte")
        viewModel.updateDescription("Celle de ma grand-mère")
        viewModel.updateYield("4")
        viewModel.updatePrepTime("15")
        viewModel.updateCookTime("35")
        viewModel.updateCookTemp("200")
        viewModel.updateTips("Servir avec de la crème")

        viewModel.prefillFromScan(
            ScannedRecipe(
                title = "Tarte aux pommes",
                description = "Un grand classique.",
                yield = 6,
                prepMinutes = 20,
                cookMinutes = 40,
                temperatureCelsius = 180,
                tips = "Se mange tiède.",
                steps = listOf("Éplucher les pommes."),
            )
        )

        val state = viewModel.uiState.value
        assertEquals("Ma tarte", state.title)
        assertEquals("Celle de ma grand-mère", state.description)
        assertEquals("4", state.yield)
        assertEquals("15", state.prepTime)
        assertEquals("35", state.cookTime)
        assertEquals("200", state.cookTemp)
        assertEquals("Servir avec de la crème", state.tips)
    }

    @Test
    fun a_scan_appends_to_the_ingredients_and_steps_already_there() = scanTest { viewModel ->
        viewModel.addIngredient()
        viewModel.updateIngredientQuery(0, "farine")
        viewModel.addStep()
        viewModel.updateStep(viewModel.uiState.value.steps.single().id, "Préchauffer le four.")

        viewModel.prefillFromScan(
            ScannedRecipe(
                ingredients = listOf(ScannedIngredient("sucre", 100f, "GRAM")),
                steps = listOf("Éplucher les pommes."),
            )
        )

        val state = viewModel.uiState.value
        assertEquals(listOf("farine", "sucre"), state.ingredients.map { it.ingredientName })
        assertEquals(listOf("Préchauffer le four.", "Éplucher les pommes."), state.steps.map { it.text })
    }

    /** Page two of the same recipe: the second scan adds to the first rather than replacing it. */
    @Test
    fun scanning_twice_keeps_both_pages() = scanTest { viewModel ->

        viewModel.prefillFromScan(
            ScannedRecipe(title = "Pot-au-feu", ingredients = listOf(ScannedIngredient("bœuf", 1f, "KILOGRAM")))
        )
        viewModel.prefillFromScan(
            ScannedRecipe(title = "Autre chose", steps = listOf("Saisir la viande."))
        )

        val state = viewModel.uiState.value
        assertEquals("Pot-au-feu", state.title, "the second page does not rename the recipe")
        assertEquals(listOf("bœuf"), state.ingredients.map { it.ingredientName })
        assertEquals(listOf("Saisir la viande."), state.steps.map { it.text })
    }

    // ── What lands in the rows ────────────────────────────────────────────────

    @Test
    fun an_ingredient_the_catalogue_does_not_hold_stays_free_text() = scanTest(search = { emptyList() }) { viewModel ->

        viewModel.prefillFromScan(
            ScannedRecipe(ingredients = listOf(ScannedIngredient("sucre vanillé", 2f, "UNIT", "en sachets")))
        )
        viewModel.awaitScan()

        val row = viewModel.uiState.value.ingredients.single()
        assertNull(row.ingredientId, "nothing matched, so nothing may be referenced")
        assertEquals("sucre vanillé", row.customName)
        assertEquals(2f, row.amount)
        assertEquals("UNIT", row.unit)
        assertEquals("en sachets", row.complement)
    }

    @Test
    fun an_ingredient_the_catalogue_holds_becomes_a_reference() = scanTest(search = { listOf(catalogue(id = 7, name = "Sucre")) }) { viewModel ->

        viewModel.prefillFromScan(
            ScannedRecipe(ingredients = listOf(ScannedIngredient("sucre", 100f, "GRAM")))
        )
        viewModel.awaitScan()

        val row = viewModel.uiState.value.ingredients.single()
        assertEquals(7L, row.ingredientId)
        assertNull(row.customName, "a referenced row carries no free-text name")
        assertEquals("Sucre", row.ingredientName)
        assertEquals("GRAM", row.unit, "the page said grams, so grams it stays")
        assertFalse(viewModel.uiState.value.isScanning)
    }

    @Test
    // The search is deliberately loose — it has to find "farine" from "fari" — so its best
    // answer to "sel" is a salt of some kind. Substituting one would read as correct and be
    // wrong in the nutrition, which is worse than leaving free text on the row.
    fun a_near_miss_from_the_fuzzy_search_is_not_taken() =
        scanTest(search = { listOf(catalogue(id = 9, name = "Sel de céleri")) }) { viewModel ->
        viewModel.prefillFromScan(ScannedRecipe(ingredients = listOf(ScannedIngredient("sel"))))
        viewModel.awaitScan()

        val row = viewModel.uiState.value.ingredients.single()
        assertNull(row.ingredientId)
        assertEquals("sel", row.customName)
    }

    @Test
    fun a_plural_on_the_page_still_matches_the_catalogue() = scanTest(search = { listOf(catalogue(id = 3, name = "Pomme")) }) { viewModel ->

        viewModel.prefillFromScan(ScannedRecipe(ingredients = listOf(ScannedIngredient("pommes", 6f, "UNIT"))))
        viewModel.awaitScan()

        assertEquals(3L, viewModel.uiState.value.ingredients.single().ingredientId)
    }

    @Test
    fun a_step_that_says_how_long_it_takes_arrives_with_its_timer() = scanTest { viewModel ->

        viewModel.prefillFromScan(
            ScannedRecipe(steps = listOf("Laisser reposer 30 min.", "Servir aussitôt."))
        )

        val steps = viewModel.uiState.value.steps
        assertEquals(30 * 60, steps[0].durationSeconds)
        assertTrue(StepAttachment.TIMER in steps[0].attachments)
        assertNull(steps[1].durationSeconds)
        assertTrue(steps[1].attachments.isEmpty())
        assertTrue(steps.none { it.durationTouched }, "a scanned step is no more decided than a typed one")
    }

    // ── When there is nothing to fill in ──────────────────────────────────────

    @Test
    fun a_page_with_no_recipe_on_it_changes_nothing_and_says_so() = scanTest { viewModel ->
        viewModel.updateTitle("Ma tarte")

        viewModel.prefillFromScan(ScannedRecipe())

        val state = viewModel.uiState.value
        assertEquals("Ma tarte", state.title)
        assertTrue(state.ingredients.isEmpty())
        assertTrue(state.steps.isEmpty())
        assertNotNull(state.scanMessage)
    }

    @Test
    fun a_scanner_that_cannot_be_reached_says_so_without_touching_the_form() = scanTest { viewModel ->
        viewModel.updateTitle("Ma tarte")

        viewModel.onScanned(com.xavierclavel.cooknco.platform.ScanResult.Failed)

        assertEquals("Ma tarte", viewModel.uiState.value.title)
        assertNotNull(viewModel.uiState.value.scanMessage)
    }

    @Test
    fun dismissing_the_message_clears_it() = scanTest { viewModel ->
        viewModel.prefillFromScan(ScannedRecipe())
        assertNotNull(viewModel.uiState.value.scanMessage)

        viewModel.dismissScanMessage()

        assertNull(viewModel.uiState.value.scanMessage)
    }

    // ── Plumbing ──────────────────────────────────────────────────────────────

    private fun catalogue(id: Long, name: String) =
        """{"id":$id,"name":{"EN":"$name","FR":"$name"},"type":"BAKERY","allowedTypes":["WEIGHT"]}"""

    /** A view model over a transport that answers the ingredient search and nothing else. */
    private fun viewModel(search: (String) -> List<String>): RecipeEditViewModel {
        val engine = MockEngine { request ->
            val query = request.url.parameters["query"].orEmpty()
            val items = search(query).joinToString(",")
            respond(
                content = """{"count":0,"page":0,"size":20,"items":[$items]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine)
        val api = RecipeApi(client)
        return RecipeEditViewModel(
            repo = RecipeRepository(api, TokenDataStore(createPreferencesDataStore(storeFile.toString())), testOfflineStore().first),
            unitRepo = UnitRepository(api),
            recipeId = null,
            userId = 1L,
        )
    }
}
