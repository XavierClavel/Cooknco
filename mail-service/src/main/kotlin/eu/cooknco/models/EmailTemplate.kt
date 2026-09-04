package eu.cooknco.models

import io.ebean.Model
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import shared.enums.Locale
import shared.utils.EmailTemplates

/**
 * A local copy of a wording an operator saved in the backoffice.
 *
 * The backend owns these rows; this table is a mirror, refreshed whenever
 * [eu.cooknco.MailTemplateStore] reaches it. Keeping one is what stops a backend that is
 * down or mid-deploy from turning every mail back into its packaged wording: the last text
 * an operator actually approved is on disk here, and sending never waits on a network call.
 */
@Entity
@Table(
    name = "email_templates",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["mail_key", "locale"])
    ]
)
class EmailTemplate(
    @Id
    val id: Long = 0L,

    /** Named `mail_key` in the database, as in the backend: `key` is reserved on some platforms. */
    @Column(name = "mail_key", nullable = false, length = 63)
    var key: String = "",

    @Column(nullable = false)
    var locale: Locale = Locale.EN,

    @Column(nullable = false, length = EmailTemplates.MAX_SUBJECT_LENGTH)
    var subject: String = "",

    @Column(nullable = false, length = EmailTemplates.MAX_BODY_LENGTH)
    var body: String = "",
): Model()
