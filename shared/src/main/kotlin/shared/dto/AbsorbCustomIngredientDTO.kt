package shared.dto

import kotlinx.serialization.Serializable

/** Names every recipe row spelled this way to be re-pointed at a real ingredient. */
@Serializable
data class AbsorbCustomIngredientDTO(
    val name: String,
)

@Serializable
data class AbsorbCustomIngredientResult(
    /** Recipe rows that gained nutrition data. */
    val convertedRows: Int,
    /**
     * Rows left as free text because the ingredient cannot be measured in the unit they use.
     * Giving the ingredient that conversion and absorbing again picks them up.
     */
    val skippedRows: Int = 0,
)
