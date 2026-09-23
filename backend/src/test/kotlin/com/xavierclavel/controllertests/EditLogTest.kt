package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.logging.LogBuffer
import com.xavierclavel.plugins.RedisService
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.banUserRaw
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.editUser
import main.com.xavierclavel.utils.getMe
import main.com.xavierclavel.utils.recipeDTO
import main.com.xavierclavel.utils.sessionToken
import main.com.xavierclavel.utils.updateRecipeRaw
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import org.koin.test.inject
import shared.utils.URL.AUTH_URL
import shared.utils.URL.RECIPE_URL
import shared.utils.URL.USER_URL
import org.junit.jupiter.api.Test
import shared.infodto.RecipeInfo
import shared.dto.UserDTO
import shared.infodto.UserInfo
import shared.utils.URL.INGREDIENT_URL
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The edit trail: one line per write, on a logger of its own so the backoffice logs tab can
 * show nothing but the writes.
 *
 * What is asserted here is the part a reader depends on and a refactor can silently break —
 * that the line is on [EDIT_LOGGER] rather than the general one, that it names the caller who
 * made the write rather than whoever owns what was written, and that a refused or empty write
 * leaves nothing behind. The exact wording is deliberately not pinned: it is prose for an
 * operator, and a test that spelled it out would fail on every rephrasing.
 *
 * Every assertion is scoped by a string unique to the test rather than by clearing the buffer,
 * because [LogBuffer] is a process-wide global: another test class running alongside this one
 * is appending to it throughout.
 */
class EditLogTest : ApplicationTest() {

    private val redisService: RedisService by inject()

    /** As [com.xavierclavel.utils.logEdit] names it; the tab filters on a substring of this. */
    private val EDIT_LOGGER = "com.xavierclavel.EditActions"

    private fun editLines(search: String): List<String> =
        LogBuffer.query(logger = EDIT_LOGGER, search = search, limit = LogBuffer.MAX_PAGE_SIZE)
            .entries
            .map { it.message }

    /** A title no other test can have written, so the buffer can be read without clearing it. */
    private fun uniqueTitle() = "edit-log-${UUID.randomUUID()}"

    @Test
    fun `a write is logged, naming what was written and who wrote it`() = runTestAsUser {
        val title = uniqueTitle()
        val me = client.getMe()
        val recipe = client.createRecipe(recipeDTO.copy(title = title))

        val lines = editLines(title)
        assertEquals(1, lines.size, "expected exactly one edit line, got $lines")
        val line = lines.single()
        assertTrue(line.contains("Recipe ${recipe.id}"), line)
        assertTrue(line.contains(title), line)
        assertTrue(line.contains("by user ${me.id}"), line)
        assertTrue(line.contains(me.username), line)
    }

    @Test
    fun `edit lines sit on a logger of their own`() = runTestAsUser {
        val title = uniqueTitle()
        client.createRecipe(recipeDTO.copy(title = title))

        val entries = LogBuffer.query(search = title, limit = LogBuffer.MAX_PAGE_SIZE).entries
        val edits = entries.filter { it.logger == EDIT_LOGGER }
        assertEquals(1, edits.size, "expected one line on $EDIT_LOGGER, got ${entries.map { it.logger }}")
    }

    @Test
    fun `a moderator action names the moderator, not the account it was aimed at`() = runTest {
        var victim: UserInfo? = null
        runAsUser1 { victim = client.getMe() }

        runAsAdmin {
            val admin = client.getMe()
            client.banUserRaw(victim!!.id, reason = "spam").apply {
                assertEquals(HttpStatusCode.OK, status)
            }

            val line = editLines(victim!!.username).single { it.contains("banned") }
            // The subject is the banned account...
            assertTrue(line.contains("User ${victim!!.id}"), line)
            // ...and the actor is the operator who banned them, which is the whole point of
            // the trail: reading the owner off the row would name the victim as the author.
            assertTrue(line.contains("by user ${admin.id}"), line)
        }
    }

    @Test
    fun `a refused write leaves no line`() = runTest {
        val title = uniqueTitle()
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe(recipeDTO.copy(title = title)) }

        runAsUser2 {
            client.updateRecipeRaw(recipe!!.id, recipeDTO.copy(title = title)).apply {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
        }

        // The create is still there; nothing claims the edit that was refused.
        val lines = editLines(title)
        assertEquals(1, lines.size, "a refused edit was logged: $lines")
        assertTrue(lines.single().contains("created"), lines.single())
    }

    /**
     * The name beside the id is read off the session, so a rename is not visible to a session
     * that is already open — until [com.xavierclavel.plugins.RedisService.touchSession] next
     * slides that session's expiry, which is where the label catches up. The two tests below
     * are the two halves of that: not immediately, and then yes.
     *
     * Only the label lags. The id is on every line and is never stale, which is what makes the
     * lag affordable in the first place.
     */
    @Test
    fun `a rename is not visible to a session already open`() = runTestAsUser {
        val me = client.getMe()
        val newName = "renamed-${UUID.randomUUID()}"
        client.editUser(UserDTO(username = newName))

        val title = uniqueTitle()
        client.createRecipe(recipeDTO.copy(title = title))

        val line = editLines(title).single()
        assertTrue(line.contains("by user ${me.id}"), line)
        assertTrue(line.contains(me.username), "expected the name the session was opened with: $line")
        assertFalse(line.contains(newName), "a fresh session is not re-read: $line")
    }

    /**
     * Driven through a bearer token so the session id is in hand, and aged by hand: the refresh
     * is gated on the session being a day old, which a test cannot wait for.
     */
    @Test
    fun `a rename reaches the session once its expiry is next slid`() = runTest {
        val token = client.sessionToken(USER1, password)
        fun HttpRequestBuilder.auth() = bearerAuth(token)

        val meId = Json.decodeFromString<UserInfo>(
            client.get("$AUTH_URL/me") { auth() }.bodyAsText()
        ).id

        val newName = "renamed-${UUID.randomUUID()}"
        client.put(USER_URL) {
            auth(); contentType(ContentType.Application.Json); setBody(UserDTO(username = newName))
        }.apply { assertEquals(HttpStatusCode.OK, status) }

        // Old enough for touchSession to act on the next request that carries it.
        redisService.redis.expire(
            "session:$token",
            RedisService.SESSION_TTL - RedisService.REFRESH_THRESHOLD - 60,
        )
        client.get("$AUTH_URL/me") { auth() }

        val title = uniqueTitle()
        client.post(RECIPE_URL) {
            auth(); contentType(ContentType.Application.Json); setBody(recipeDTO.copy(title = title))
        }.apply { assertEquals(HttpStatusCode.Created, status) }

        val line = editLines(title).single()
        assertTrue(line.contains("by user $meId"), line)
        assertTrue(line.contains(newName), "the label should have caught up: $line")
    }

    @Test
    fun `a delete that removed nothing leaves no line`() = runTestAsAdmin {
        val missing = 999_999L
        client.delete("$INGREDIENT_URL/$missing").apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
        assertEquals(emptyList(), editLines("Ingredient $missing"))
    }
}
