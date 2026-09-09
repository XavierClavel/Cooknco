package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.models.query.QDevice
import com.xavierclavel.models.query.QNotification
import com.xavierclavel.models.query.QUser
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.announce
import main.com.xavierclavel.utils.announceRaw
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.getPushAudience
import main.com.xavierclavel.utils.getPushAudienceRaw
import main.com.xavierclavel.utils.listNotifications
import main.com.xavierclavel.utils.registerDevice
import main.com.xavierclavel.utils.sendTestNotification
import main.com.xavierclavel.utils.sendTestNotificationRaw
import org.junit.jupiter.api.Test
import shared.enums.DevicePlatform
import shared.enums.Locale
import shared.utils.NotificationWordings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Announcements and test sends, from the backoffice notifications tab.
 *
 * An announcement is the one notification whose wording an operator writes, so what is worth
 * pinning down is the audience — that "everybody" is asked for explicitly and reaches
 * everybody, that naming users reaches only those — and that a test cannot be aimed at
 * anyone but the caller.
 */
class AdminNotificationControllerTest : ApplicationTest() {

    // ----------------------------------------------------------- authorisation

    @Test
    fun `notification sending is closed to anonymous callers`() = runTest {
        client.getPushAudienceRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.announceRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.sendTestNotificationRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `notification sending is closed to regular users`() = runTestAsUser {
        client.getPushAudienceRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.announceRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // ---------------------------------------------------------------- audience

    /**
     * The two numbers answer different questions, and a user with two handsets is what
     * makes them differ: one recipient, two pushes.
     */
    @Test
    fun `the audience counts users and devices separately`() = runTest {
        val second = "audience@mail.com"
        runAsAdmin { client.createUser(second) }

        runAsUser1 {
            client.registerDevice("phone")
            client.registerDevice("tablet")
        }
        runAs(second, "password") { client.registerDevice("other-phone") }

        runAsAdmin {
            val audience = client.getPushAudience()
            // A broadcast is stored for every account, device or not, so users is all of them
            assertEquals(QUser().isBanned.eq(false).findCount(), audience.users)
            assertEquals(3, audience.devices)
            assertEquals(mapOf(DevicePlatform.ANDROID to 3), audience.byPlatform)
        }
    }

    /**
     * The language filter narrows on the *device* locale, which is the only one a client
     * reports — and so a filtered broadcast reaches nobody who has no device at all.
     */
    @Test
    fun `a broadcast can be narrowed to the users with a device in one language`() = runTest {
        val french = "french@mail.com"
        runAsAdmin { client.createUser(french) }

        runAsUser1 { client.registerDevice("en-phone", locale = Locale.EN) }
        runAs(french, "password") { client.registerDevice("fr-phone", locale = Locale.FR) }

        runAsAdmin {
            val all = client.getPushAudience()
            assertEquals(QUser().isBanned.eq(false).findCount(), all.users)
            assertEquals(2, all.devices)

            val inFrench = client.getPushAudience(locale = Locale.FR)
            assertEquals(1, inFrench.users, "only the account with a French device")
            assertEquals(1, inFrench.devices)
        }
    }

    @Test
    fun `naming users narrows the audience to their devices`() = runTest {
        val second = "narrow@mail.com"
        var secondId = 0L
        runAsAdmin { secondId = client.createUser(second).id }

        runAsUser1 { client.registerDevice("user1-phone") }
        runAs(second, "password") { client.registerDevice("user2-phone") }

        runAsAdmin {
            val audience = client.getPushAudience(users = listOf(secondId))
            assertEquals(1, audience.users)
            assertEquals(1, audience.devices)
        }
    }

    // ------------------------------------------------------------ announcements

    @Test
    fun `an announcement reaches every user, with or without a device`() = runTest {
        val withoutDevice = "quiet@mail.com"
        runAsAdmin { client.createUser(withoutDevice) }
        runAsUser1 { client.registerDevice("loud-phone") }

        runAsAdmin {
            val result = client.announce(title = "Closing", body = "Back on Monday")

            // admin + user1 + user2 + the account with no device
            assertEquals(4, result.recipients)
            assertEquals(1, result.devices, "only one of them has anywhere to push to")
            assertEquals(4, QNotification().findCount(), "a row per recipient, device or not")
        }

        // Stored for the silent account too, which is the point of storing before pushing
        runAs(withoutDevice, "password") {
            assertEquals("Closing", client.listNotifications().single().title)
        }
    }

    @Test
    fun `an announcement to named users reaches only them`() = runTest {
        var targetId = 0L
        val target = "target@mail.com"
        runAsAdmin { targetId = client.createUser(target).id }

        runAsAdmin {
            val result = client.announce(title = "Just you", body = "Body", userIds = listOf(targetId))
            assertEquals(1, result.recipients)
            assertEquals(1, QNotification().findCount())
        }

        runAs(target, "password") {
            assertEquals("Just you", client.listNotifications().single().title)
        }
        runAsUser1 {
            assertEquals(emptyList(), client.listNotifications())
        }
    }

    @Test
    fun `an announcement naming a user who does not exist is refused`() = runTestAsAdmin {
        client.announceRaw(userIds = listOf(9_999_999L)).apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
        assertEquals(0, QNotification().findCount(), "nothing is sent when part of the audience is wrong")
    }

    @Test
    fun `the pushed payload carries the wording and the notification it announces`() = runTest {
        runAsUser1 { client.registerDevice("payload-phone") }

        runAsAdmin {
            client.announce(title = "Title here", body = "Body here", link = "/recipe/view?id=7")

            val pushed = fakePushSender.awaitKind("announcement")
            assertEquals(1, pushed.size)
            assertEquals("Title here", pushed.single().title)
            assertEquals("Body here", pushed.single().body)
            assertEquals("/recipe/view?id=7", pushed.single().data["link"])
            // The id lets a tap mark exactly the row it came from read
            assertTrue(pushed.single().data["notificationId"]?.toLongOrNull() != null)
        }
    }

    /** A blank link is left off the payload rather than sent empty for the client to test. */
    @Test
    fun `a notification with nowhere to go carries no link`() = runTest {
        runAsUser1 { client.registerDevice("linkless-phone") }

        runAsAdmin {
            client.announce(title = "No link", body = "Body")
            val pushed = fakePushSender.awaitKind("announcement")
            assertFalse(pushed.single().data.containsKey("link"))
        }
    }

    // ---------------------------------------------------------------- validation

    @Test
    fun `an announcement needs both a title and a message`() = runTestAsAdmin {
        client.announceRaw(title = "  ", body = "Body").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        client.announceRaw(title = "Title", body = "  ").apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        assertEquals(0, QNotification().findCount())
    }

    @Test
    fun `an over-long announcement is refused rather than truncated`() = runTestAsAdmin {
        client.announceRaw(title = "x".repeat(NotificationWordings.MAX_TITLE_LENGTH + 1)).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
        client.announceRaw(body = "x".repeat(NotificationWordings.MAX_BODY_LENGTH + 1)).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `an announcement with nobody in its audience is refused`() = runTestAsAdmin {
        // Every account in a test database reads in FR by default, so an EN-only broadcast
        // has an empty audience — which is worth being told rather than reported as sent
        client.announceRaw(locale = Locale.EN).apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    // -------------------------------------------------------------- test sends

    @Test
    fun `a test send goes to the caller's own devices and reports what happened`() = runTestAsAdmin {
        client.registerDevice("admin-phone")
        client.registerDevice("admin-tablet")

        val result = client.sendTestNotification(title = "Ping", body = "Pong")

        assertEquals(2, result.devices)
        assertEquals(2, result.pushed)
        assertEquals(0, result.failed)
        assertEquals(setOf("admin-phone", "admin-tablet"), fakePushSender.sent.map { it.token }.toSet())
    }

    @Test
    fun `a test send with no device says so rather than reporting a send`() = runTestAsAdmin {
        client.sendTestNotificationRaw().apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    @Test
    fun `a test send falls back to a default wording`() = runTestAsAdmin {
        client.registerDevice("default-phone")

        client.sendTestNotification(title = "", body = "")

        val pushed = fakePushSender.sent.single()
        assertTrue(pushed.title.isNotBlank())
        assertTrue(pushed.body.isNotBlank())
    }

    // -------------------------------------------------------------- dead tokens

    /**
     * The only thing that prunes the table. A merely failed push must not, or an outage
     * would quietly unsubscribe every live user.
     */
    @Test
    fun `a token FCM reports as dead is dropped`() = runTestAsAdmin {
        client.registerDevice("live-phone")
        client.registerDevice("dead-phone")
        fakePushSender.staleTokens = setOf("dead-phone")

        client.sendTestNotification()

        assertTrue(QDevice().token.eq("live-phone").exists())
        assertFalse(QDevice().token.eq("dead-phone").exists())
    }

    @Test
    fun `a push that merely failed keeps the device`() = runTestAsAdmin {
        client.registerDevice("flaky-phone")
        fakePushSender.failing = true

        client.sendTestNotification()

        assertTrue(QDevice().token.eq("flaky-phone").exists())
    }
}
