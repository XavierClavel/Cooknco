package main.com.xavierclavel.other

import com.xavierclavel.ApplicationTest
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import main.com.xavierclavel.utils.chooseLocale
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.createUserRawLocale
import main.com.xavierclavel.utils.getSettings
import main.com.xavierclavel.utils.login
import main.com.xavierclavel.utils.logout
import main.com.xavierclavel.utils.registerDevice
import main.com.xavierclavel.utils.updateSettings
import main.com.xavierclavel.utils.userService
import org.junit.jupiter.api.Test
import shared.dto.UserSettingsDTO
import shared.enums.Locale
import shared.utils.URL.USER_URL
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The language an account is written to in.
 *
 * `users.locale` spent its whole life non-null with a default of FR and no writer at all,
 * which is why every mail this product sent went out in French whoever read it. It is now
 * nullable — "nobody has told us" being a real state — and this is what fills it: a client
 * reporting at signup, at sign-in or when registering a device, and the user saying so
 * themselves in the settings.
 *
 * The one rule underneath all of it is that a report never corrects a value already there.
 * A client says what machine it is; only the account says what the person reads.
 */
class UserLocaleTest : ApplicationTest() {

    private val password = "password"

    // ------------------------------------------------------------------ signup

    @Test
    fun `signing up records the language the client reported`() = runTest {
        val mail = "reports-french@mail.com"
        runAsAdmin { client.createUser(mail, locale = Locale.FR) }

        runAs(mail, password) {
            assertEquals(Locale.FR, client.getSettings().locale)
        }
    }

    @Test
    fun `signing up without reporting one leaves the account without a language`() = runTest {
        val mail = "reports-nothing@mail.com"
        runAsAdmin { client.createUser(mail, locale = null) }

        runAs(mail, password) {
            assertNull(
                client.getSettings().locale,
                "nothing has said anything, and a default would be a guess dressed as a fact",
            )
        }
    }

    /**
     * An account is worth more than a preference: a client that spells the language wrong
     * gets an account without one, not a 400. See `AuthController.signup`.
     */
    @Test
    fun `a language the client spelled wrong does not cost it the account`() = runTest {
        val mail = "garbled@mail.com"
        runAsAdmin { client.createUserRawLocale(mail, "klingon") }

        runAs(mail, password) {
            assertNull(client.getSettings().locale, "unreadable is unknown, and unknown falls back")
        }
    }

    // ---------------------------------------------------------------- settings

    @Test
    fun `choosing a language in the settings saves it on the account`() = runTest {
        val mail = "chooses@mail.com"
        runAsAdmin { client.createUser(mail, locale = Locale.EN) }

        runAs(mail, password) {
            client.chooseLocale(Locale.FR)
            assertEquals(Locale.FR, client.getSettings().locale)
        }
    }

    /**
     * The reason `UserSettingsDTO.locale` is nullable on the way in: a client that predates
     * the field sends a body without it, and saving the other settings from such a client
     * must not wipe a language chosen from one that does know about it.
     */
    @Test
    fun `a settings save that says nothing about the language leaves it alone`() = runTest {
        val mail = "old-client@mail.com"
        runAsAdmin { client.createUser(mail, locale = Locale.EN) }

        runAs(mail, password) {
            client.chooseLocale(Locale.FR)

            // Exactly what a client with no notion of the field puts on the wire - a typed
            // helper cannot express it, since a Kotlin caller always has the property
            client.put("$USER_URL/settings") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                setBody("""{"autoAcceptFollowRequests":true,"isAccountPublic":true}""")
            }.apply { assertEquals(HttpStatusCode.OK, status) }

            assertEquals(Locale.FR, client.getSettings().locale)
        }
    }

    // ------------------------------------------------------------------- login

    @Test
    fun `signing in gives an account with no language the one its client reports`() = runTest {
        val mail = "learns-at-login@mail.com"
        runAsAdmin { client.createUser(mail, locale = null) }

        client.login(mail, password, locale = Locale.FR)
        assertEquals(Locale.FR, client.getSettings().locale)
        client.logout()
    }

    @Test
    fun `signing in elsewhere does not rewrite a language the user chose`() = runTest {
        val mail = "chose-french@mail.com"
        runAsAdmin { client.createUser(mail, locale = null) }

        runAs(mail, password) { client.chooseLocale(Locale.FR) }

        // The same account, from a borrowed machine that is in English
        client.login(mail, password, locale = Locale.EN)
        assertEquals(
            Locale.FR,
            client.getSettings().locale,
            "a browser's language is not a decision the user made",
        )
        client.logout()
    }

    // ------------------------------------------------------------------ device

    @Test
    fun `registering a device gives an account with no language the phone's`() = runTest {
        val mail = "learns-from-phone@mail.com"
        runAsAdmin { client.createUser(mail, locale = null) }

        runAs(mail, password) {
            client.registerDevice("a-french-phone", locale = Locale.FR)
            assertEquals(Locale.FR, client.getSettings().locale)
        }
    }

    /**
     * The handset and the person are two different questions, and the device keeps
     * answering its own: `devices.locale` is what an announcement's language filter reads
     * for an account that has none, and what the backoffice shows about the install.
     */
    @Test
    fun `a phone in another language does not rewrite the account, and still records its own`() = runTest {
        val mail = "travels@mail.com"
        var id = 0L
        runAsAdmin { id = client.createUser(mail, locale = Locale.EN).id }

        runAs(mail, password) {
            client.registerDevice("a-french-phone", locale = Locale.FR)
            assertEquals(Locale.EN, client.getSettings().locale)
        }

        assertEquals(
            Locale.FR,
            com.xavierclavel.models.query.QDevice().token.eq("a-french-phone").findOne()!!.locale,
            "the device still says what it is",
        )
        assertEquals(Locale.EN, userService.getEntityById(id).locale)
    }

    @Test
    fun `the settings a user saves are untouched by all of this`() = runTest {
        val mail = "settings-intact@mail.com"
        runAsAdmin { client.createUser(mail, locale = Locale.EN) }

        runAs(mail, password) {
            client.updateSettings(UserSettingsDTO(autoAcceptFollowRequests = true, isAccountPublic = false))
            client.getSettings().apply {
                assertEquals(true, autoAcceptFollowRequests)
                assertEquals(false, isAccountPublic)
                assertEquals(Locale.EN, locale)
            }
        }
    }
}
