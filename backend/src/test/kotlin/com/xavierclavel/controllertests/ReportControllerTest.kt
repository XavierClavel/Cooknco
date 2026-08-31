package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.getMe
import main.com.xavierclavel.utils.report
import main.com.xavierclavel.utils.reportRaw
import org.junit.jupiter.api.Test
import shared.enums.ReportReason
import shared.enums.ReportStatus
import shared.enums.ReportTargetType
import shared.infodto.RecipeInfo
import kotlin.test.assertEquals

class ReportControllerTest : ApplicationTest() {

    @Test
    fun `report a recipe`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 {
            val report = client.report(
                ReportTargetType.RECIPE,
                recipe!!.id,
                ReportReason.INAPPROPRIATE_CONTENT,
                "not a recipe",
            )
            assertEquals(ReportStatus.PENDING, report.status)
            assertEquals(recipe!!.id, report.targetId)
            assertEquals(recipe!!.title, report.targetLabel)
            assertEquals("not a recipe", report.comment)
        }
    }

    @Test
    fun `cannot report own recipe`() = runTestAsUser {
        val recipe = client.createRecipe()
        client.reportRaw(ReportTargetType.RECIPE, recipe.id).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `cannot report own account`() = runTestAsUser {
        val me = client.getMe()
        client.reportRaw(ReportTargetType.USER, me.id).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `cannot file a second pending report on the same target`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        runAsUser2 {
            client.report(ReportTargetType.RECIPE, recipe!!.id)
            client.reportRaw(ReportTargetType.RECIPE, recipe!!.id).apply {
                assertEquals(HttpStatusCode.BadRequest, status)
            }
        }
    }

    @Test
    fun `cannot report something that does not exist`() = runTestAsUser {
        client.reportRaw(ReportTargetType.RECIPE, 999_999).apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun `reporting requires being logged in`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        client.reportRaw(ReportTargetType.RECIPE, recipe!!.id).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }
}
