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

    /** Per 100 g, kcal for calories and mg for cholesterol, g for the rest. Null means unknown. */
    val calories: Int? = null,
    val carbohydrates: Float? = null,
    val cholesterol: Float? = null,
    val saturatedFat: Float? = null,
    val unsaturatedFat: Float? = null,
    val sugars: Float? = null,
    val fibers: Float? = null,
    val proteins: Float? = null,
    val salt: Float? = null,

    /** Where the nutrition above was copied from; null when it was typed in by hand. */
    val ciqual: CiqualSource? = null,

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
