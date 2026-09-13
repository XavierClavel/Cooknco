package com.xavierclavel.models.jointables

import com.xavierclavel.models.RecipeStep
import io.ebean.Model
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import shared.dto.RecipeDTO

/**
 * One ingredient of a recipe, used by one of its steps.
 *
 * A row of its own — one step, one recipe ingredient — with an identity, rather than a pure
 * many-to-many intersection. The difference is what the row is allowed to become: an
 * intersection can only say *that* a step uses an ingredient, while a row with an id can say
 * how much. "50 g of the butter in this step, the rest in that one" is a column here when it
 * is wanted, not a remodelling.
 *
 * It also has to be a named entity for a duller reason. Ebean manages an implicit
 * intersection only through the association that owns it: replacing a recipe's steps deleted
 * the steps and left their intersection rows behind, and the next thing a save does is clear
 * the ingredient rows those orphans still pointed at — `ON DELETE RESTRICT` at both ends, so
 * the save failed outright. Named, the link is deleted on its own terms, by a query bean, at
 * the point in the write order that needs it: see
 * `RecipeIngredientService.replaceRecipeIngredients`.
 *
 * Nothing here is unique. An ingredient may be used by several steps and a step uses several
 * ingredients; what is constrained is only that each row names exactly one of each. A save
 * rewrites every link of the recipe from de-duplicated positions, so the same pair cannot
 * arrive twice today — but that is the writer's doing, not a constraint, and a per-step
 * quantity is exactly the reason not to make it one.
 */
@Entity
@Table(name = "recipe_step_ingredients")
class RecipeStepIngredient(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    var step: RecipeStep? = null,

    @ManyToOne
    var ingredient: RecipeIngredient? = null,

    /**
     * How much of the ingredient this step uses, in the ingredient's own unit.
     *
     * Null means all of it. See [RecipeDTO.RecipeStepIngredientDTO.amount], and note that it
     * is stored for the recipe's own yield like every other amount — scaling for a different
     * number of portions is the reader's job, not the row's.
     */
    var amount: Float? = null,

) : Model()
