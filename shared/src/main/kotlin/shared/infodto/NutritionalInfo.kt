package shared.infodto

import kotlinx.serialization.Serializable

@Serializable
data class NutritionalInfo(
    val calories: Int = 0,
    val carbohydrates: Float = 0f,
    val cholesterol: Float = 0f,
    val saturatedFat: Float = 0f,
    val unsaturatedFat: Float = 0f,
    val sugars: Float = 0f,
    val fibers: Float = 0f,
    val proteins: Float = 0f,
    val salt: Float = 0f,
)