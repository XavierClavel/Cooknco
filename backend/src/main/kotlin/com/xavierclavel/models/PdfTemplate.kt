package com.xavierclavel.models

import com.xavierclavel.utils.PdfTemplates
import io.ebean.Model
import io.ebean.annotation.WhenModified
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import shared.enums.Locale
import shared.infodto.AdminPdfTemplateLocale
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The layout an operator saved for one kind of document, in one locale.
 *
 * A row is an override, exactly as [EmailTemplate] is: a kind with no row is rendered from
 * the layout packaged in the jar, so restoring one is a delete rather than a second copy of
 * the original markup.
 */
@Entity
@Table(
    name = "pdf_templates",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["document_key", "locale"])
    ]
)
class PdfTemplate(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    /** Named `document_key` in the database, for the reason `mail_key` is. */
    @Column(name = "document_key", nullable = false, length = 63)
    var key: String = "",

    @Column(nullable = false)
    var locale: Locale = Locale.EN,

    @Column(nullable = false, length = PdfTemplates.MAX_BODY_LENGTH)
    var body: String = "",

    @WhenModified
    var updatedAt: LocalDateTime = LocalDateTime.now(),

) : Model() {
    fun toInfo() = AdminPdfTemplateLocale(
        locale = locale,
        body = body,
        custom = true,
        updatedAt = updatedAt.toEpochSecond(ZoneOffset.UTC),
    )
}
