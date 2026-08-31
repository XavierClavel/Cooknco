package main.com.xavierclavel.other

import com.xavierclavel.ApplicationTest
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.getAdminUser
import main.com.xavierclavel.utils.getMe
import main.com.xavierclavel.utils.getRecipeRaw
import main.com.xavierclavel.utils.listAdminRecipes
import main.com.xavierclavel.utils.listRecipes
import main.com.xavierclavel.utils.listReports
import main.com.xavierclavel.utils.listReportsRaw
import main.com.xavierclavel.utils.report
import main.com.xavierclavel.utils.resolveReport
import main.com.xavierclavel.utils.resolveReportRaw
import org.junit.jupiter.api.Test
import shared.enums.AccountStatus
import shared.enums.ModerationAction
import shared.enums.ReportReason
import shared.enums.ReportStatus
import shared.enums.ReportTargetType
import shared.infodto.RecipeInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End-to-end moderation: a user reports something, a moderator decides what happens. */
class ModerationTest : ApplicationTest() {

    @Test
    fun `the queue is closed to regular users`() = runTestAsUser {
        client.listReportsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `a filed report shows up in the pending queue`() = runTest {
        var recipe: RecipeInfo? = null
        var reporterName: String? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 {
            reporterName = client.getMe().username
            client.report(ReportTargetType.RECIPE, recipe!!.id, ReportReason.SPAM, "ad for a shop")
        }

        runAsAdmin {
            val queue = client.listReports(status = ReportStatus.PENDING)
            assertEquals(1, queue.count)
            queue.items.first().apply {
                assertEquals(ReportTargetType.RECIPE, targetType)
                assertEquals(recipe!!.id, targetId)
                assertEquals(recipe!!.title, targetLabel)
                assertEquals(reporterName, reporter?.username)
                assertEquals("ad for a shop", comment)
                assertFalse(targetHidden)
            }
        }
    }

    @Test
    fun `dismissing a report leaves the content alone`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 { client.report(ReportTargetType.RECIPE, recipe!!.id) }

        runAsAdmin {
            val report = client.listReports().items.first()
            client.resolveReport(report.id, ModerationAction.DISMISS, "looks fine").apply {
                assertEquals(ReportStatus.DISMISSED, status)
                assertEquals(ModerationAction.DISMISS, resolution)
                assertEquals("looks fine", moderatorNote)
                assertNotNull(resolvedBy)
            }
            assertFalse(client.listAdminRecipes().items.first { it.id == recipe!!.id }.isHidden)
        }
    }

    @Test
    fun `hiding through the queue hides the recipe`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 { client.report(ReportTargetType.RECIPE, recipe!!.id) }

        runAsAdmin {
            val report = client.listReports().items.first()
            client.resolveReport(report.id, ModerationAction.HIDE_CONTENT, "off topic").apply {
                assertEquals(ReportStatus.RESOLVED, status)
                assertTrue(targetHidden)
            }
            assertTrue(client.listAdminRecipes().items.first { it.id == recipe!!.id }.isHidden)
        }

        runAsUser2 {
            assertEquals(HttpStatusCode.Forbidden, client.getRecipeRaw(recipe!!.id).status)
        }
    }

    @Test
    fun `deleting through the queue removes the recipe but keeps the report`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 { client.report(ReportTargetType.RECIPE, recipe!!.id) }

        runAsAdmin {
            val report = client.listReports().items.first()
            client.resolveReport(report.id, ModerationAction.DELETE_CONTENT)
            assertTrue(client.listAdminRecipes().items.none { it.id == recipe!!.id })
            // The report survives its target, which now renders as deleted
            client.listReports().items.first().apply {
                assertEquals(ReportStatus.RESOLVED, status)
                assertNull(targetLabel)
            }
        }
    }

    @Test
    fun `suspending the author also hides the reported recipe`() = runTest {
        var recipe: RecipeInfo? = null
        var authorId: Long = 0
        runAsUser1 {
            recipe = client.createRecipe()
            authorId = client.getMe().id
        }
        runAsUser2 { client.report(ReportTargetType.RECIPE, recipe!!.id) }

        runAsAdmin {
            val report = client.listReports().items.first()
            client.resolveReport(
                report.id,
                ModerationAction.SUSPEND_AUTHOR,
                "repeat offender",
                suspensionDays = 14,
            )
            client.getAdminUser(authorId).apply {
                assertEquals(AccountStatus.SUSPENDED, status)
                assertNotNull(suspendedUntil)
            }
            assertTrue(client.listAdminRecipes().items.first { it.id == recipe!!.id }.isHidden)
        }
    }

    @Test
    fun `banning the author hides their other recipes from everyone else`() = runTest {
        var reported: RecipeInfo? = null
        var other: RecipeInfo? = null
        var authorId: Long = 0
        runAsUser1 {
            reported = client.createRecipe()
            other = client.createRecipe()
            authorId = client.getMe().id
        }
        runAsUser2 { client.report(ReportTargetType.RECIPE, reported!!.id) }

        runAsAdmin {
            val report = client.listReports().items.first()
            client.resolveReport(report.id, ModerationAction.BAN_AUTHOR, "abuse")
            client.getAdminUser(authorId).apply {
                assertEquals(AccountStatus.BANNED, status)
                assertTrue(isBanned)
            }
        }

        runAsUser2 {
            val visible = client.listRecipes().map { it.id }
            assertFalse(reported!!.id in visible)
            assertFalse(other!!.id in visible)
        }
    }

    @Test
    fun `a resolved report cannot be resolved twice`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 { client.report(ReportTargetType.RECIPE, recipe!!.id) }

        runAsAdmin {
            val report = client.listReports().items.first()
            client.resolveReport(report.id, ModerationAction.DISMISS)
            client.resolveReportRaw(report.id, ModerationAction.DISMISS).apply {
                assertEquals(HttpStatusCode.BadRequest, status)
            }
        }
    }

    @Test
    fun `deciding on one report settles every other report on the same target`() = runTest {
        val thirdMail = "third@mail.com"
        setupTestUser(thirdMail)

        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 { client.report(ReportTargetType.RECIPE, recipe!!.id, ReportReason.SPAM) }
        runAs(thirdMail) { client.report(ReportTargetType.RECIPE, recipe!!.id, ReportReason.COPYRIGHT) }

        runAsAdmin {
            val pending = client.listReports(status = ReportStatus.PENDING)
            assertEquals(2, pending.count)
            assertEquals(2, pending.items.first().reportsOnTargetCount)

            client.resolveReport(pending.items.first().id, ModerationAction.HIDE_CONTENT, "spam")
            assertEquals(0, client.listReports(status = ReportStatus.PENDING).count)
            assertEquals(2, client.listReports(status = ReportStatus.RESOLVED).count)
        }
    }

    @Test
    fun `hiding is not applicable to an account report`() = runTest {
        var authorId: Long = 0
        runAsUser1 { authorId = client.getMe().id }
        runAsUser2 { client.report(ReportTargetType.USER, authorId) }

        runAsAdmin {
            val report = client.listReports().items.first()
            client.resolveReportRaw(report.id, ModerationAction.HIDE_CONTENT).apply {
                assertEquals(HttpStatusCode.BadRequest, status)
            }
            // The failed decision left the report pending
            assertEquals(ReportStatus.PENDING, client.listReports().items.first().status)
        }
    }

    @Test
    fun `reports can be filtered by target type`() = runTest {
        var recipe: RecipeInfo? = null
        var authorId: Long = 0
        runAsUser1 {
            recipe = client.createRecipe()
            authorId = client.getMe().id
        }
        runAsUser2 {
            client.report(ReportTargetType.RECIPE, recipe!!.id)
            client.report(ReportTargetType.USER, authorId)
        }

        runAsAdmin {
            assertEquals(1, client.listReports(targetType = ReportTargetType.RECIPE).count)
            assertEquals(1, client.listReports(targetType = ReportTargetType.USER).count)
            assertEquals(2, client.listReports().count)
        }
    }

    @Test
    fun `admin recipe listing can show only reported recipes`() = runTest {
        var reported: RecipeInfo? = null
        runAsUser1 { reported = client.createRecipe() }
        runAsUser2 {
            client.createRecipe()
            client.report(ReportTargetType.RECIPE, reported!!.id)
        }

        runAsAdmin {
            assertEquals(listOf(reported!!.id), client.listAdminRecipes(reported = true).items.map { it.id })
        }
    }
}
