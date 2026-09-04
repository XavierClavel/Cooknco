package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.Locale

/**
 * One kind of mail, as the backoffice mails tab shows it.
 *
 * [builtIn] is what an operator acts on: a built-in kind is one the application emits, so
 * it always has a packaged wording to restore to and can never be removed. A kind an
 * operator added has neither, and sends nothing until code names its key.
 */
@Serializable
data class AdminEmailTemplateInfo(
    val key: String,
    val builtIn: Boolean,
    /** Names the wording may use as `{{name}}`. Empty for a custom kind: nothing fills one. */
    val placeholders: List<String>,
    /** One entry per locale the app sends in, always in the same order. */
    val locales: List<AdminEmailTemplateLocale>,
)

/** The wording sent for one locale, and where it comes from. */
@Serializable
data class AdminEmailTemplateLocale(
    val locale: Locale,
    val subject: String,
    val body: String,
    /** False while the wording packaged with the app is the one being sent. */
    val custom: Boolean,
    /** Epoch seconds. Null while nothing has been saved over the packaged wording. */
    val updatedAt: Long? = null,
)

/** A wording filled in as it would go out, for the backoffice to show. */
@Serializable
data class EmailPreviewInfo(
    val subject: String,
    val body: String,
    /** `{{name}}`s left in the body because nothing fills them. */
    val unfilled: List<String>,
)
