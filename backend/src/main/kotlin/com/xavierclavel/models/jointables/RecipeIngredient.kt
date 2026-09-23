package com.xavierclavel.models.jointables

import com.xavierclavel.models.Ingredient
import com.xavierclavel.models.Recipe
import shared.dto.CUSTOM_INGREDIENT_NAME_MAX_LENGTH
import shared.enums.AmountUnit
import shared.enums.MeasurementType
import shared.infodto.RecipeIngredientInfo
import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import shared.enums.Locale

/**
 * An ingredient of a recipe: either a reference to the ingredients table, or a free-text name
 * entered by the user when no matching ingredient exists yet. Exactly one of [ingredient] and
 * [customName] is set, enforced by a check constraint.
 */
@Entity
@Table(name = "recipe_ingredients")
class RecipeIngredient (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    var recipe: Recipe? =  null,

    @ManyToOne
    var ingredient: Ingredient? = null,

    @Column(length = CUSTOM_INGREDIENT_NAME_MAX_LENGTH)
    var customName: String? = null,

    var amount: Float? = null,

    // Stored by name: adding or reordering units must never rewrite existing rows.
    @Enumerated(EnumType.STRING)
    var unit: AmountUnit = AmountUnit.NONE,

    var complement: String? = null,

    @DbDefault("0")
    var sortOrder: Int = 0,

    ): Model() {

    /**
     * @param catalogueName what the catalogue entry this line names is called in the reader's
     *   language, resolved by `IngredientService.namesOf` rather than read off
     *   `ingredient.translations`. That collection lazy-loads one query per *line*, which a recipe
     *   with a dozen ingredients pays a dozen times over — and a cookbook export pays per recipe as
     *   well, twice, the book being printed twice to number its contents.
     *
     *   Free text wins over it, as it always has: a line the cook typed is what they typed.
     */
    fun toInfo(catalogueName: String?) = RecipeIngredientInfo(
        id = ingredient?.id,
        name = customName
            ?: catalogueName
            ?: "Unknown",
        type = ingredient?.type,
        amount = amount,
        unit = unit,
        complement = complement,
        // Custom ingredients carry no capability data, so every unit is offered.
        allowedTypes = ingredient?.allowedTypes() ?: MeasurementType.entries.toSet(),
    )
}
