package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.utils.logger
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import main.com.xavierclavel.utils.banUserRaw
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.deleteRecipeAsAdminRaw
import main.com.xavierclavel.utils.deleteUserAsAdminRaw
import main.com.xavierclavel.utils.getAdminOverview
import main.com.xavierclavel.utils.getAdminOverviewRaw
import main.com.xavierclavel.utils.getAdminUser
import main.com.xavierclavel.utils.getLogs
import main.com.xavierclavel.utils.getLogsRaw
import main.com.xavierclavel.utils.getMe
import main.com.xavierclavel.utils.getTrends
import main.com.xavierclavel.utils.getTrendsRaw
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.hideRecipe
import main.com.xavierclavel.utils.report
import main.com.xavierclavel.utils.listAdminIngredients
import main.com.xavierclavel.utils.listAdminRecipes
import main.com.xavierclavel.utils.listAdminUsers
import main.com.xavierclavel.utils.listRecipes
import main.com.xavierclavel.utils.login
import main.com.xavierclavel.utils.reinstateUserRaw
import main.com.xavierclavel.utils.setUserRoleRaw
import main.com.xavierclavel.utils.suspendUserRaw
import main.com.xavierclavel.utils.unhideRecipe
import org.junit.jupiter.api.Test
import shared.dto.IngredientDTO
import shared.enums.AccountStatus
import shared.enums.IngredientType
import shared.enums.Locale
import com.xavierclavel.services.AdminService
import shared.enums.LogLevel
import shared.enums.ReportTargetType
import shared.enums.TimeGranularity
import shared.enums.UserRole
import shared.infodto.LogEntryInfo
import shared.infodto.RecipeInfo
import shared.utils.URL.ADMIN_URL
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminControllerTest : ApplicationTest() {

    // ----------------------------------------------------------- authorisation

    @Test
    fun `backoffice is closed to anonymous callers`() = runTest {
        client.getAdminOverviewRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.getLogsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `backoffice is closed to regular users`() = runTestAsUser {
        client.getAdminOverviewRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.getLogsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // ---------------------------------------------------------------- overview

    @Test
    fun `overview counts users and recipes`() = runTest {
        runAsUser1 { client.createRecipe() }
        runAsAdmin {
            val overview = client.getAdminOverview()
            // user1, user2 and the default admin all exist by now
            assertTrue(overview.usersCount >= 3)
            assertTrue(overview.recipesCount >= 1)
            assertEquals(0, overview.pendingReportsCount)
        }
    }

    @Test
    fun `overview counts hidden recipes`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsAdmin {
            assertEquals(0, client.getAdminOverview().hiddenRecipesCount)
            client.hideRecipe(recipe!!.id, "spam")
            assertEquals(1, client.getAdminOverview().hiddenRecipesCount)
        }
    }

    // ------------------------------------------------------------------- users

    @Test
    fun `users listing exposes mail and account status`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin {
            client.getAdminUser(userId).apply {
                assertEquals(USER1, mail)
                assertEquals(AccountStatus.ACTIVE, status)
                assertFalse(isBanned)
                assertNull(suspendedUntil)
            }
        }
    }

    @Test
    fun `users can be searched by exact mail`() = runTest {
        val mail = "searchable@mail.com"
        val userId = setupTestUser(mail)
        runAsAdmin {
            val result = client.listAdminUsers(query = mail)
            assertEquals(1, result.count)
            result.items.first().apply {
                assertEquals(userId, id)
                assertEquals(mail, mail)
            }
        }
    }

    @Test
    fun `users can be searched by username fragment`() = runTest {
        var username: String? = null
        runAsUser1 { username = client.getMe().username }
        runAsAdmin {
            val result = client.listAdminUsers(query = username!!.substring(0, 8))
            assertTrue(result.items.any { it.username == username })
        }
    }

    @Test
    fun `users can be filtered by status`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin {
            client.suspendUserRaw(userId, days = 3, reason = "cooling off")
            assertEquals(listOf(userId), client.listAdminUsers(status = "SUSPENDED").items.map { it.id })
            assertFalse(client.listAdminUsers(status = "ACTIVE").items.any { it.id == userId })
        }
    }

    @Test
    fun `suspending then reinstating an account`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin {
            client.suspendUserRaw(userId, days = 5, reason = "spamming").apply {
                assertEquals(HttpStatusCode.OK, status)
            }
            client.getAdminUser(userId).apply {
                assertEquals(AccountStatus.SUSPENDED, status)
                assertNotNull(suspendedUntil)
                assertEquals("spamming", moderationNote)
            }
            client.reinstateUserRaw(userId).apply { assertEquals(HttpStatusCode.OK, status) }
            client.getAdminUser(userId).apply {
                assertEquals(AccountStatus.ACTIVE, status)
                assertNull(suspendedUntil)
            }
        }
    }

    @Test
    fun `a suspended user cannot log back in`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin { client.suspendUserRaw(userId, days = 5) }
        client.login(USER1, password).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `a banned user cannot log back in`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin {
            client.banUserRaw(userId, "abuse").apply { assertEquals(HttpStatusCode.OK, status) }
        }
        client.login(USER1, password).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `suspension length is validated`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin {
            client.suspendUserRaw(userId, days = 0).apply { assertEquals(HttpStatusCode.BadRequest, status) }
            client.suspendUserRaw(userId, days = 99_999).apply { assertEquals(HttpStatusCode.BadRequest, status) }
        }
    }

    @Test
    fun `admins cannot be moderated before being demoted`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin {
            client.setUserRoleRaw(userId, UserRole.ADMIN).apply { assertEquals(HttpStatusCode.OK, status) }
            client.banUserRaw(userId).apply { assertEquals(HttpStatusCode.Forbidden, status) }
            client.suspendUserRaw(userId).apply { assertEquals(HttpStatusCode.Forbidden, status) }
            client.deleteUserAsAdminRaw(userId).apply { assertEquals(HttpStatusCode.Forbidden, status) }
        }
    }

    @Test
    fun `the last admin cannot be demoted`() = runTestAsAdmin {
        val me = client.getMe()
        client.setUserRoleRaw(me.id, UserRole.USER).apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    @Test
    fun `an admin cannot delete their own account from the backoffice`() = runTestAsAdmin {
        val me = client.getMe()
        client.deleteUserAsAdminRaw(me.id).apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    @Test
    fun `admin can delete a regular account`() = runTest {
        var userId: Long = 0
        runAsUser1 { userId = client.getMe().id }
        runAsAdmin {
            client.deleteUserAsAdminRaw(userId).apply { assertEquals(HttpStatusCode.OK, status) }
            assertTrue(client.listAdminUsers().items.none { it.id == userId })
        }
    }

    // ----------------------------------------------------------------- recipes

    @Test
    fun `hiding a recipe keeps it visible to its owner only`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }

        runAsAdmin {
            client.hideRecipe(recipe!!.id, "off topic").apply {
                assertTrue(isHidden)
                assertEquals("off topic", hiddenReason)
            }
        }

        // The author still sees it, flagged as hidden
        runAsUser1 {
            val own = client.getRecipe(recipe!!.id)
            assertTrue(own.isHidden)
            assertTrue(client.listRecipes(user = own.owner.id).any { it.id == recipe!!.id })
        }

        // Everyone else no longer does
        runAsUser2 {
            assertFalse(client.listRecipes().any { it.id == recipe!!.id })
        }
    }

    @Test
    fun `un-hiding a recipe brings it back`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsAdmin {
            client.hideRecipe(recipe!!.id, "mistake")
            client.unhideRecipe(recipe!!.id).apply {
                assertFalse(isHidden)
                assertEquals("", hiddenReason)
            }
        }
        runAsUser2 {
            assertTrue(client.listRecipes().any { it.id == recipe!!.id })
        }
    }

    @Test
    fun `recipes can be filtered by hidden state`() = runTest {
        var hidden: RecipeInfo? = null
        var visible: RecipeInfo? = null
        runAsUser1 { hidden = client.createRecipe() }
        runAsUser2 { visible = client.createRecipe() }
        runAsAdmin {
            client.hideRecipe(hidden!!.id, "spam")
            assertEquals(listOf(hidden!!.id), client.listAdminRecipes(hidden = true).items.map { it.id })
            assertEquals(listOf(visible!!.id), client.listAdminRecipes(hidden = false).items.map { it.id })
        }
    }

    @Test
    fun `admin can delete any recipe`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsAdmin {
            client.deleteRecipeAsAdminRaw(recipe!!.id).apply { assertEquals(HttpStatusCode.OK, status) }
            assertTrue(client.listAdminRecipes().items.none { it.id == recipe!!.id })
        }
    }

    // ------------------------------------------------------------- ingredients

    @Test
    fun `ingredient catalogue reports usage counts`() = runTestAsAdmin {
        client.createIngredient()
        val result = client.listAdminIngredients()
        assertEquals(1, result.count)
        assertEquals(0, result.items.first().recipesCount)
    }

    @Test
    fun `ingredient search matches any locale and yields one row per ingredient`() = runTestAsAdmin {
        val ingredient = client.createIngredient(
            IngredientDTO(
                name = mapOf(Locale.EN to "aubergine", Locale.FR to "aubergine"),
                type = IngredientType.VEGETABLE,
            ),
        )
        client.createIngredient(
            IngredientDTO(name = mapOf(Locale.EN to "carrot"), type = IngredientType.VEGETABLE),
        )

        // "aubergine" matches both translations of the same row, which must not double it
        client.listAdminIngredients(query = "auberg").apply {
            assertEquals(1, count)
            assertEquals(ingredient.id, items.first().id)
        }
        client.listAdminIngredients(query = "carr").apply { assertEquals(1, count) }
        assertEquals(2, client.listAdminIngredients().count)
    }

    // ------------------------------------------------------------------ trends

    @Test
    fun `trends return one bucket per period, including empty ones`() = runTestAsAdmin {
        val trends = client.getTrends(granularity = "DAY", buckets = 7)
        assertEquals(TimeGranularity.DAY, trends.granularity)
        assertEquals(7, trends.points.size)
        // Generated buckets, so quiet days are explicit zeros rather than gaps
        assertTrue(trends.points.dropLast(1).all { it.newRecipes == 0 })
        // Chronological, and each bucket is a distinct date
        assertEquals(trends.points.map { it.bucket }.sorted(), trends.points.map { it.bucket })
        assertEquals(trends.points.size, trends.points.map { it.bucket }.distinct().size)
    }

    @Test
    fun `trends count activity into the current bucket`() = runTest {
        runAsUser1 { client.createRecipe() }
        runAsAdmin {
            val trends = client.getTrends(granularity = "DAY", buckets = 7)
            val today = trends.points.last()
            assertEquals(1, today.newRecipes)
            // user1, user2 and the default admin all signed up in this run
            assertTrue(today.newUsers >= 3)
        }
    }

    @Test
    fun `the cumulative user total never decreases`() = runTestAsAdmin {
        val totals = client.getTrends(granularity = "DAY", buckets = 10).points.map { it.totalUsers }
        assertEquals(totals.sorted(), totals)
        assertTrue(totals.last() >= 3)
    }

    @Test
    fun `the cumulative recipe total never decreases and tracks what was created`() = runTest {
        runAsUser1 {
            client.createRecipe()
            client.createRecipe()
        }
        runAsAdmin {
            val points = client.getTrends(granularity = "DAY", buckets = 10).points
            val totals = points.map { it.totalRecipes }
            assertEquals(totals.sorted(), totals)
            // Nothing existed before this run, so the running total lands on today's two
            assertEquals(0, points.first().totalRecipes)
            assertEquals(2, totals.last())
        }
    }

    @Test
    fun `reports show up in the trend buckets`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 { client.report(ReportTargetType.RECIPE, recipe!!.id) }
        runAsAdmin {
            assertEquals(1, client.getTrends(granularity = "DAY", buckets = 7).points.last().newReports)
        }
    }

    @Test
    fun `every granularity is accepted`() = runTestAsAdmin {
        for (g in TimeGranularity.entries) {
            val trends = client.getTrends(granularity = g.name, buckets = 5)
            assertEquals(g, trends.granularity)
            assertEquals(5, trends.points.size)
        }
    }

    @Test
    fun `the bucket count is clamped rather than trusted`() = runTestAsAdmin {
        assertEquals(AdminService.MAX_BUCKETS, client.getTrends(granularity = "DAY", buckets = 10_000).points.size)
        assertEquals(AdminService.MIN_BUCKETS, client.getTrends(granularity = "DAY", buckets = -5).points.size)
    }

    @Test
    fun `an invalid granularity is rejected`() = runTestAsAdmin {
        client.getTrendsRaw(granularity = "FORTNIGHT").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `trends are closed to regular users`() = runTestAsUser {
        client.getTrendsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // -------------------------------------------------------------------- logs

    @Test
    fun `logs are captured and filterable`() = runTestAsAdmin {
        // Creating a recipe emits an info line naming the recipe
        val recipe = client.createRecipe()

        val page = client.getLogs(level = "INFO", search = recipe.title)
        assertTrue(page.entries.isNotEmpty())
        assertTrue(page.entries.all { it.message.contains(recipe.title) })
        assertTrue(page.bufferCapacity > 0)

        // Filtering on a logger nothing writes to yields nothing
        assertTrue(client.getLogs(logger = "does.not.exist").entries.isEmpty())
    }

    @Test
    fun `log level filter excludes lower severities`() = runTestAsAdmin {
        client.createRecipe()
        assertTrue(client.getLogs(level = "INFO").entries.isNotEmpty())
        assertTrue(client.getLogs(level = "ERROR").entries.all { it.level.name == "ERROR" })
    }

    @Test
    fun `an invalid log level is rejected`() = runTestAsAdmin {
        client.getLogsRaw(level = "NOPE").apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    @Test
    fun `the live tail streams matching lines as they are logged`() = runTestAsAdmin {
        val marker = "sse-marker-${UUID.randomUUID()}"

        client.prepareGet("$ADMIN_URL/logs/stream") {
            url {
                parameters.append("level", "INFO")
                parameters.append("search", marker)
            }
        }.execute { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Text.EventStream.contentType, response.contentType()?.contentType)

            val channel = response.bodyAsChannel()

            coroutineScope {
                // The collector subscribes as the handler starts running, so keep emitting
                // until a frame comes back rather than betting on winning that race
                val emitter = launch {
                    while (isActive) {
                        logger.info { marker }
                        delay(100)
                    }
                }

                try {
                    val data = withTimeout(15.seconds) {
                        var line = channel.readUTF8Line()
                        while (line != null && !line.startsWith("data:")) {
                            line = channel.readUTF8Line()
                        }
                        line
                    }
                    assertNotNull(data)
                    val entry = Json { ignoreUnknownKeys = true }
                        .decodeFromString<LogEntryInfo>(data.removePrefix("data:").trim())
                    assertEquals(LogLevel.INFO, entry.level)
                    assertTrue(entry.message.contains(marker))
                    assertTrue(entry.sequence > 0)
                } finally {
                    emitter.cancel()
                }
            }
        }
    }

    @Test
    fun `the live tail is closed to regular users`() = runTestAsUser {
        client.get("$ADMIN_URL/logs/stream").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }
}
