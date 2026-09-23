package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.exceptions.UnauthorizedCause
import io.ebean.DB
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.login
import main.com.xavierclavel.utils.logout
import main.com.xavierclavel.utils.requestPasswordReset
import main.com.xavierclavel.utils.resetPassword
import main.com.xavierclavel.utils.signup
import main.com.xavierclavel.utils.updatePassword
import main.com.xavierclavel.utils.verifyUser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import shared.enums.EmailTemplateKind
import shared.events.UserCreatedEvent
import shared.events.UserMailRequestedEvent
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthControllerTest : ApplicationTest() {

    @Test
    fun `signup does not grant access before email verification`() = runTest {
        val password = UUID.randomUUID().toString()
        val mail = "user@mail.com"
        val user = client.signup(mail = mail, password = password)
        client.login(mail, password).apply {
            assertEquals(status, HttpStatusCode.Unauthorized)
            assertEquals(bodyAsText(), UnauthorizedCause.USER_NOT_VERIFIED.key)
        }
    }

    @Test
    fun `access is granted after email verification`() = runTest {
        val password = UUID.randomUUID().toString()
        val user = client.signup(password = password)
        val token = userService.getEntityById(user.id).token
        client.verifyUser(token)
        assertDoesNotThrow { client.login(user.username, password) }
    }

    @Test
    fun `update user password`() = runTest {
        val oldPassword = UUID.randomUUID().toString()
        val mail = "user@mail.fr"
        val user = client.signup(mail = mail, password = oldPassword)
        val token = userService.getEntityById(user.id).token
        client.verifyUser(token)
        assertDoesNotThrow { client.login(mail, oldPassword) }

        val newPassword = UUID.randomUUID().toString()
        client.updatePassword(oldPassword, newPassword)
        client.login(mail, newPassword).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        client.login(mail, oldPassword).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.INVALID_MAIL_OR_PASSWORD.key, bodyAsText())
        }
    }

    @Test
    fun `updating password fails if password provided is wrong`() = runTest {
        val oldPassword = UUID.randomUUID().toString()
        val mail = "myUser@mail.com"
        val user = client.signup(mail=mail, password = oldPassword)
        val token = userService.getEntityById(user.id).token
        client.verifyUser(token)
        client.login(mail, oldPassword)

        val wrongPassword = UUID.randomUUID().toString()
        val newPassword = UUID.randomUUID().toString()
        client.login(mail, oldPassword)

        client.updatePassword(wrongPassword, newPassword).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.INVALID_PASSWORD.key, bodyAsText())
        }

        client.login(mail, oldPassword).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        client.logout().apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.login(mail, newPassword).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.INVALID_MAIL_OR_PASSWORD.key, bodyAsText())
        }
    }

    @Test
    fun `reset user password`() = runTest {
        val oldPassword = UUID.randomUUID().toString()
        val newPassword = UUID.randomUUID().toString()
        val mail = "me@mail.com"
        val user = client.signup(mail = mail, password = oldPassword)
        val token = userService.getEntityById(user.id).token
        client.verifyUser(token)

        client.login(user.username, oldPassword)
        client.logout()

        val decryptedMail = encryptionService.decrypt(userService.getEntityById(user.id).mailEncrypted)
        client.requestPasswordReset(decryptedMail)
        val newToken = userService.getEntityById(user.id).token
        client.resetPassword(newToken, newPassword)

        client.login(mail, newPassword).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
        client.logout()

        client.login(mail, oldPassword).apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.INVALID_MAIL_OR_PASSWORD.key, bodyAsText())
        }
    }

    @Test
    fun `user created event is produced on user signup`() = runTest {
        val password = UUID.randomUUID().toString()
        val mail = UUID.randomUUID().toString()
        val user = client.signup(mail = mail, password = password)
        assertEquals(2, mockEventProducer.eventsProduced.size)
        assertTrue { mockEventProducer.eventsProduced.first() is UserCreatedEvent }
        assertTrue { mockEventProducer.eventsProduced[1] is UserMailRequestedEvent }
        val actualFirst = mockEventProducer.eventsProduced.first() as UserCreatedEvent
        val actualSecond = mockEventProducer.eventsProduced[1] as UserMailRequestedEvent
        assertEquals(actualFirst.id, user.id)
        assertEquals(actualSecond.recipientId, user.id)
        assertEquals(EmailTemplateKind.ACCOUNT_VERIFICATION.key, actualSecond.templateKey)
        // The address travels with the request: this is what lets mail-service keep no users
        assertEquals(mail, encryptionService.decrypt(actualSecond.encryptedRecipient))
    }

    /**
     * The backoffice's "last seen" column reads `users.last_activity_date`, and the stamp
     * used to be set on a loaded entity that was never saved — so the column kept the value
     * it was inserted with and every account read as last seen the day it joined.
     *
     * Backdating the row is what makes that visible: a sign-in seconds after a signup leaves
     * the stamp and the join date within a millisecond of each other whether it is written
     * or not. Raw SQL to set it up, as test fixtures are allowed to.
     */
    @Test
    fun `signing in stamps the account's last connection`() = runTest {
        val password = UUID.randomUUID().toString()
        val mail = "returning@mail.com"
        val user = client.signup(mail = mail, password = password)
        client.verifyUser(userService.getEntityById(user.id).token)

        DB.sqlUpdate("update users set last_activity_date = last_activity_date - interval '30 days' where id = :id")
            .setParameter("id", user.id)
            .execute()
        val backdated = userService.getEntityById(user.id).lastActivityDate

        client.login(mail, password).apply { assertEquals(HttpStatusCode.OK, status) }

        val stamped = userService.getEntityById(user.id).lastActivityDate
        assertTrue(stamped.isAfter(backdated), "signing in left last_activity_date at ${'$'}stamped")
        assertTrue(
            stamped.isAfter(LocalDateTime.now().minusMinutes(1)),
            "last_activity_date should be the moment of the sign-in, was ${'$'}stamped",
        )
    }

    /**
     * The one case where the two dates legitimately agree: the column is seeded at creation,
     * so an account that has never signed in reads as last seen the day it joined rather
     * than as never seen at all.
     */
    @Test
    fun `an account that never signed in reads as last seen the day it joined`() = runTest {
        val user = client.signup(mail = "fresh@mail.com", password = UUID.randomUUID().toString())
        userService.getEntityById(user.id).apply {
            assertEquals(joinDate, lastActivityDate)
        }
    }

}