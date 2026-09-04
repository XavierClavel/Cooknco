package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.EmailTemplate
import com.xavierclavel.models.query.QEmailTemplate
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.dto.EffectiveEmailTemplate
import shared.dto.EmailPreviewDTO
import shared.dto.EmailTemplateDTO
import shared.dto.EmailTestDTO
import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.events.EventProducer
import shared.events.TestMailRequestedEvent
import shared.infodto.AdminEmailTemplateInfo
import shared.infodto.AdminEmailTemplateLocale
import shared.infodto.EmailPreviewInfo
import shared.utils.EmailTemplates

/**
 * The wordings the application sends its mails with.
 *
 * Every mail has two possible sources, in order: the wording an operator saved from the
 * backoffice, and the one packaged in `shared`. The packaged wording is a floor rather than
 * a seed — it is never written to the database — so a fresh install sends something
 * sensible, the tab does not show rows nobody wrote, and restoring a mail is just deleting
 * the override.
 *
 * The mails are sent by mail-service, not here: this owns the text, and [effective] is what
 * mail-service reads it through.
 */
class EmailTemplateService: KoinComponent {
    private val configuration: Configuration by inject()
    private val encryptionService: EncryptionService by inject()
    private val eventProducer: EventProducer by inject()

    companion object {
        /**
         * What a kind an operator adds starts out as.
         *
         * Shaped like the packaged wordings so the format is evident, and obviously a draft
         * so nobody mistakes it for something considered.
         */
        private const val DRAFT_BODY = "<div>Hello!</div>\n<br/>\n<div>Cook&Co team</div>"

        /**
         * A typo guard, not address validation: the address is only ever used to send one
         * test mail to whoever typed it, and refusing valid oddities would help nobody.
         */
        private val MAIL_FORMAT = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }

    // ------------------------------------------------------------------- reading

    /** Built-in kinds first, in the order the app declares them, then whatever was added. */
    fun describe(): List<AdminEmailTemplateInfo> {
        val overrides = findOverrides()
        val builtIn = EmailTemplateKind.entries.map { it.key }
        val custom = overrides.keys.map { it.first }.distinct().filterNot { it in builtIn }.sorted()
        return (builtIn + custom).map { describe(it, overrides) }
    }

    fun describe(key: String): AdminEmailTemplateInfo {
        val overrides = findOverrides()
        if (EmailTemplateKind.of(key) == null && overrides.keys.none { it.first == key }) {
            throw NotFoundException(NotFoundCause.MAIL_TEMPLATE_NOT_FOUND)
        }
        return describe(key, overrides)
    }

    private fun describe(key: String, overrides: Map<Pair<String, Locale>, EmailTemplate>): AdminEmailTemplateInfo {
        val kind = EmailTemplateKind.of(key)
        return AdminEmailTemplateInfo(
            key = key,
            builtIn = kind != null,
            placeholders = kind?.placeholders ?: emptyList(),
            locales = Locale.entries.map { locale ->
                overrides[key to locale]?.toInfo() ?: packagedInfo(kind, locale)
            },
        )
    }

    /**
     * What is sent for a locale nobody has saved a wording for.
     *
     * A built-in falls back to the jar. A custom kind has nothing to fall back to, so it
     * reports an empty wording: the tab shows there is nothing to send, which is the truth.
     */
    private fun packagedInfo(kind: EmailTemplateKind?, locale: Locale): AdminEmailTemplateLocale {
        val (subject, body) = kind?.let { EmailTemplates.packaged(it, locale) } ?: ("" to "")
        return AdminEmailTemplateLocale(locale = locale, subject = subject, body = body, custom = false)
    }

    private fun findOverrides(): Map<Pair<String, Locale>, EmailTemplate> =
        QEmailTemplate().findList().associateBy { it.key to it.locale }

    private fun findOverride(key: String, locale: Locale): EmailTemplate? =
        QEmailTemplate().key.eq(key).locale.eq(locale).findOne()

    // ------------------------------------------------------------------- writing

    /** Saves a wording over whatever was being sent, packaged or not. */
    fun save(key: String, locale: Locale, dto: EmailTemplateDTO): AdminEmailTemplateInfo {
        val kind = requireKnown(key)
        validate(kind, dto.subject, dto.body)

        val template = findOverride(key, locale) ?: EmailTemplate(key = key, locale = locale)
        template.subject = dto.subject.trim()
        template.body = dto.body.trim()
        template.save()

        logger.info { "Mail template $key/$locale saved" }
        return describe(key)
    }

    /**
     * Drops an override, which puts the packaged wording back in service.
     *
     * @return false when there was no override to remove
     */
    fun restore(key: String, locale: Locale): Boolean {
        // A custom kind has nothing to fall back to: dropping its row would leave it half-defined
        if (requireKnown(key) == null) throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_HAS_NO_PACKAGED_WORDING)
        val removed = findOverride(key, locale)?.delete() ?: false
        if (removed) logger.info { "Mail template $key/$locale restored to the packaged wording" }
        return removed
    }

    /**
     * Adds a kind of the operator's own, as a draft in every locale.
     *
     * It sends nothing until backend code emits a mail naming this key — until then the
     * only thing that reaches an inbox from it is a test send.
     */
    fun create(key: String): AdminEmailTemplateInfo {
        if (!EmailTemplates.KEY_FORMAT.matches(key)) throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_KEY_INVALID)
        if (EmailTemplateKind.of(key) != null || QEmailTemplate().key.eq(key).exists()) {
            throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_ALREADY_EXISTS)
        }
        Locale.entries.forEach {
            EmailTemplate(key = key, locale = it, subject = key, body = DRAFT_BODY).save()
        }
        logger.info { "Mail template $key added" }
        return describe(key)
    }

    /**
     * Removes a kind an operator added, in every locale.
     *
     * @return false when there was nothing under that key
     */
    fun delete(key: String): Boolean {
        if (EmailTemplateKind.of(key) != null) throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_IS_BUILT_IN)
        val removed = QEmailTemplate().key.eq(key).delete() > 0
        if (removed) logger.info { "Mail template $key removed" }
        return removed
    }

    /**
     * Refuses a wording that would go out broken.
     *
     * A missing declared placeholder is the one thing checked hard: a password reset with
     * no `{{link}}` in it is a mail nobody can act on. Placeholders nothing fills are left
     * alone here and reported by [preview] instead, since on a custom kind they are
     * expected rather than wrong.
     */
    private fun validate(kind: EmailTemplateKind?, subject: String, body: String) {
        if (subject.isBlank() || body.isBlank()) throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_EMPTY)
        if (subject.length > EmailTemplates.MAX_SUBJECT_LENGTH || body.length > EmailTemplates.MAX_BODY_LENGTH) {
            throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_TOO_LONG)
        }
        val used = EmailTemplates.placeholdersIn(body)
        if (kind != null && !used.containsAll(kind.placeholders)) {
            throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_MISSING_PLACEHOLDER)
        }
    }

    private fun requireKnown(key: String): EmailTemplateKind? {
        val kind = EmailTemplateKind.of(key)
        if (kind == null && !QEmailTemplate().key.eq(key).exists()) {
            throw NotFoundException(NotFoundCause.MAIL_TEMPLATE_NOT_FOUND)
        }
        return kind
    }

    // -------------------------------------------------------- preview and testing

    /**
     * Fills a wording in as it would go out.
     *
     * The wording comes from the request rather than the database so that what an operator
     * previews is what sits in the editor, saved or not.
     */
    fun preview(key: String, dto: EmailPreviewDTO): EmailPreviewInfo {
        val kind = requireKnown(key)
        val values = EmailTemplates.sampleValues(kind, configuration.frontend.url)
        return EmailPreviewInfo(
            subject = EmailTemplates.render(dto.subject, values),
            body = EmailTemplates.render(dto.body, values),
            unfilled = EmailTemplates.placeholdersIn(dto.body) - values.keys,
        )
    }

    /**
     * Asks mail-service for one mail to the address given.
     *
     * Nothing is sent from here: the request goes onto the same topic as every other mail,
     * so a test proves the whole path rather than a shortcut through it. That also means
     * the answer is "queued", not "delivered" — the operator's inbox is the receipt.
     */
    fun requestTest(key: String, dto: EmailTestDTO) {
        val kind = requireKnown(key)
        val recipient = dto.recipient.trim()
        if (!MAIL_FORMAT.matches(recipient)) throw BadRequestException(BadRequestCause.INVALID_MAIL_ADDRESS)
        // A custom kind with no wording for this locale would reach mail-service as a blank mail
        if (kind == null && findOverride(key, dto.locale) == null) {
            throw BadRequestException(BadRequestCause.MAIL_TEMPLATE_EMPTY)
        }

        eventProducer.produceEvent {
            TestMailRequestedEvent(
                templateKey = key,
                locale = dto.locale,
                encryptedRecipient = encryptionService.encrypt(recipient),
            )
        }
        logger.info { "Test mail $key/${dto.locale} queued" }
    }

    // ---------------------------------------------------------------- mail-service

    /**
     * Every wording an operator saved, for mail-service to send with.
     *
     * Only overrides: a built-in absent from this list is one mail-service renders from its
     * own packaged copy, so the two services never hold two copies of the same text.
     */
    fun effective(): List<EffectiveEmailTemplate> = QEmailTemplate().findList().map { it.toEffective() }
}
