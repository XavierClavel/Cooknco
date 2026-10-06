package com.xavierclavel.cooknco.ui.recipe

import androidx.lifecycle.viewModelScope
import com.xavierclavel.cooknco.data.AppLanguage
import com.xavierclavel.cooknco.data.RecipeRepository
import com.xavierclavel.cooknco.data.TokenDataStore
import com.xavierclavel.cooknco.data.UnitRepository
import com.xavierclavel.cooknco.data.createPreferencesDataStore
import com.xavierclavel.cooknco.data.testOfflineStore
import com.xavierclavel.cooknco.network.RecipeApi
import com.xavierclavel.cooknco.platform.CapturedPage
import com.xavierclavel.cooknco.platform.PhotoCaptureResult
import com.xavierclavel.cooknco.ui.i18n.stringsFor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What reading a photo with the backend's model does to the editor.
 *
 * The backend answers with the same thing a Cooklang import gets, so the landing rules are
 * `RecipeEditImportTest`'s and are not repeated here beyond the one that must never break —
 * nothing typed is lost. What is specific to the photo is the request and the failures: each
 * refusal the cook can act on differently has to reach them as a different sentence.
 */
class RecipeEditAiScanTest {

    /** A store per call, not per test: one test below runs the harness several times. */
    private fun newStoreFile() = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-ai-scan-${Random.nextLong()}.preferences_pb"

    private val tart = """
        {
          "recipe": {
            "title": "Tarte aux pommes",
            "description": "",
            "dishClass": "DESERT",
            "tips": "",
            "ingredients": [
              {"id": null, "customName": "pommes", "unit": "UNIT", "amount": 4.0, "complement": null}
            ],
            "steps": [{"text": "Enfourner", "durationSeconds": 2100, "ingredients": [{"index": 0, "amount": null}]}]
          },
          "ingredientNames": ["pommes"],
          "unmatchedIngredients": 0,
          "stepsWereSplit": false
        }
    """.trimIndent()

    private val page = CapturedPage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2))

    private val strings get() = stringsFor(AppLanguage.current.value)

    @Test
    fun a_scanned_page_fills_the_editor_and_says_so() = aiScanTest(tart) { viewModel, _ ->
        viewModel.scanWithAi(PhotoCaptureResult.Captured(listOf(page)))
        viewModel.awaitAiScan()

        val state = viewModel.uiState.value
        assertEquals("Tarte aux pommes", state.title)
        assertEquals(listOf("pommes"), state.ingredients.map { it.customName })
        assertEquals(2100, state.steps.single().durationSeconds)
        assertEquals(strings.aiScanDone, state.importMessage)
    }

    @Test
    fun a_scanned_page_never_overwrites_what_was_typed() = aiScanTest(tart) { viewModel, _ ->
        viewModel.updateTitle("Ma tarte")
        viewModel.addStep()
        viewModel.updateStep(viewModel.uiState.value.steps.first().id, "Préchauffer")

        viewModel.scanWithAi(PhotoCaptureResult.Captured(listOf(page)))
        viewModel.awaitAiScan()

        val state = viewModel.uiState.value
        assertEquals("Ma tarte", state.title)
        assertEquals(listOf("Préchauffer", "Enfourner"), state.steps.map { it.text })
    }

    @Test
    fun every_page_is_posted_to_the_recipe_scan() = aiScanTest(tart) { viewModel, requests ->
        viewModel.scanWithAi(PhotoCaptureResult.Captured(listOf(page, page)))
        viewModel.awaitAiScan()

        val request = requests.single { it.url.encodedPath.endsWith("/recipe/scan") }
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertTrue(request.body.contentType.toString().startsWith("multipart/form-data"))
        assertEquals(2, countParts(request.body))
    }

    /** A capture that could not happen is the scanner's failure, and asks the backend nothing. */
    @Test
    fun a_failed_capture_says_so_and_sends_nothing() = aiScanTest(tart) { viewModel, requests ->
        viewModel.scanWithAi(PhotoCaptureResult.Failed)

        assertEquals(strings.scanFailed, viewModel.uiState.value.scanMessage)
        assertTrue(requests.none { it.url.encodedPath.endsWith("/recipe/scan") })
    }

    @Test
    fun each_refusal_the_cook_can_act_on_has_its_own_sentence() {
        expectMessage("recipe_scan_nothing_read", HttpStatusCode.BadRequest) { strings.aiScanNothingRead }
        expectMessage("ai_daily_limit", HttpStatusCode.TooManyRequests) { strings.aiScanDailyLimit }
        expectMessage("recipe_reader_not_configured", HttpStatusCode.ServiceUnavailable) { strings.aiScanUnavailable }
        expectMessage("recipe_reader_busy", HttpStatusCode.ServiceUnavailable) { strings.aiScanUnavailable }
        expectMessage("ai_budget_exhausted", HttpStatusCode.ServiceUnavailable) { strings.aiScanUnavailable }
        expectMessage("something_else", HttpStatusCode.InternalServerError) { strings.aiScanFailed }
    }

    private fun expectMessage(cause: String, status: HttpStatusCode, expected: () -> String) =
        aiScanTest(cause, status) { viewModel, _ ->
            viewModel.updateTitle("Ma tarte")
            viewModel.scanWithAi(PhotoCaptureResult.Captured(listOf(page)))
            viewModel.awaitAiScan()

            val state = viewModel.uiState.value
            assertEquals(expected(), state.importMessage, "for $cause")
            assertEquals("Ma tarte", state.title)
            assertTrue(state.ingredients.isEmpty())
            assertNull(state.error)
        }

    // ── Harness ───────────────────────────────────────────────────────────────

    /** `RecipeEditImportTest.importTest`, recording what was sent. */
    private fun aiScanTest(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        test: suspend TestScope.(RecipeEditViewModel, List<HttpRequestData>) -> Unit,
    ): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val storeFile = newStoreFile()
        val tokens = TokenDataStore(createPreferencesDataStore(storeFile.toString()))
        tokens.saveToken("test-token")
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            if (request.url.encodedPath.endsWith("/recipe/scan")) {
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        // HttpTimeout as in ApiClient: the request sets a timeout of its own, which Ktor
        // refuses on a client without the plugin.
        val api = RecipeApi(HttpClient(engine) { install(HttpTimeout) })
        val viewModel = RecipeEditViewModel(
            repo = RecipeRepository(api, tokens, testOfflineStore().first),
            unitRepo = UnitRepository(api),
            recipeId = null,
            userId = 1L,
        )
        try {
            test(viewModel, requests)
        } finally {
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
            Dispatchers.resetMain()
            FileSystem.SYSTEM.delete(storeFile, mustExist = false)
        }
    }

    private suspend fun RecipeEditViewModel.awaitAiScan() = uiState.first { !it.isScanningWithAi }

    /** How many pages a multipart body holds, read off its rendered bytes. */
    private suspend fun countParts(body: OutgoingContent): Int =
        Regex("filename=page\\d+\\.jpg").findAll(body.toByteArray().decodeToString()).count()
}
