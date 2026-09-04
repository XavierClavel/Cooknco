package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.models.query.QEmailTemplate
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.addMailTemplateRaw
import main.com.xavierclavel.utils.deleteMailTemplateRaw
import main.com.xavierclavel.utils.getInternalMailTemplates
import main.com.xavierclavel.utils.getInternalMailTemplatesRaw
import main.com.xavierclavel.utils.listMailTemplates
import main.com.xavierclavel.utils.listMailTemplatesRaw
import main.com.xavierclavel.utils.mailTemplate
import main.com.xavierclavel.utils.previewMailTemplate
import main.com.xavierclavel.utils.previewMailTemplateRaw
import main.com.xavierclavel.utils.restoreMailTemplateRaw
import main.com.xavierclavel.utils.saveMailTemplateRaw
import main.com.xavierclavel.utils.sendTestMailRaw
import org.junit.jupiter.api.Test
import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.enums.MailPlaceholder
import shared.events.TestMailRequestedEvent
import shared.utils.EmailTemplates
import shared.utils.URL.ADMIN_URL
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wordings the app sends its mails with.
 *
 * They work like the backoffice-managed default images: what an operator saves is an
 * override, the copy packaged in `shared` is the floor, and restoring one is a delete. The
 * two things worth pinning down beyond that are what mail-service is told — the internal
 * endpoint has to report overrides and nothing else — and that a wording cannot be saved
 * in a state that would send a mail nobody can act on.
 */
class AdminMailTemplateControllerTest : ApplicationTest() {

    private val reset = EmailTemplateKind.PASSWORD_RESET.key
    private val verification = EmailTemplateKind.ACCOUNT_VERIFICATION.key
    private val link = "{{${MailPlaceholder.LINK}}}"

    // ----------------------------------------------------------- authorisation

    @Test
    fun `mail templates are closed to anonymous callers`() = runTest {
        client.listMailTemplatesRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.saveMailTemplateRaw(reset, Locale.EN, "Hi", link)
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.addMailTemplateRaw("welcome_back").apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.sendTestMailRaw(reset, "someone@example.com")
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `mail templates are closed to regular users`() = runTestAsUser {
        client.listMailTemplatesRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.saveMailTemplateRaw(reset, Locale.EN, "Hi", link)
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    /**
     * mail-service is not a user and holds no session, so this one endpoint has to stay
     * outside the gate. What keeps it private is the network: nginx proxies `/api/` and
     * `/image/` only, so nothing outside the cluster can reach this prefix at all.
     */
    @Test
    fun `what mail-service reads is not behind the admin session`() = runTest {
        assertEquals(HttpStatusCode.OK, client.getInternalMailTemplatesRaw().status)
    }

    // ---------------------------------------------------------------- listing

    @Test
    fun `an untouched database sends the wordings packaged with the app`() = runTestAsAdmin {
        val templates = client.listMailTemplates()

        assertEquals(EmailTemplateKind.entries.map { it.key }, templates.map { it.key })
        templates.forEach { template ->
            assertTrue(template.builtIn, "${template.key} is emitted by the app")
            assertEquals(listOf(MailPlaceholder.LINK), template.placeholders)
            assertEquals(Locale.entries, template.locales.map { it.locale })
            template.locales.forEach {
                assertFalse(it.custom, "${template.key}/${it.locale} has nothing saved over it")
                assertNull(it.updatedAt)
                // The packaged wording is what is actually being sent, so it is what is shown
                assertTrue(it.subject.isNotBlank(), "${template.key}/${it.locale} needs a subject")
                assertContains(it.body, link, message = "${template.key}/${it.locale} needs its link")
            }
        }
    }

    @Test
    fun `mail-service is told about nothing until a wording is saved`() = runTestAsAdmin {
        assertTrue(client.getInternalMailTemplates().isEmpty())
    }

    @Test
    fun `a mail nothing stands for is not found`() = runTestAsAdmin {
        assertEquals(HttpStatusCode.NotFound, client.saveMailTemplateRaw("nonsense", Locale.EN, "Hi", "body").status)
        assertEquals(HttpStatusCode.NotFound, client.previewMailTemplateRaw("nonsense", "Hi", "body").status)
        assertEquals(HttpStatusCode.NotFound, client.sendTestMailRaw("nonsense", "someone@example.com").status)
    }

    // ---------------------------------------------------------------- saving

    @Test
    fun `a saved wording replaces the packaged one, in that locale only`() = runTestAsAdmin {
        val packaged = client.mailTemplate(reset).locales.first { it.locale == Locale.FR }

        client.saveMailTemplateRaw(reset, Locale.EN, "Reset your password", "<div>Go to $link</div>")
            .apply { assertEquals(HttpStatusCode.OK, status) }

        val template = client.mailTemplate(reset)
        val en = template.locales.first { it.locale == Locale.EN }
        assertTrue(en.custom)
        assertEquals("Reset your password", en.subject)
        assertNotNull(en.updatedAt)
        // Wordings are saved one language at a time: the other must be exactly as it was
        assertEquals(packaged, template.locales.first { it.locale == Locale.FR })
    }

    @Test
    fun `a saved wording is what mail-service is told to send`() = runTestAsAdmin {
        client.saveMailTemplateRaw(verification, Locale.FR, "Vérifie ton compte", "<div>$link</div>")

        val effective = client.getInternalMailTemplates()
        assertEquals(1, effective.size, "only the saved wording travels, never the packaged one")
        assertEquals(verification, effective.first().key)
        assertEquals(Locale.FR, effective.first().locale)
        assertEquals("Vérifie ton compte", effective.first().subject)
    }

    @Test
    fun `a wording that drops the link is refused`() = runTestAsAdmin {
        val response = client.saveMailTemplateRaw(reset, Locale.EN, "Reset", "<div>Nothing to click</div>")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("mail_template_missing_placeholder", response.bodyAsText())
        assertFalse(client.mailTemplate(reset).locales.first { it.locale == Locale.EN }.custom)
    }

    @Test
    fun `an empty wording is refused`() = runTestAsAdmin {
        client.saveMailTemplateRaw(reset, Locale.EN, "   ", link)
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
        client.saveMailTemplateRaw(reset, Locale.EN, "Reset", "  ")
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    @Test
    fun `a wording longer than the column is refused rather than truncated`() = runTestAsAdmin {
        val body = link + "x".repeat(EmailTemplates.MAX_BODY_LENGTH)

        val response = client.saveMailTemplateRaw(reset, Locale.EN, "Reset", body)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("mail_template_too_long", response.bodyAsText())
    }

    // --------------------------------------------------------------- restoring

    @Test
    fun `restoring a wording puts the packaged one back`() = runTestAsAdmin {
        val packaged = client.mailTemplate(reset).locales.first { it.locale == Locale.EN }
        client.saveMailTemplateRaw(reset, Locale.EN, "Reset", "<div>$link</div>")

        client.restoreMailTemplateRaw(reset, Locale.EN).apply { assertEquals(HttpStatusCode.OK, status) }

        assertEquals(packaged, client.mailTemplate(reset).locales.first { it.locale == Locale.EN })
        assertTrue(client.getInternalMailTemplates().isEmpty(), "mail-service falls back to its own copy")
    }

    @Test
    fun `restoring a wording nobody saved reports nothing to do`() = runTestAsAdmin {
        assertEquals(HttpStatusCode.NotFound, client.restoreMailTemplateRaw(reset, Locale.EN).status)
    }

    @Test
    fun `a locale nothing stands for is refused`() = runTestAsAdmin {
        assertEquals(HttpStatusCode.BadRequest, client.delete("$ADMIN_URL/mails/templates/$reset/klingon").status)
    }

    // ------------------------------------------------------------ custom kinds

    @Test
    fun `a mail an operator adds starts as a draft in every locale`() = runTestAsAdmin {
        client.addMailTemplateRaw("welcome_back").apply { assertEquals(HttpStatusCode.Created, status) }

        val template = client.mailTemplate("welcome_back")
        assertFalse(template.builtIn)
        // Nothing declares placeholders for a kind no code emits, so nothing is required of it
        assertTrue(template.placeholders.isEmpty())
        assertEquals(Locale.entries, template.locales.map { it.locale })
        template.locales.forEach {
            assertTrue(it.custom, "a custom kind is only ever its saved rows")
            assertTrue(it.body.isNotBlank())
        }
        assertEquals(Locale.entries.size, client.getInternalMailTemplates().size)
    }

    @Test
    fun `a custom wording may say anything, since nothing fills it in`() = runTestAsAdmin {
        client.addMailTemplateRaw("welcome_back")

        client.saveMailTemplateRaw("welcome_back", Locale.EN, "Welcome back", "<div>Nothing to click</div>")
            .apply { assertEquals(HttpStatusCode.OK, status) }
    }

    @Test
    fun `a key that is not one is refused`() = runTestAsAdmin {
        listOf("we", "welcome-back", "1welcome", "welcome back", "").forEach {
            val response = client.addMailTemplateRaw(it)
            assertEquals(HttpStatusCode.BadRequest, response.status, "\"$it\" is not a usable key")
            assertEquals("mail_template_key_invalid", response.bodyAsText())
        }
    }

    /** Case and stray spaces are the operator's typing, not a different key. */
    @Test
    fun `a key is taken as written in lower case`() = runTestAsAdmin {
        client.addMailTemplateRaw("  Welcome_Back  ").apply { assertEquals(HttpStatusCode.Created, status) }

        assertTrue(client.listMailTemplates().any { it.key == "welcome_back" })
        client.addMailTemplateRaw("WELCOME_BACK").apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    @Test
    fun `a key already in use is refused`() = runTestAsAdmin {
        client.addMailTemplateRaw(reset).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("mail_template_already_exists", bodyAsText())
        }

        client.addMailTemplateRaw("welcome_back")
        client.addMailTemplateRaw("welcome_back").apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    @Test
    fun `removing a custom mail removes every locale`() = runTestAsAdmin {
        client.addMailTemplateRaw("welcome_back")

        client.deleteMailTemplateRaw("welcome_back").apply { assertEquals(HttpStatusCode.OK, status) }

        assertTrue(client.listMailTemplates().none { it.key == "welcome_back" })
        assertTrue(client.getInternalMailTemplates().isEmpty())
    }

    @Test
    fun `a built-in mail cannot be removed`() = runTestAsAdmin {
        val response = client.deleteMailTemplateRaw(reset)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("mail_template_is_built_in", response.bodyAsText())
        assertTrue(client.listMailTemplates().any { it.key == reset })
    }

    @Test
    fun `a custom mail has no packaged wording to restore`() = runTestAsAdmin {
        client.addMailTemplateRaw("welcome_back")

        val response = client.restoreMailTemplateRaw("welcome_back", Locale.EN)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("mail_template_has_no_packaged_wording", response.bodyAsText())
    }

    /**
     * A custom kind normally has a row per locale, since adding one writes them all. It can
     * still come up short: the day [Locale] grows an entry, every kind added before it will
     * be missing that one, and the tab has to say so rather than imply a mail is ready.
     */
    @Test
    fun `a custom mail with no wording for a locale says there is nothing to send`() = runTestAsAdmin {
        client.addMailTemplateRaw("welcome_back")
        QEmailTemplate().key.eq("welcome_back").locale.eq(Locale.EN).delete()

        val en = client.mailTemplate("welcome_back").locales.first { it.locale == Locale.EN }
        assertFalse(en.custom)
        assertTrue(en.subject.isEmpty())
        assertTrue(en.body.isEmpty())

        client.sendTestMailRaw("welcome_back", "someone@example.com", Locale.EN).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("mail_template_empty", bodyAsText())
        }
    }

    // ---------------------------------------------------------------- preview

    @Test
    fun `a preview fills in what the mail is sent with`() = runTestAsAdmin {
        val preview = client.previewMailTemplate(reset, "Reset your password", "<div>Go to $link</div>")

        // The same expression mail-service builds the real link from, with an inert token
        assertEquals("<div>Go to http://localhost:3000/password/reset/new?token=sample-token</div>", preview.body)
        assertEquals("Reset your password", preview.subject)
        assertTrue(preview.unfilled.isEmpty())
    }

    @Test
    fun `a preview reports a placeholder nothing fills`() = runTestAsAdmin {
        val preview = client.previewMailTemplate(reset, "Reset", "<div>$link {{nickname}}</div>")

        assertEquals(listOf("nickname"), preview.unfilled)
        // Left as written rather than blanked: an operator has to be able to see the mistake
        assertContains(preview.body, "{{nickname}}")
    }

    /** The draft is the point of a preview: it must not read the saved wording back. */
    @Test
    fun `a preview shows the draft, not what is saved`() = runTestAsAdmin {
        client.saveMailTemplateRaw(reset, Locale.EN, "Saved", "<div>saved $link</div>")

        val preview = client.previewMailTemplate(reset, "Draft", "<div>draft $link</div>")

        assertEquals("Draft", preview.subject)
        assertContains(preview.body, "draft")
    }

    // -------------------------------------------------------------- test send

    @Test
    fun `a test mail goes out through mail-service like any other`() = runTestAsAdmin {
        client.sendTestMailRaw(reset, "someone@example.com", Locale.FR)
            .apply { assertEquals(HttpStatusCode.Accepted, status) }

        val event = mockEventProducer.eventsProduced.filterIsInstance<TestMailRequestedEvent>().single()
        assertEquals(reset, event.templateKey)
        assertEquals(Locale.FR, event.locale)
        // Encrypted with the same key as the addresses on the other events: no plaintext in Kafka
        assertEquals("someone@example.com", encryptionService.decrypt(event.encryptedRecipient))
    }

    @Test
    fun `a test mail to something that is not an address is refused`() = runTestAsAdmin {
        listOf("someone", "someone@example", "", "two@addresses@example.com").forEach {
            val response = client.sendTestMailRaw(reset, it)
            assertEquals(HttpStatusCode.BadRequest, response.status, "\"$it\" is not an address")
            assertEquals("invalid_mail_address", response.bodyAsText())
        }
        assertTrue(mockEventProducer.eventsProduced.filterIsInstance<TestMailRequestedEvent>().isEmpty())
    }
}
