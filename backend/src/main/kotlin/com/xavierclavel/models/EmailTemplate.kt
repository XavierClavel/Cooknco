package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.WhenModified
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import shared.dto.EffectiveEmailTemplate
import shared.enums.Locale
import shared.infodto.AdminEmailTemplateLocale
import shared.utils.EmailTemplates
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The wording an operator saved for one mail, in one locale.
 *
 * A row is an override rather than the mail itself: a built-in kind with no row is sent
 * with the wording packaged in the jar, which is what makes restoring one a delete instead
 * of a second copy of the original text. Templates are keyed by [key] rather than by an
 * enum so that a kind an operator added is nothing but rows.
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    /** Named `mail_key` in the database: `key` is reserved on several platforms. */
    @Column(name = "mail_key", nullable = false, length = 63)
    var key: String = "",

    @Column(nullable = false)
    var locale: Locale = Locale.EN,

    @Column(nullable = false, length = EmailTemplates.MAX_SUBJECT_LENGTH)
    var subject: String = "",

    @Column(nullable = false, length = EmailTemplates.MAX_BODY_LENGTH)
    var body: String = "",

    @WhenModified
    var updatedAt: LocalDateTime = LocalDateTime.now(),

) : Model() {
    fun toInfo() = AdminEmailTemplateLocale(
        locale = locale,
        subject = subject,
        body = body,
        custom = true,
        updatedAt = updatedAt.toEpochSecond(ZoneOffset.UTC),
    )

    fun toEffective() = EffectiveEmailTemplate(key = key, locale = locale, subject = subject, body = body)
}
