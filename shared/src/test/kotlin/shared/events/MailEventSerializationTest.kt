package shared.events

import kotlinx.serialization.json.Json
import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.enums.MailPlaceholder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * This JSON is the contract between the backend and mail-service. The two are deployed
 * separately, nothing else exercises it, and a break in it shows up as mail quietly
 * stopping rather than as anything failing.
 */
class MailEventSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val event = UserMailRequestedEvent(
        templateKey = EmailTemplateKind.NEW_RECIPE.key,
        locale = Locale.FR,
        encryptedRecipient = "ZmFrZS1jaXBoZXJ0ZXh0",
        recipientId = 7,
        dedupeKey = "new_recipe:42:7",
        values = mapOf(
            MailPlaceholder.USERNAME to "alice",
            MailPlaceholder.TITLE to "Tarte",
            MailPlaceholder.LINK to "https://example.test/recipe/view?id=42",
        ),
    )

    @Test
    fun `round trips through the polymorphic base class`() {
        // mail-service decodes as CookncoEvent, never as the concrete type
        assertEquals(event, json.decodeFromString<CookncoEvent>(json.encodeToString<CookncoEvent>(event)))
    }

    @Test
    fun `carries the discriminator the consumer dispatches on`() {
        assertTrue(json.encodeToString<CookncoEvent>(event).contains("\"user_mail_requested\""))
    }

    @Test
    fun `goes to the topic mail-service subscribes to, keyed per recipient`() {
        assertEquals(MailEvent.MAILS_TOPIC, event.getTopic())
        assertEquals("7", event.getKey())
    }

    @Test
    fun `carries no plaintext address`() {
        // The address is encrypted before it reaches the topic, so a broker dump is not a
        // mailing list. Only mail-service, which holds the key, can read it back.
        assertTrue(!json.encodeToString<CookncoEvent>(event).contains("@"))
    }

    @Test
    fun `a value added by a newer producer does not break an older consumer`() {
        val fromNewerProducer = """
            {"type":"user_mail_requested","templateKey":"new_recipe","locale":"FR",
             "encryptedRecipient":"x","recipientId":7,"dedupeKey":"k","values":{},
             "somethingAddedLater":"value"}
        """.trimIndent()
        assertTrue(json.decodeFromString<CookncoEvent>(fromNewerProducer) is UserMailRequestedEvent)
    }
}
