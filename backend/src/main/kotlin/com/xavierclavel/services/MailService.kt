package com.xavierclavel.services

import com.xavierclavel.models.User
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.enums.MailPlaceholder
import shared.events.EventProducer
import shared.events.UserMailRequestedEvent

/**
 * Asks mail-service to write to someone.
 *
 * Everything the mail needs is resolved here and put on the event: the address, the locale
 * to read it in, and the values its wording fills in. mail-service therefore keeps no copy
 * of `users` or `follows` to look any of it up in — a copy that could only ever be a stale
 * second opinion about data this side owns.
 *
 * The wording itself is not here. That belongs to [EmailTemplateService], which an operator
 * edits and mail-service reads through, so a mail can be reworded without a deploy.
 */
class MailService: KoinComponent {
    private val configuration: Configuration by inject()
    private val userService: UserService by inject()
    private val unsubscribeService: UnsubscribeService by inject()
    private val eventProducer: EventProducer by inject()

    /**
     * Sends [kind] to [recipient], unless they have switched that sort of mail off.
     *
     * [target] is what the mail's link is about — a one-use token, or the id of the recipe
     * being announced. [values] carries whatever else the wording names.
     */
    fun send(
        kind: EmailTemplateKind,
        recipient: User,
        target: String,
        values: Map<String, String> = emptyMap(),
    ) = sendAll(kind, listOf(recipient), target, values)

    /**
     * The same mail to many people, in the language each of them reads.
     *
     * One query resolves every recipient's language, rather than one per recipient: this is
     * the path a popular author's followers come down, and asking per recipient would run a
     * query per mail before a single one was sent.
     */
    fun sendAll(
        kind: EmailTemplateKind,
        recipients: List<User>,
        target: String,
        values: Map<String, String> = emptyMap(),
    ) {
        // A notification mail waits to be asked for, and a banned account is written to
        // about its own credentials and nothing else. Neither ever gates an account mail.
        val addressees = recipients.filter {
            kind.isTransactional || (it.mailNotificationsEnabled && !it.isBanned)
        }
        if (addressees.isEmpty()) return

        // Resolved exactly as a notification's is, and for the whole audience at once —
        // see UserService.readingLocalesOf. mail-service is told the answer rather than
        // working it out: it has no devices and no requests to infer one from.
        val reading = userService.readingLocalesOf(addressees)
        val link = kind.link(configuration.frontend.url, target)

        addressees.forEach { recipient ->
            eventProducer.produceEvent {
                UserMailRequestedEvent(
                    templateKey = kind.key,
                    locale = reading[recipient.id] ?: Locale.FR,
                    encryptedRecipient = recipient.mailEncrypted,
                    recipientId = recipient.id,
                    dedupeKey = "${kind.key}:$target:${recipient.id}",
                    values = values + (MailPlaceholder.LINK to link) + unsubscribeValue(kind, recipient),
                )
            }
        }
        logger.info { "Requested ${kind.key} mail for ${addressees.size} user(s)" }
    }

    /**
     * The way out of these mails, for the mails there is a way out of.
     *
     * Resolved per recipient rather than once per send, unlike the link above: it names the
     * account, and is the only thing in a notification mail that has to.
     *
     * A transactional mail gets nothing, matching the placeholder its kind declares — an
     * unsubscribe offered on a password reset is one we would not honour, since that mail
     * goes out whatever the setting says.
     */
    private fun unsubscribeValue(kind: EmailTemplateKind, recipient: User): Map<String, String> =
        if (kind.isTransactional) emptyMap()
        else mapOf(MailPlaceholder.UNSUBSCRIBE to unsubscribeService.linkFor(recipient.id))
}
