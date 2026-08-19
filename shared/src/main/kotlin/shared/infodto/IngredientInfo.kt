package shared.infodto

import shared.dto.IngredientDTO
import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.Locale
import shared.enums.MeasurementType
import kotlinx.serialization.Serializable

@Serializable
data class IngredientInfo(
    val id: Long,
    val name: Map<Locale, String>,
    val type: IngredientType,

    val calories: Int = 0,
    val carbohydrates: Float = 0f,
    val cholesterol: Float = 0f,
    val saturatedFat: Float = 0f,
    val unsaturatedFat: Float = 0f,
    val sugars: Float = 0f,
    val fibers: Float = 0f,
    val proteins: Float = 0f,
    val sodium: Float = 0f,

    val gramsPerUnit: Float? = null,
    val gramsPerMilliliter: Float? = null,
    val measurableByWeight: Boolean = true,
    val defaultUnit: AmountUnit? = null,

    /** Derived from the conversions above; clients filter the unit picker with it. */
    val allowedTypes: Set<MeasurementType> = setOf(MeasurementType.NONE),
) {
    fun compareToDTO(ingredientDTO: IngredientDTO): Boolean =
        this.type == ingredientDTO.type &&
        this.name == ingredientDTO.name
}
