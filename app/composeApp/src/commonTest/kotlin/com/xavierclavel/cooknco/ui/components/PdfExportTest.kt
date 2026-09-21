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
 * The two decisions the export makes with no screen and no server involved: who it is
 * unlocked for, and what a refusal is called. What a locked row then does is
 * [PremiumLockTest].
 */
class PdfExportTest {

    private fun user(role: String = "USER", isPremium: Boolean = false) = UserInfo(
        id = 1L,
        version = 1L,
        username = "someone",
        role = role,
        isPremium = isPremium,
        joinDate = 0L,
        bio = "",
        recipesCount = 0,
        likesCount = 0,
        cookbooksCount = 0,
        followersCount = 0,
        followsCount = 0,
    )

    /**
     * The export is unlocked on one flag, and the backend sets it — including for an admin,
     * who holds no grant but passes every premium gate. Nothing here re-derives that, so
     * the app cannot come to a different answer than the route it is about to call, and a
     * response that says nothing leaves the export locked rather than unparsed. Locked, not
     * hidden: the row is on the sheet either way, and says why it will not open.
     */
    @Test
    fun only_a_premium_account_has_the_export_unlocked() {
        assertTrue(user(isPremium = true).isPremium)
        assertFalse(user().isPremium)
    }

    @Test
    fun the_role_still_says_who_moderates() {
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
