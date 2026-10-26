package shared.dto

import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.MeasurementType
import shared.utils.UnitCapabilities
import shared.enums.Locale
import kotlinx.serialization.Serializable

@Serializable
data class IngredientDTO(
    val name: Map<Locale, String> = mapOf(),
    val type: IngredientType = IngredientType.MISCELLANEOUS,

    /** Per 100 g, kcal for calories and mg for cholesterol, g for the rest. Null means unknown. */
    val calories: Int? = null,
    val carbohydrates: Float? = null,
    val cholesterol: Float? = null,
    val saturatedFat: Float? = null,
    val unsaturatedFat: Float? = null,
    val fibers: Float? = null,
    val proteins: Float? = null,
    /** Salt, not sodium: a figure 2.5 times the sodium in it. */
    val salt: Float? = null,
    val sugars: Float? = null,

    /**
     * The Ciqual food to take the nutrition from. When set, the server copies the values above
     * from its bundled table and ignores whatever was sent; null means they are typed in by hand.
     */
    val ciqualCode: Int? = null,

    /** Grams per piece; null means the ingredient is not countable. */
    val gramsPerUnit: Float? = null,
    /** Density in g/mL; null means volume units don't apply. */
    val gramsPerMilliliter: Float? = null,
    val measurableByWeight: Boolean = true,

    /** Unit the recipe editor preselects for this ingredient. */
    val defaultUnit: AmountUnit? = null,
) {
    fun allowedTypes(): Set<MeasurementType> =
        UnitCapabilities.allowedTypes(gramsPerUnit, gramsPerMilliliter, measurableByWeight)
}
