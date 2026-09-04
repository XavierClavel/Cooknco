package shared.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import shared.enums.Locale

@Serializable
sealed class MailEvent(): CookncoEvent() {
    override fun getTopic() = "cooknco-mails"
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
