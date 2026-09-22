package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.CascadeType
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import com.xavierclavel.models.jointables.RecipeStepIngredient
import shared.dto.RECIPE_STEP_TEXT_MAX_LENGTH
import shared.dto.RecipeDTO

/**
 * One step of a recipe.
 *
 * A table of its own, on the same shape as `RecipeIngredient`, rather than the
 * `@ElementCollection` of strings the steps were until this version. A step has grown from
 * a string into something with properties of its own — a duration, so far — and an element
 * collection is the wrong home for that: its rows have no identity, so nothing can ever
 * point at a step; nothing can be queried on them; and they cannot carry a sort order, which
 * is why the old `recipes_steps` had none and relied on rows coming back in the order they
 * were written.
 *
 * [sortOrder] is the fix for that last part, and the reason the migration has to invent an
 * order for the steps that already exist — see `1.48.sql`.
 *
 * Not soft-deletable, deliberately, like every other child of a recipe: Ebean only cascades
 * a soft delete to a child that is soft-deletable itself, so a deleted recipe keeps its
 * steps and can be restored whole. See `RecipeService.tryDelete`.
 */
@Entity
@Table(name = "recipe_steps")
class RecipeStep(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne
    var recipe: Recipe? = null,

    @Column(length = RECIPE_STEP_TEXT_MAX_LENGTH)
    var text: String = "",

    /** Null means no timer, which is what nearly every step means. See [RecipeDTO.RecipeStepDTO]. */
    var durationSeconds: Int? = null,

    @DbDefault("0")
    var sortOrder: Int = 0,

    /**
     * Bumped every time a picture is saved for this step, and part of the file name.
     *
     * The same trick the recipe, user and cookbook pictures use: the URL changes when the
     * picture does, so nothing has to persuade a cache to let go of the old one. Zero means
     * the step has no picture — there is no default to fall back to, because a step without
     * one should show nothing rather than a placeholder.
     */
    @DbDefault("0")
    var imageVersion: Long = 0,

    /**
     * The recipe's own ingredients that this step uses.
     *
     * Links rather than ingredients: each [RecipeStepIngredient] is one step and one
     * ingredient, and is a row with an identity so that it can carry what the pairing itself
     * is worth — a per-step quantity, when that is wanted. Reading it as a plain set of
     * ingredients is what this deliberately is not.
     *
     * An ingredient can be used by several steps: butter in the first and again in the
     * fourth. Most ingredients are used by no step at all, and a step that says nothing about
     * ingredients is the normal case.
     *
     * Positions are how the two lists are matched up inside one request
     * ([RecipeDTO.RecipeStepDTO.ingredients]); rows are what is stored, because nothing
     * stops a stored position from pointing past the end of a list somebody has shortened.
     */
    @OneToMany(mappedBy = "step", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var ingredientLinks: MutableList<RecipeStepIngredient> = mutableListOf(),

) : Model() {

    fun toDto() = RecipeDTO.RecipeStepDTO(
        id = id,
        imageVersion = imageVersion,
        text = text,
        durationSeconds = durationSeconds,
        // Back to positions on the way out. sortOrder *is* the position the client sent, so
        // this is a projection rather than a lookup - and sorted, so a step's ingredients read
        // in the order the recipe lists them rather than the order they were linked.
        //
        // Amounts go back exactly as they came, blanks included. What a blank works out to is
        // the reader's to compute: it depends on the recipe's other steps, which every reader
        // already has.
        ingredients = ingredientLinks
            .mapNotNull { link ->
                link.ingredient?.let { RecipeDTO.RecipeStepIngredientDTO(it.sortOrder, link.amount) }
            }
            .sortedBy { it.index },
    )

    /** Bumped when a picture is saved, so the URL moves with it. */
    fun increaseImageVersion() = apply { imageVersion++ }.update()

    /** Back to what a step with no picture says. See `ImageController.deleteRecipeStepImage`. */
    fun clearImageVersion() = apply { imageVersion = 0 }.update()

    /** Takes on what the author wrote, keeping the row - and its id - as it is. */
    fun mergeDto(dto: RecipeDTO.RecipeStepDTO, sortOrder: Int) = apply {
        this.text = dto.text
        // A zero or a negative would be a timer that has already run out. Only a real
        // duration is one; anything else is a step without a timer.
        this.durationSeconds = dto.durationSeconds?.takeIf { it > 0 }
        this.sortOrder = sortOrder
    }

    companion object {
        fun of(dto: RecipeDTO.RecipeStepDTO, sortOrder: Int) = RecipeStep(
            text = dto.text,
            // A zero or a negative would be a timer that has already run out. Only a real
            // duration is one; anything else is a step without a timer.
            durationSeconds = dto.durationSeconds?.takeIf { it > 0 },
            sortOrder = sortOrder,
        )
    }
}
