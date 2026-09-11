package shared.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import shared.enums.Locale

@Serializable
sealed class MailEvent(): CookncoEvent() {
    override fun getTopic() = MAILS_TOPIC

    companion object {
        const val MAILS_TOPIC = "cooknco-mails"
    }
}

/**
 * A mail the application decided to send to one person.
 *
 * Like [TestMailRequestedEvent] it names no user: the address, the reading locale and every
 * value the wording fills in travel with it, the address encrypted under the same key. That
 * is what lets mail-service hold no copy of `users` and `followers` — whoever decided to
 * send this already knew who it was for, and is the only side that can know it reliably.
 *
 * Delivery is at-least-once, so a redelivery can re-send a mail that already went out.
 * [dedupeKey] is what a delivery log would key on to make it exactly-once.
 */
@Serializable
@SerialName("user_mail_requested")
data class UserMailRequestedEvent(
    val templateKey: String,
    val locale: Locale,
    val encryptedRecipient: String,
    /** Recipient id, for the logs — never for a lookup. */
    val recipientId: Long,
    /** `<key>:<subject>:<recipient>`, identical on every attempt. */
    val dedupeKey: String,
    /** The `{{placeholders}}` the wording may fill in, already resolved. */
    val values: Map<String, String> = emptyMap(),
) : MailEvent() {
    // Keyed per recipient, so two mails to the same person keep their order.
    override fun getKey() = recipientId.toString()
}

/**
 * A one-off mail an operator asked the backoffice for, to see a template land in a real
 * inbox rather than only in a preview.
 *
 * It goes through the same topic and the same renderer as every other mail, which is the
 * point: a test that took a shortcut would prove nothing about the real path. It names no
 * user — the recipient is whatever address was typed, encrypted with the same key as the
 * addresses on [UserCreatedEvent] so no plaintext mail sits in the topic.
 */
@Serializable
@SerialName("test_mail_requested")
data class TestMailRequestedEvent(
    val templateKey: String,
    val locale: Locale,
    val encryptedRecipient: String,
) : MailEvent()
