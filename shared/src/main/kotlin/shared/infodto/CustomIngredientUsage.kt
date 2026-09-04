package shared.infodto

import kotlinx.serialization.Serializable

/**
 * How often a free-text ingredient name is used across all recipes. This is the queue that tells
 * admins which ingredients are worth adding to the catalog.
 */
@Serializable
data class CustomIngredientUsage(
    /** One of the spellings users typed; names are grouped case- and accent-insensitively. */
    val name: String,
    val uses: Int,
)
