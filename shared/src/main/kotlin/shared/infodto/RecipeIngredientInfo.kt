package shared.infodto

import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.MeasurementType
import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredientInfo(
    /** Null for custom ingredients, which have no entry in the ingredients table. */
    val id: Long? = null,
    val name: String,
    val amount: Float?,
    val unit: AmountUnit,
    val complement : String?,
    val type: IngredientType? = null,
    /** Which unit families the editor may offer for this row. */
    val allowedTypes: Set<MeasurementType> = setOf(MeasurementType.NONE),
) {
    val isCustom: Boolean get() = id == null
}
