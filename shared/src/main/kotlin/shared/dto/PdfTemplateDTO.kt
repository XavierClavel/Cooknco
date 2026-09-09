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
 * @param recipeId the recipe to fill the layout with. Null falls back to a sample, so the
 *   tab is usable on an install with nothing in it yet.
 */
@Serializable
data class PdfPreviewDTO(
    val body: String,
    val recipeId: Long? = null,
)
