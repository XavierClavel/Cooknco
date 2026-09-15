package shared.dto

import kotlinx.serialization.Serializable

/** The layout an operator saved for one kind of document, in one locale. */
@Serializable
data class PdfTemplateDTO(
    val body: String,
)

/**
 * A preview request.
 *
 * The layout travels with it rather than being read back from the database, so what is
 * previewed is what sits in the editor, saved or not — the same reason [EmailPreviewDTO]
 * carries its wording.
 *
 * @param subjectId what to fill the layout with, read against the kind being previewed: a
 *   recipe id for `recipe`, a cookbook id for `cookbook`. Null falls back to the most
 *   recent one of whichever it is, so the tab is usable without hunting for an id first.
 */
@Serializable
data class PdfPreviewDTO(
    val body: String,
    val subjectId: Long? = null,
)
