package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.plugins.RedisService
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.importPhoto
import main.com.xavierclavel.utils.importPhotoRaw
import main.com.xavierclavel.utils.testImageBytes
import org.junit.jupiter.api.Test
import org.koin.test.inject
import shared.dto.RECIPE_STEP_TEXT_MAX_LENGTH
import shared.enums.AmountUnit
import shared.enums.DishClass
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The premium photo import, with the model replaced by [main.com.xavierclavel.utils.FakeRecipePhotoReader].
 *
 * What is under test is everything around the model — who may call it, what reaches it, what
 * an answer is turned into, and what a bad answer costs the cook — because that is the part a
 * change of provider must leave untouched. The provider's own HTTP is
 * `OpenAiCompatiblePhotoReaderTest`'s.
 *
 * As with the Cooklang import, the result is **saved through the ordinary create route** at
 * least once: an import the editor could not then save would pass every other test here.
 */
class PhotoImportControllerTest : ApplicationTest() {

    private val redisService: RedisService by inject()

    private val tart = """
        {
          "title": "Tarte aux pommes",
          "description": "La tarte de grand-mère",
          "servings": 6,
          "prepMinutes": "20",
          "cookMinutes": 35,
          "ovenTemperatureCelsius": 180,
          "course": "DESERT",
          "tips": "Servir tiède",
          "ingredients": [
            { "name": "farine", "amount": 250, "unit": "g", "note": null },
            { "name": "beurre", "amount": "1/2", "unit": "cup", "note": "mou" },
            { "name": "pommes", "amount": 4, "unit": null, "note": "en lamelles" },
            { "name": "ail", "amount": 2, "unit": "gousses", "note": null }
          ],
          "steps": [
            { "text": "Mélanger la farine et le beurre.", "minutes": null, "uses": ["farine", "beurre"] },
            { "text": "Disposer les pommes et enfourner.", "minutes": 35, "uses": ["pommes", "sucre"] }
          ]
        }
    """.trimIndent()

    private fun grantPremiumForever(mail: String) {
        userService.getEntityById(userService.findByMail(mail).id).grantPremiumForever().update()
    }

    // ----------------------------------------------------------- authorisation

    /**
     * Refused before the body is read, and so before anything reaches the model: an unpaid
     * account must cost no tokens at all, not merely be told no afterwards.
     */
    @Test
    fun `the photo import is closed to an account with no subscription`() = runTest {
        runAsUser1 {
            client.importPhotoRaw().apply {
                assertEquals(HttpStatusCode.Forbidden, status)
                assertContains(bodyAsText(), "premium_required")
            }
        }
        assertTrue(fakePhotoReader.calls.isEmpty())
    }

    @Test
    fun `the photo import is closed to anonymous callers`() = runTest {
        client.importPhotoRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        assertTrue(fakePhotoReader.calls.isEmpty())
    }

    /** One rule for who premium applies to: see `UserInfo.isPremium`. */
    @Test
    fun `an admin imports without a grant`() = runTestAsAdmin {
        fakePhotoReader.answer = tart
        assertEquals("Tarte aux pommes", client.importPhoto().recipe.title)
    }

    // ------------------------------------------------------------ what is read

    @Test
    fun `a transcription becomes the recipe the editor opens on`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = tart
        runAsUser1 {
            val imported = client.importPhoto()
            val recipe = imported.recipe

            assertEquals("Tarte aux pommes", recipe.title)
            assertEquals("La tarte de grand-mère", recipe.description)
            assertEquals(6, recipe.yield)
            // A number the model wrote as a string still counts.
            assertEquals(20, recipe.preparationTime)
            assertEquals(35, recipe.cookingTime)
            assertEquals(180, recipe.cookingTemperature)
            assertEquals(DishClass.DESERT, recipe.dishClass)
            assertEquals("Servir tiède", recipe.tips)

            assertEquals(listOf("farine", "beurre", "pommes", "ail"), imported.ingredientNames)
            val (flour, butter, apples, garlic) = recipe.ingredients
            assertEquals(AmountUnit.GRAM to 250f, flour.unit to flour.amount)
            // "1/2" read as a fraction, and the note kept as the complement.
            assertEquals(AmountUnit.CUP to 0.5f, butter.unit to butter.amount)
            assertEquals("mou", butter.complement)
            // A plain count is a count, which is what the save path insists on.
            assertEquals(AmountUnit.UNIT to 4f, apples.unit to apples.amount)
            // A unit with no column keeps both halves: 2, and "gousses".
            assertEquals(2f, garlic.amount)
            assertContains(garlic.complement.orEmpty(), "gousses")
            // Nothing in an empty catalogue, so every row is free text — and said so.
            assertEquals(4, imported.unmatchedIngredients)

            assertEquals(2, recipe.steps.size)
            assertEquals("Mélanger la farine et le beurre.", recipe.steps[0].text)
            assertNull(recipe.steps[0].durationSeconds)
            assertEquals(35 * 60, recipe.steps[1].durationSeconds)
            // Linked by name, without restating an amount: the list holds the quantity.
            assertEquals(listOf(0, 1), recipe.steps[0].ingredients.map { it.index })
            assertTrue(recipe.steps[0].ingredients.all { it.amount == null })
            // "sucre" is not in the list, so it is dropped rather than invented as a row.
            assertEquals(listOf(2), recipe.steps[1].ingredients.map { it.index })
        }
    }

    /** The whole feature in one assertion: the editor can save what it was handed. */
    @Test
    fun `an imported recipe saves through the ordinary create route`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = tart
        runAsUser1 {
            val imported = client.importPhoto()
            val saved = client.createRecipe(imported.recipe)
            assertEquals("Tarte aux pommes", saved.title)
            assertEquals(4, saved.ingredients.size)
            assertEquals(listOf(0, 1), saved.steps[0].ingredients.map { it.index })
        }
    }

    @Test
    fun `every page reaches the model, in order, as the image it is`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = tart
        val first = testImageBytes(40, 30)
        val second = testImageBytes(30, 40)
        runAsUser1 { client.importPhoto(listOf(first, second)) }

        val pages = fakePhotoReader.calls.single()
        assertEquals(2, pages.size)
        assertTrue(pages[0].bytes.contentEquals(first))
        assertTrue(pages[1].bytes.contentEquals(second))
        assertTrue(pages.all { it.mediaType == "image/jpeg" })
    }

    /** `response_format` is asked for, not guaranteed: a fence around the object still reads. */
    @Test
    fun `an answer wrapped in a markdown fence is still read`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = "Here is the recipe:\n```json\n$tart\n```"
        runAsUser1 { assertEquals("Tarte aux pommes", client.importPhoto().recipe.title) }
    }

    /** A model has no 255-character bound; the import cuts the way a `.cook` import does. */
    @Test
    fun `a step longer than a step may be is split, and says so`() = runTest {
        grantPremiumForever(USER1)
        val sentence = "Remuer doucement la préparation pendant quelques minutes. "
        val long = sentence.repeat(RECIPE_STEP_TEXT_MAX_LENGTH / sentence.length + 2).trim()
        fakePhotoReader.answer = """{"title": "Long", "ingredients": [], "steps": [{"text": "$long", "uses": []}]}"""
        runAsUser1 {
            val imported = client.importPhoto()
            assertTrue(imported.stepsWereSplit)
            assertTrue(imported.recipe.steps.size > 1)
            assertTrue(imported.recipe.steps.all { it.text.length <= RECIPE_STEP_TEXT_MAX_LENGTH })
        }
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun `a photograph with no recipe on it is refused with a cause the cook can act on`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = """{"title": null, "ingredients": [], "steps": []}"""
        runAsUser1 {
            client.importPhotoRaw().apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertContains(bodyAsText(), "photo_import_nothing_read")
            }
        }
    }

    /**
     * An answer that is not the transcription is the provider failing, not the photo being
     * unreadable: a 503 to retry, and the import is not counted against the day.
     */
    @Test
    fun `an answer that is not the transcription is a provider failure, and is not counted`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = "I'm sorry, I can't help with that."
        val userId = userService.findByMail(USER1).id
        runAsUser1 {
            client.importPhotoRaw().apply {
                assertEquals(HttpStatusCode.ServiceUnavailable, status)
                assertContains(bodyAsText(), "recipe_reader_failed")
            }
        }
        assertEquals(1L, countSoFar(userId), "the failed import must have been refunded")
    }

    @Test
    fun `a provider that cannot be reached is a 503, and is not counted`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.failure = ServiceUnavailableException(ServiceUnavailableCause.RECIPE_READER_UNAVAILABLE)
        val userId = userService.findByMail(USER1).id
        runAsUser1 {
            client.importPhotoRaw().apply {
                assertEquals(HttpStatusCode.ServiceUnavailable, status)
                assertContains(bodyAsText(), "recipe_reader_unavailable")
            }
        }
        assertEquals(1L, countSoFar(userId))
    }

    @Test
    fun `a request with no photograph is refused`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            client.importPhotoRaw(pages = emptyList()).apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertContains(bodyAsText(), "photo_import_no_photo")
            }
        }
        assertTrue(fakePhotoReader.calls.isEmpty())
    }

    /** A provider is never sent something this backend has not recognised as a picture. */
    @Test
    fun `a file that is not an image never reaches the model`() = runTest {
        grantPremiumForever(USER1)
        runAsUser1 {
            client.importPhotoRaw(pages = listOf("not a picture at all".toByteArray())).apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertContains(bodyAsText(), "invalid_image")
            }
        }
        assertTrue(fakePhotoReader.calls.isEmpty())
    }

    @Test
    fun `more pages than one recipe has are refused`() = runTest {
        grantPremiumForever(USER1)
        val pages = List(configuration.photoImport.maxPhotos + 1) { testImageBytes() }
        runAsUser1 {
            client.importPhotoRaw(pages = pages).apply {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertContains(bodyAsText(), "photo_import_too_many_photos")
            }
        }
        assertTrue(fakePhotoReader.calls.isEmpty())
    }

    /**
     * A subscription buys a day's worth of imports, not an open tap on a paid model — and the
     * refusal past it is a 429, since nothing about the account is wrong.
     */
    @Test
    fun `past the daily allowance an import is refused without reaching the model`() = runTest {
        grantPremiumForever(USER1)
        fakePhotoReader.answer = tart
        val userId = userService.findByMail(USER1).id
        runBlocking { repeat(configuration.photoImport.dailyReadsPerUser) { redisService.countPhotoImport(userId, today()) } }
        runAsUser1 {
            client.importPhotoRaw().apply {
                assertEquals(HttpStatusCode.TooManyRequests, status)
                assertContains(bodyAsText(), "photo_import_daily_limit")
            }
        }
        assertTrue(fakePhotoReader.calls.isEmpty())
        // Refused attempts are given back, so hammering the button does not eat into tomorrow.
        assertEquals(configuration.photoImport.dailyReadsPerUser.toLong() + 1, countSoFar(userId))
    }

    @Test
    fun `the allowance is per account`() = runTest {
        grantPremiumForever(USER1)
        grantPremiumForever(USER2)
        fakePhotoReader.answer = tart
        val user1 = userService.findByMail(USER1).id
        runBlocking { repeat(configuration.photoImport.dailyReadsPerUser) { redisService.countPhotoImport(user1, today()) } }
        runAsUser2 { client.importPhoto() }
    }

    private fun today() = LocalDate.now(ZoneOffset.UTC).toString()

    /** What the counter holds, read by counting one more: the call itself is what is measured. */
    private fun countSoFar(userId: Long): Long = runBlocking { redisService.countPhotoImport(userId, today()) }
}
