package shared.infodto

import shared.enums.Locale
import kotlinx.serialization.Serializable

/**
 * A food of the Anses Ciqual table, with its values already read into the catalogue's shape —
 * what linking an ingredient to it would copy. Per 100 g, kcal for calories and mg for
 * cholesterol, g for the rest; null where Ciqual has no figure.
 */
@Serializable
data class CiqualFoodInfo(
    val code: Int,
    val name: Map<Locale, String>,
    val group: Map<Locale, String> = mapOf(),

    val calories: Int? = null,
    val carbohydrates: Float? = null,
    val cholesterol: Float? = null,
    val saturatedFat: Float? = null,
    val unsaturatedFat: Float? = null,
    val sugars: Float? = null,
    val fibers: Float? = null,
    val proteins: Float? = null,
    val salt: Float? = null,
)

/**
 * The Ciqual food an ingredient's nutrition was copied from, and the edition of the table it
 * was read in — what the citation Anses requires has to name.
 */
@Serializable
data class CiqualSource(
    val code: Int,
    val edition: Int,
    /** Empty if the bundled edition no longer lists [code]. */
    val name: Map<Locale, String> = mapOf(),
)
