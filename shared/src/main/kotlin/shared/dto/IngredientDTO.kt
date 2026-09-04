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

    val calories: Int = 0,
    val carbohydrates: Float = 0f,
    val cholesterol: Float = 0f,
    val saturatedFat: Float = 0f,
    val unsaturatedFat: Float = 0f,
    val fibers: Float = 0f,
    val proteins: Float = 0f,
    val sodium: Float = 0f,
    val sugars: Float = 0f,

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
