package com.xavierclavel.models

import com.xavierclavel.models.localization.LocalizedIngredientName
import shared.dto.IngredientDTO
import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.MeasurementType
import shared.infodto.IngredientInfo
import shared.utils.UnitCapabilities
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.OneToMany
import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.CascadeType
import jakarta.persistence.GenerationType

@Entity
@Table(name = "ingredients")
class Ingredient (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @OneToMany(mappedBy = "ingredient", cascade = [CascadeType.ALL])
    var translations: MutableList<LocalizedIngredientName> = mutableListOf(),

    var type: IngredientType = IngredientType.MISCELLANEOUS,

    var calories: Int = 0,

    var cholesterol: Float = 0f,

    @DbDefault(value = "0")
    var saturatedFat: Float = 0f,

    @DbDefault(value = "0")
    var unsaturatedFat: Float = 0f,

    @DbDefault(value = "0")
    var carbohydrates: Float = 0f,

    @DbDefault(value = "0")
    var sugars: Float = 0f,

    var fibers: Float = 0f,
    var proteins: Float = 0f,
    var sodium: Float = 0f,

    /**
     * Weight of one piece, in grams. Non-null means the ingredient is countable, so [AmountUnit] values
     * of type [MeasurementType.AMOUNT] are offered. Null means "not countable" — there is deliberately no
     * separate flag, so an ingredient can never claim to be countable without saying what a piece weighs.
     */
    var gramsPerUnit: Float? = null,

    /** Density in g/mL. Non-null means volume units are offered. Same reasoning as [gramsPerUnit]. */
    var gramsPerMilliliter: Float? = null,

    /** False only for ingredients that cannot sensibly be weighed, e.g. "salt, to taste". */
    @DbDefault(value = "true")
    var measurableByWeight: Boolean = true,

    /** Preselected in the recipe editor. Null falls back to grams, or whatever is allowed. */
    @Enumerated(EnumType.STRING)
    var defaultUnit: AmountUnit? = null,

    ): Model() {

    /** The measurement families this ingredient can be expressed in. */
    fun allowedTypes(): Set<MeasurementType> =
        UnitCapabilities.allowedTypes(gramsPerUnit, gramsPerMilliliter, measurableByWeight)

    fun mergeDTO(ingredientDTO: IngredientDTO): Ingredient {
        this.type = ingredientDTO.type

        this.calories = ingredientDTO.calories
        this.carbohydrates = ingredientDTO.carbohydrates
        this.cholesterol = ingredientDTO.cholesterol
        this.saturatedFat = ingredientDTO.saturatedFat
        this.unsaturatedFat = ingredientDTO.unsaturatedFat
        this.fibers = ingredientDTO.fibers
        this.sugars = ingredientDTO.sugars
        this.proteins = ingredientDTO.proteins
        this.sodium = ingredientDTO.sodium

        this.gramsPerUnit = ingredientDTO.gramsPerUnit
        this.gramsPerMilliliter = ingredientDTO.gramsPerMilliliter
        this.measurableByWeight = ingredientDTO.measurableByWeight
        this.defaultUnit = ingredientDTO.defaultUnit

        return this
    }

    fun toInfo() = IngredientInfo(
        id = this.id,
        type = this.type,

        calories = this.calories,
        carbohydrates = this.carbohydrates,
        cholesterol = this.cholesterol,
        saturatedFat = this.saturatedFat,
        unsaturatedFat = this.unsaturatedFat,
        sugars = this.sugars,
        fibers = this.fibers,
        proteins = this.proteins,
        sodium = this.sodium,

        gramsPerUnit = this.gramsPerUnit,
        gramsPerMilliliter = this.gramsPerMilliliter,
        measurableByWeight = this.measurableByWeight,
        defaultUnit = this.defaultUnit,
        allowedTypes = this.allowedTypes(),

        name = translations.associate { it.locale to it.name },
    )
}
