package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.logging.LogBuffer
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.unsubscribeRaw
import org.junit.jupiter.api.Test
import shared.dto.UserSettingsDTO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The unsubscribe link the notification mails carry.
 *
 * What it has to be is a link somebody can press straight from an inbox, long after the mail
 * went out and without signing in — so the token is signed rather than stored, and the
 * endpoint sits outside the authenticate block. What it therefore must *not* be is worth
 * anything beyond an unsubscribe, and that is what most of this pins down: a forgery is
 * refused, and even a genuine token only ever switches these mails off.
 */
class UnsubscribeControllerTest : ApplicationTest() {

    private val reader = "reader@mail.com"

    private val subscribed = UserSettingsDTO(
        autoAcceptFollowRequests = true,
        isAccountPublic = true,
        mailNotificationsEnabled = true,
    )

    @Test
    fun `pressing the link in a mail stops the mails`() = runTest {
        val readerId = setupTestUser(reader, subscribed)

        client.unsubscribeRaw(unsubscribeService.tokenFor(readerId))
            .apply { assertEquals(HttpStatusCode.OK, status) }

        assertFalse(mailsWanted(readerId))
        // The actor is passed in rather than read off a session, since there is none: what
        // the trail must not do is file this under nobody
        assertEquals(1, editLines(readerId).size)
    }

    /**
     * The link is in every mail already sent and stays in the reader's archive for good, so
     * a second press is an ordinary thing rather than an error to report.
     */
    @Test
    fun `pressing it twice is pressing it once`() = runTest {
        val readerId = setupTestUser(reader, subscribed)
        val token = unsubscribeService.tokenFor(readerId)

        client.unsubscribeRaw(token).apply { assertEquals(HttpStatusCode.OK, status) }
        client.unsubscribeRaw(token).apply { assertEquals(HttpStatusCode.OK, status) }

        assertFalse(mailsWanted(readerId))
        // One write, so one line. A trail with a line per press would read as a reader
        // asking over and over rather than as somebody who pressed twice
        assertEquals(1, editLines(readerId).size)
    }

    /**
     * The one asymmetry in the feature, and the reason the token is safe to hand out. Whoever
     * holds one can silence an address; if the same token could put it back on the list, a
     * leaked mail would be a way to mail somebody who had already said no, and the account
     * would never see it happen. Turning them back on is the settings screen, behind a session.
     */
    @Test
    fun `the link cannot subscribe anyone, only unsubscribe them`() = runTest {
        val readerId = setupTestUser(reader, UserSettingsDTO(mailNotificationsEnabled = false))
        assertFalse(mailsWanted(readerId))

        client.unsubscribeRaw(unsubscribeService.tokenFor(readerId))
            .apply { assertEquals(HttpStatusCode.OK, status) }

        assertFalse(mailsWanted(readerId))
    }

    @Test
    fun `a token nobody signed is refused`() = runTest {
        val readerId = setupTestUser(reader, subscribed)
        val signature = unsubscribeService.tokenFor(readerId).substringAfter('.')

        listOf(
            "",
            "$readerId",
            "$readerId.",
            "$readerId.forged",
            // Another account's id in front of a signature that is genuinely ours. The id is
            // public, so this is the forgery worth being sure about: only the signature can
            // be doing the work
            "${readerId + 1}.$signature",
            "$readerId.${signature.dropLast(1)}",
        ).forEach {
            assertEquals(HttpStatusCode.Unauthorized, client.unsubscribeRaw(it).status, "\"$it\" is not a token")
        }

        assertTrue(mailsWanted(readerId))
        assertTrue(editLines(readerId).isEmpty(), "a refused token wrote nothing to log")
    }

    /** An account gone since the mail was sent is receiving nothing either way. */
    @Test
    fun `a token for an account that no longer exists is not an error`() = runTest {
        val readerId = setupTestUser(reader, subscribed)
        val token = unsubscribeService.tokenFor(readerId)
        userService.deleteUserById(readerId)

        assertEquals(HttpStatusCode.OK, client.unsubscribeRaw(token).status)
    }

    private fun mailsWanted(userId: Long): Boolean =
        userService.getSettings(userId).mailNotificationsEnabled == true

    /**
     * The edit-trail lines naming this account.
     *
     * Scoped by the account id rather than by clearing the buffer, as `EditLogTest` is and for
     * the same reason: [LogBuffer] is process-wide and other test classes append to it
     * throughout. Ids are never reused within a run, so this matches only our own presses.
     */
    private fun editLines(userId: Long): List<String> =
        LogBuffer.query(
            logger = "com.xavierclavel.EditActions",
            search = "notification mails by user $userId ",
            limit = LogBuffer.MAX_PAGE_SIZE,
        ).entries.map { it.message }
}
