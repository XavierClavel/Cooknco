package shared.dto

import kotlinx.serialization.Serializable
import shared.enums.Locale

/** The wording an operator saved for one kind of mail, in one locale. */
@Serializable
data class EmailTemplateDTO(
    val subject: String,
    val body: String,
)

/**
 * A kind of mail an operator is adding.
 *
 * [key] is how code will later address it, so it is the one field: everything else about a
 * custom kind is the wording itself.
 */
@Serializable
data class EmailTemplateKeyDTO(
    val key: String,
)

/**
 * A preview request. The wording travels with it rather than being read back from the
 * database, so what is previewed is what sits in the editor, saved or not.
 */
@Serializable
data class EmailPreviewDTO(
    val subject: String,
    val body: String,
)

/** Where a test mail should go, and which locale's wording to send. */
@Serializable
data class EmailTestDTO(
    val recipient: String,
    val locale: Locale,
)

/**
 * A template as mail-service needs it: what to send, with none of the backoffice's
 * bookkeeping.
 *
 * Only wording an operator actually saved is listed. A built-in kind missing from the list
 * is one mail-service should render from its own packaged copy, which is what makes
 * restoring a wording a deletion rather than a second copy of the original text.
 */
@Serializable
data class EffectiveEmailTemplate(
    val key: String,
    val locale: Locale,
    val subject: String,
    val body: String,
)
