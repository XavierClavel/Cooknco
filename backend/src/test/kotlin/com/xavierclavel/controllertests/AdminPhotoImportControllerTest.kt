package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.getPhotoImportOverview
import main.com.xavierclavel.utils.getPhotoImportOverviewRaw
import main.com.xavierclavel.utils.importPhoto
import main.com.xavierclavel.utils.importPhotoRaw
import main.com.xavierclavel.utils.savePhotoImportSettings
import main.com.xavierclavel.utils.savePhotoImportSettingsRaw
import org.junit.jupiter.api.Test
import shared.enums.PhotoImportOutcome
import shared.infodto.PhotoImportSettingsDTO
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The backoffice photo-import tab: what it reports and what its settings do to the import.
 *
 * The settings are tested by their effect on `POST /recipe/import/photo` rather than by
 * reading them back, because a limit that saves and shows but does not bite is the failure
 * that costs money. [main.com.xavierclavel.utils.FakeRecipePhotoReader] bills 1 000 tokens in
 * and 200 out per import, which is what the cost assertions are worked out from.
 */
class AdminPhotoImportControllerTest : ApplicationTest() {

    private val toast = """{"title": "Toast", "ingredients": [{"name": "pain", "amount": 2}], "steps": [{"text": "Griller.", "uses": ["pain"]}]}"""

    /** €1 per million in, €2 per million out: 1 000 × 1 + 200 × 2 = 1 400 µ€ an import. */
    private val priced = PhotoImportSettingsDTO(
        dailyLimitPerUser = 30,
        monthlyBudget = null,
        inputPricePerMillion = 1.0,
        outputPricePerMillion = 2.0,
        currency = "EUR",
    )
    private val costOfOne = 0.0014

    private fun grantPremiumForever(mail: String) {
        userService.getEntityById(userService.findByMail(mail).id).grantPremiumForever().update()
    }

    // ----------------------------------------------------------- authorisation

    @Test
    fun `the tab is closed to anybody but an admin`() = runTest {
        client.getPhotoImportOverviewRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        runAsUser1 {
            client.getPhotoImportOverviewRaw().apply { assertFalse(status.value in 200..299) }
            client.savePhotoImportSettingsRaw(priced).apply { assertFalse(status.value in 200..299) }
        }
    }

    // ----------------------------------------------------------------- reading

    @Test
    fun `with nothing saved the defaults are shown, and say so`() = runTestAsAdmin {
        val overview = client.getPhotoImportOverview()
        assertFalse(overview.settingsSaved)
        assertEquals(configuration.photoImport.dailyReadsPerUser, overview.settings.dailyLimitPerUser)
        assertNull(overview.settings.monthlyBudget)
        // The test install has no provider, and the tab must not pretend otherwise.
        assertFalse(overview.provider.configured)
        assertEquals(30, overview.days.size)
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), overview.days.last().date)
        assertEquals(0, overview.month.imports)
    }

    @Test
    fun `an import is counted with its tokens, and costed at the prices in force`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = toast
        runAsAdmin { client.savePhotoImportSettings(priced) }
        runAsUser1 { client.importPhoto() }

        runAsAdmin {
            val overview = client.getPhotoImportOverview()
            assertEquals(1, overview.month.imports)
            assertEquals(1000L, overview.month.inputTokens)
            assertEquals(200L, overview.month.outputTokens)
            assertEquals(costOfOne, overview.month.cost, 1e-9)
            assertEquals(1, overview.today.imports)
            assertEquals(1, overview.days.last().imports)
            assertEquals(costOfOne, overview.days.last().cost, 1e-9)

            val heaviest = overview.topUsers.single()
            assertEquals(userService.findByMail(USER1).id, heaviest.userId)
            assertEquals(1, heaviest.importsToday)

            val entry = overview.recent.single()
            assertEquals(PhotoImportOutcome.READ, entry.outcome)
            assertEquals(1, entry.pages)
            assertEquals(heaviest.username, entry.username)
        }
    }

    /**
     * A month's spend is what was spent: a price corrected today must not rewrite it.
     */
    @Test
    fun `changing a price leaves what was already spent alone`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = toast
        runAsAdmin { client.savePhotoImportSettings(priced) }
        runAsUser1 { client.importPhoto() }
        runAsAdmin {
            client.savePhotoImportSettings(priced.copy(inputPricePerMillion = 100.0, outputPricePerMillion = 100.0))
            assertEquals(costOfOne, client.getPhotoImportOverview().month.cost, 1e-9)
        }
    }

    /** Every billed answer is in the spend, including the ones the cook got nothing from. */
    @Test
    fun `answers the cook got nothing from are counted too, with their outcome`() = runTest {
        grantPremiumForever(USER1)
        runAsAdmin { client.savePhotoImportSettings(priced) }
        runAsUser1 {
            fakePhotoReader.answer = """{"ingredients": [], "steps": []}"""
            client.importPhotoRaw().apply { assertEquals(HttpStatusCode.BadRequest, status) }
            fakePhotoReader.answer = "not json"
            client.importPhotoRaw().apply { assertEquals(HttpStatusCode.ServiceUnavailable, status) }
        }
        runAsAdmin {
            val overview = client.getPhotoImportOverview()
            assertEquals(2, overview.month.imports)
            assertEquals(2 * costOfOne, overview.month.cost, 1e-9)
            assertEquals(
                setOf(PhotoImportOutcome.NOTHING_READ, PhotoImportOutcome.FAILED),
                overview.recent.map { it.outcome }.toSet(),
            )
        }
    }

    /** An account's deletion forgets who spent it, and not that it was spent. */
    @Test
    fun `a deleted account's spend stays in the month, without its name`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = toast
        runAsAdmin { client.savePhotoImportSettings(priced) }
        runAsUser1 { client.importPhoto() }
        userService.deleteUserById(userService.findByMail(USER1).id)

        runAsAdmin {
            val overview = client.getPhotoImportOverview()
            assertEquals(costOfOne, overview.month.cost, 1e-9)
            assertNull(overview.topUsers.single().userId)
            assertNull(overview.recent.single().username)
        }
    }

    // ---------------------------------------------------------------- settings

    @Test
    fun `saved settings are shown back as saved`() = runTestAsAdmin {
        client.savePhotoImportSettings(priced.copy(monthlyBudget = 25.0, currency = "usd"))
        val overview = client.getPhotoImportOverview()
        assertTrue(overview.settingsSaved)
        assertEquals(25.0, overview.settings.monthlyBudget)
        // Upper-cased on the way in, so "usd" and "USD" are one currency.
        assertEquals("USD", overview.settings.currency)
    }

    @Test
    fun `each wrong field is refused with its own cause`() = runTestAsAdmin {
        mapOf(
            priced.copy(dailyLimitPerUser = -1) to "photo_import_limit_invalid",
            priced.copy(dailyLimitPerUser = 100_000) to "photo_import_limit_invalid",
            priced.copy(monthlyBudget = 0.0) to "photo_import_budget_invalid",
            priced.copy(inputPricePerMillion = -1.0) to "photo_import_price_invalid",
            priced.copy(currency = "euros") to "photo_import_currency_invalid",
        ).forEach { (settings, cause) ->
            client.savePhotoImportSettingsRaw(settings).apply {
                assertEquals(HttpStatusCode.BadRequest, status, cause)
                assertContains(bodyAsText(), cause)
            }
        }
        assertFalse(client.getPhotoImportOverview().settingsSaved)
    }

    /** The limit saved here is the one the import enforces, from the next import on. */
    @Test
    fun `the daily limit per account is the one saved in the backoffice`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = toast
        runAsAdmin { client.savePhotoImportSettings(priced.copy(dailyLimitPerUser = 1)) }
        runAsUser1 {
            client.importPhoto()
            client.importPhotoRaw().apply {
                assertEquals(HttpStatusCode.TooManyRequests, status)
                assertContains(bodyAsText(), "photo_import_daily_limit")
            }
        }
        assertEquals(1, fakePhotoReader.calls.size)
    }

    @Test
    fun `a daily limit of zero closes the import to everybody`() = runTest {
        grantPremiumForever(USER1)
        runAsAdmin { client.savePhotoImportSettings(priced.copy(dailyLimitPerUser = 0)) }
        runAsUser1 { client.importPhotoRaw().apply { assertEquals(HttpStatusCode.TooManyRequests, status) } }
        assertTrue(fakePhotoReader.calls.isEmpty())
    }

    /**
     * Once the month's spend reaches the budget, nobody's import reaches the model — and the
     * refusal is a 503, which the app reads as "unavailable" rather than blaming the cook.
     */
    @Test
    fun `past the monthly budget no import reaches the model`() = runTest {
        grantPremiumForever(USER1)
        grantPremiumForever(USER2)
        fakePhotoReader.answer = toast
        // One import spends 0.0014: a budget of 0.01 rounds to cents and holds seven of them.
        runAsAdmin { client.savePhotoImportSettings(priced.copy(monthlyBudget = 0.01)) }
        runAsUser1 { repeat(8) { client.importPhoto() } }
        runAsUser2 {
            client.importPhotoRaw().apply {
                assertEquals(HttpStatusCode.ServiceUnavailable, status)
                assertContains(bodyAsText(), "recipe_reader_budget_exhausted")
            }
        }
        assertEquals(8, fakePhotoReader.calls.size)
    }

    /** With no prices, nothing costs anything, so a budget can never be reached. */
    @Test
    fun `without prices a budget never bites`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = toast
        runAsAdmin { client.savePhotoImportSettings(priced.copy(inputPricePerMillion = 0.0, outputPricePerMillion = 0.0, monthlyBudget = 0.01)) }
        runAsUser1 { repeat(3) { client.importPhoto() } }
    }
}
