package com.xavierclavel.models

import com.xavierclavel.models.localization.LocalizedIngredientName
import shared.dto.IngredientDTO
import shared.enums.AmountUnit
import shared.enums.IngredientType
import shared.enums.Locale
import shared.enums.MeasurementType
import shared.infodto.CiqualFoodInfo
import shared.infodto.CiqualSource
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

    /*
     * Nutrition per 100 g: kcal for calories, mg for cholesterol, g for the rest. Null is unknown,
     * which is not the same as none — Ciqual has no cholesterol figure for a quarter of its foods.
     * The columns were not null until 1.55, so a 0 typed in before then may mean either.
     */
    var calories: Int? = null,
    var cholesterol: Float? = null,
    var saturatedFat: Float? = null,
    var unsaturatedFat: Float? = null,
    var carbohydrates: Float? = null,
    var sugars: Float? = null,
    var fibers: Float? = null,
    var proteins: Float? = null,
    /** Salt, not sodium (2.5 times the sodium in it): what Ciqual states, and what the French label always said. */
    var salt: Float? = null,

    /**
     * The Ciqual food the nutrition above was copied from, or null when it was typed in by hand.
     * While set, the values are Ciqual's and nothing else: see [linkCiqual].
     */
    var ciqualCode: Int? = null,

    /** The edition of the table [ciqualCode] was read in, which the citation names. Set with it. */
    var ciqualEdition: Int? = null,

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

    /** Takes everything from [ingredientDTO], nutrition typed in by hand included; see [linkCiqual]. */
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
        this.salt = ingredientDTO.salt
        this.ciqualCode = null
        this.ciqualEdition = null

        this.gramsPerUnit = ingredientDTO.gramsPerUnit
        this.gramsPerMilliliter = ingredientDTO.gramsPerMilliliter
        this.measurableByWeight = ingredientDTO.measurableByWeight
        this.defaultUnit = ingredientDTO.defaultUnit

        return this
    }

    /**
     * Replaces the nutrition with [food]'s, overwriting whatever [mergeDTO] took from the request.
     * A linked ingredient cites Anses, so it holds Anses's figures and no others: a value can only
     * be changed by unlinking it first.
     */
    fun linkCiqual(food: CiqualFoodInfo, edition: Int): Ingredient {
        this.calories = food.calories
        this.carbohydrates = food.carbohydrates
        this.cholesterol = food.cholesterol
        this.saturatedFat = food.saturatedFat
        this.unsaturatedFat = food.unsaturatedFat
        this.fibers = food.fibers
        this.sugars = food.sugars
        this.proteins = food.proteins
        this.salt = food.salt
        this.ciqualCode = food.code
        this.ciqualEdition = edition
        return this
    }

    /**
     * @param names what this ingredient is called, per language, resolved by
     *   [com.xavierclavel.services.IngredientService.namesOf] rather than read off [translations].
     *   An ingredient has no name of its own, so a catalogue listing reading the collection costs a
     *   round trip per entry — and a paged one cannot fetch it: Ebean drops a `-to-many` from the
     *   plan as soon as `maxRows` is set. `IngredientFetchPlanTest` is what keeps this in use.
     * @param ciqual what [ciqualCode] cites, named from the bundled table by
     *   [com.xavierclavel.services.CiqualService.sourceOf]; null when the nutrition was typed in.
     */
    fun toInfo(names: Map<Locale, String>, ciqual: CiqualSource?) = IngredientInfo(
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
        salt = this.salt,
        ciqual = ciqual,

        gramsPerUnit = this.gramsPerUnit,
        gramsPerMilliliter = this.gramsPerMilliliter,
        measurableByWeight = this.measurableByWeight,
        defaultUnit = this.defaultUnit,
        allowedTypes = this.allowedTypes(),

        name = names,
    )
}
