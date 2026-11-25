package shared.infodto

import shared.enums.AmountUnit
import shared.enums.Locale
import kotlinx.serialization.Serializable

/**
 * What a category of Open Food Facts costs in France: the median of the prices Open Prices
 * recorded for it, in euros per one [per] — a kilo, a litre or a piece.
 */
@Serializable
data class OpenPriceInfo(
    /** The Open Food Facts category tag, such as `en:carrots`. */
    val category: String,
    val name: Map<Locale, String>,
    val per: AmountUnit,
    val price: Float,
    /** How many prices the median is taken over. */
    val prices: Int,
)

/**
 * What a recipe costs to make, from the Open Prices medians of its ingredients.
 *
 * Only the lines that could be priced are in it: an ingredient typed as free text, or one the
 * catalogue has not linked to a category, adds nothing. [pricedIngredients] out of
 * [measuredIngredients] is what says how much of the recipe that is.
 */
@Serializable
data class RecipeCostInfo(
    /** Euros, for the recipe as written. */
    val total: Float,
    /** [total] over the recipe's yield; null when it states none. */
    val perPortion: Float? = null,
    val pricedIngredients: Int,
    /** The lines that state an amount. "Salt, to taste" is not one, and costs next to nothing. */
    val measuredIngredients: Int,
)
