package com.xavierclavel.cooknco.ui.components

import com.xavierclavel.cooknco.network.ApiException
import com.xavierclavel.cooknco.network.dto.UserInfo
import com.xavierclavel.cooknco.ui.i18n.EnStrings
import com.xavierclavel.cooknco.ui.i18n.FrStrings
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two decisions the export makes with no screen and no server involved: who is offered
 * one, and what a refusal is called.
 */
class PdfExportTest {

    private fun user(role: String) = UserInfo(
        id = 1L,
        version = 1L,
        username = "someone",
        role = role,
        joinDate = 0L,
        bio = "",
        recipesCount = 0,
        likesCount = 0,
        cookbooksCount = 0,
        followersCount = 0,
        followsCount = 0,
    )

    @Test
    fun only_an_admin_is_offered_an_export() {
        assertTrue(user("ADMIN").isAdmin)
        assertFalse(user("USER").isAdmin)
        // Whatever the backend grows next is not an admin here, which is the safe way round:
        // the routes refuse it anyway, so the only thing at stake is offering a dead action.
        assertFalse(user("MODERATOR").isAdmin)
    }

    @Test
    fun a_cookbook_too_long_to_print_says_so_rather_than_apologising() {
        val refusal = ApiException(HttpStatusCode.BadRequest, "cookbook_too_large_to_export")

        assertEquals(EnStrings.cookbookTooLargeToExport, pdfExportFailure(refusal, EnStrings))
        assertEquals(FrStrings.cookbookTooLargeToExport, pdfExportFailure(refusal, FrStrings))
    }

    @Test
    fun a_renderer_with_no_room_left_is_worth_trying_again() {
        val refusal = ApiException(HttpStatusCode.ServiceUnavailable, "pdf_renderer_busy")

        assertEquals(EnStrings.exportRendererBusy, pdfExportFailure(refusal, EnStrings))
    }

    /**
     * Anything else reads as one apology. What must never happen is the backend's own
     * wording reaching the screen: it is a cause, not a sentence, and it is not translated.
     */
    @Test
    fun anything_else_is_named_rather_than_passed_through() {
        assertEquals(
            EnStrings.exportFailed,
            pdfExportFailure(ApiException(HttpStatusCode.InternalServerError, "pdf_renderer_failed"), EnStrings),
        )
        assertEquals(EnStrings.exportFailed, pdfExportFailure(RuntimeException("Connection reset"), EnStrings))
        assertEquals(EnStrings.exportFailed, pdfExportFailure(RuntimeException(), EnStrings))
    }
}
