package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.models.query.QDevice
import com.xavierclavel.models.query.QNotification
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.clearAllNotificationsRaw
import main.com.xavierclavel.utils.clearNotificationRaw
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.follow
import main.com.xavierclavel.utils.getNotifications
import main.com.xavierclavel.utils.getNotificationsRaw
import main.com.xavierclavel.utils.listNotifications
import main.com.xavierclavel.utils.markAllNotificationsReadRaw
import main.com.xavierclavel.utils.markNotificationReadRaw
import main.com.xavierclavel.utils.registerDevice
import main.com.xavierclavel.utils.registerDeviceRaw
import main.com.xavierclavel.utils.sendTestNotification
import main.com.xavierclavel.utils.unregisterDeviceRaw
import org.junit.jupiter.api.Test
import shared.dto.UserSettingsDTO
import shared.enums.DevicePlatform
import shared.enums.Locale
import shared.enums.NotificationKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A user's own notifications, and the devices they are pushed to.
 *
 * The things worth pinning down here are the ones that are not obvious from the endpoints:
 * that a device is identified by its token rather than by who registered it, that a
 * notification is nobody's but its recipient's, and that the list survives a push going
 * nowhere — which is the whole reason a notification is a row before it is a push.
 */
class NotificationControllerTest : ApplicationTest() {

    // ----------------------------------------------------------- authorisation

    @Test
    fun `notifications are closed to anonymous callers`() = runTest {
        client.getNotificationsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.registerDeviceRaw("some-token").apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.markAllNotificationsReadRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.clearAllNotificationsRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // ----------------------------------------------------------------- devices

    @Test
    fun `registering the same token twice leaves one device`() = runTestAsUser {
        client.registerDevice("token-a")
        client.registerDevice("token-a")

        assertEquals(1, QDevice().token.eq("token-a").findCount())
    }

    /**
     * The case the uniqueness is actually for: a handset that changes hands must stop
     * pushing the previous account's notifications to whoever holds it now.
     */
    @Test
    fun `a token registered by someone else moves to the new owner`() = runTest {
        val second = "second@mail.com"
        runAsAdmin { client.createUser(second) }

        runAsUser1 { client.registerDevice("shared-handset") }
        val firstOwner = QDevice().token.eq("shared-handset").findOne()!!.user!!.id

        runAs(second, "password") { client.registerDevice("shared-handset") }

        val devices = QDevice().token.eq("shared-handset").findList()
        assertEquals(1, devices.size, "a token is one device, whoever registered it")
        assertTrue(devices.single().user!!.id != firstOwner, "the device moved to the new owner")
    }

    @Test
    fun `registration records the platform and the locale the client is running in`() = runTestAsUser {
        client.registerDevice("token-fr", platform = DevicePlatform.IOS, locale = Locale.FR)

        val device = QDevice().token.eq("token-fr").findOne()
        assertNotNull(device)
        assertEquals(DevicePlatform.IOS, device.platform)
        assertEquals(Locale.FR, device.locale)
    }

    @Test
    fun `unregistering forgets the device`() = runTestAsUser {
        client.registerDevice("token-b")
        assertEquals(HttpStatusCode.OK, client.unregisterDeviceRaw("token-b").status)
        assertFalse(QDevice().token.eq("token-b").exists())
    }

    /** Knowing a token must not be enough to unsubscribe somebody else's device. */
    @Test
    fun `a device cannot be unregistered by another user`() = runTest {
        val second = "other@mail.com"
        runAsAdmin { client.createUser(second) }
        runAsUser1 { client.registerDevice("token-c") }

        runAs(second, "password") {
            assertEquals(HttpStatusCode.NotFound, client.unregisterDeviceRaw("token-c").status)
        }
        assertTrue(QDevice().token.eq("token-c").exists())
    }

    // ----------------------------------------------------------------- reading

    @Test
    fun `a fresh account has nothing waiting`() = runTestAsUser {
        val notifications = client.getNotifications()

        assertEquals(emptyList(), notifications.notifications)
        assertEquals(0, notifications.unreadCount)
        assertEquals(emptyList(), notifications.followersPending)
    }

    /**
     * The point of storing before pushing: the recipient finds the notification whether or
     * not their device could be reached.
     */
    @Test
    fun `a notification is listed even when the push failed`() = runTestAsAdmin {
        client.registerDevice("dead-token")
        fakePushSender.failing = true

        val result = client.sendTestNotification(title = "Still here", body = "In the list")
        assertEquals(0, result.pushed)
        assertEquals(1, result.failed)

        val listed = client.listNotifications()
        assertEquals(1, listed.size)
        assertEquals("Still here", listed.single().title)
        assertFalse(listed.single().read)
    }

    @Test
    fun `an announcement arrives with no actor`() = runTestAsAdmin {
        client.registerDevice("token-d")
        client.sendTestNotification(title = "Notice", body = "Body")

        val notification = client.listNotifications().single()
        assertEquals(NotificationKind.ANNOUNCEMENT, notification.kind)
        assertNull(notification.actor, "nobody caused an announcement")
    }

    // ------------------------------------------------------------ marking read

    @Test
    fun `marking one read drops it out of the unread count`() = runTestAsAdmin {
        client.registerDevice("token-e")
        client.sendTestNotification(title = "One", body = "Body")
        client.sendTestNotification(title = "Two", body = "Body")
        assertEquals(2, client.getNotifications().unreadCount)

        val first = client.listNotifications().first()
        assertEquals(HttpStatusCode.OK, client.markNotificationReadRaw(first.id).status)

        val after = client.getNotifications()
        assertEquals(1, after.unreadCount)
        assertTrue(after.notifications.single { it.id == first.id }.read)
    }

    @Test
    fun `marking all read leaves nothing unread`() = runTestAsAdmin {
        client.registerDevice("token-f")
        client.sendTestNotification(title = "One", body = "Body")
        client.sendTestNotification(title = "Two", body = "Body")

        assertEquals(HttpStatusCode.OK, client.markAllNotificationsReadRaw().status)

        val after = client.getNotifications()
        assertEquals(0, after.unreadCount)
        assertTrue(after.notifications.all { it.read })
    }

    /** A notification belongs to its recipient; another account cannot even see its id. */
    @Test
    fun `a notification cannot be marked read by another user`() = runTest {
        var notificationId = 0L
        runAsAdmin {
            client.registerDevice("token-g")
            client.sendTestNotification(title = "Private", body = "Body")
            notificationId = client.listNotifications().single().id
        }

        runAsUser1 {
            assertEquals(HttpStatusCode.NotFound, client.markNotificationReadRaw(notificationId).status)
            assertEquals(emptyList(), client.listNotifications())
        }
    }

    // ---------------------------------------------------------------- clearing

    /**
     * Clearing is a delete, not a flag: what is asserted is that the row is gone, because
     * that is what keeps the unread count right without anything being told about it.
     */
    @Test
    fun `clearing one takes it out of the list and out of the unread count`() = runTestAsAdmin {
        client.registerDevice("token-h")
        client.sendTestNotification(title = "One", body = "Body")
        client.sendTestNotification(title = "Two", body = "Body")

        val first = client.listNotifications().first()
        assertEquals(HttpStatusCode.OK, client.clearNotificationRaw(first.id).status)

        val after = client.getNotifications()
        assertEquals(1, after.notifications.size)
        assertTrue(after.notifications.none { it.id == first.id })
        assertEquals(1, after.unreadCount, "an unread notification cleared is one fewer unread")
        assertFalse(QNotification().id.eq(first.id).exists(), "the row is gone, not hidden")
    }

    /** Clearing the same notification twice is the client's own retry, and says so. */
    @Test
    fun `clearing one that is already gone is a 404`() = runTestAsAdmin {
        client.registerDevice("token-i")
        client.sendTestNotification(title = "Once", body = "Body")
        val id = client.listNotifications().single().id

        assertEquals(HttpStatusCode.OK, client.clearNotificationRaw(id).status)
        assertEquals(HttpStatusCode.NotFound, client.clearNotificationRaw(id).status)
    }

    /** Read or not: clearing says the user is done with the list, not that they read it. */
    @Test
    fun `clearing all empties the list whether or not it was read`() = runTestAsAdmin {
        client.registerDevice("token-j")
        client.sendTestNotification(title = "Read", body = "Body")
        client.sendTestNotification(title = "Unread", body = "Body")
        client.markNotificationReadRaw(client.listNotifications().first().id)

        assertEquals(HttpStatusCode.OK, client.clearAllNotificationsRaw().status)

        val after = client.getNotifications()
        assertEquals(emptyList(), after.notifications)
        assertEquals(0, after.unreadCount)
    }

    /**
     * The half of the bell clearing must not touch.
     *
     * A follow request is a queue the user has to answer, not news, so clearing the
     * notification that announced one leaves the request itself waiting — otherwise "clear
     * all" would quietly decline everybody.
     */
    @Test
    fun `clearing all leaves follow requests waiting`() = runTest {
        val privateAccount = "private@mail.com"
        val requester = "requester@mail.com"
        var privateId = 0L
        runAsAdmin {
            privateId = client.createUser(privateAccount).id
            client.createUser(requester)
            userService.updateSettings(
                privateId,
                UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = false),
            )
        }

        runAs(requester, "password") { client.follow(privateId) }
        notificationService.awaitDispatches()

        runAs(privateAccount, "password") {
            assertEquals(1, client.getNotifications().notifications.size)
            assertEquals(HttpStatusCode.OK, client.clearAllNotificationsRaw().status)

            val after = client.getNotifications()
            assertEquals(emptyList(), after.notifications)
            assertEquals(1, after.followersPending.size, "the request is still there to answer")
        }
    }

    /** Someone else's notification is not the caller's to clear, and stays where it is. */
    @Test
    fun `a notification cannot be cleared by another user`() = runTest {
        var notificationId = 0L
        runAsAdmin {
            client.registerDevice("token-k")
            client.sendTestNotification(title = "Private", body = "Body")
            notificationId = client.listNotifications().single().id
        }

        runAsUser1 {
            assertEquals(HttpStatusCode.NotFound, client.clearNotificationRaw(notificationId).status)
        }
        assertTrue(QNotification().id.eq(notificationId).exists(), "it is still its owner's")
    }
}
