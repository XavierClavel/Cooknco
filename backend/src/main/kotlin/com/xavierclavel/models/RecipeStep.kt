package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.DbDefault
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
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

    @Column(length = 255)
    var text: String = "",

    /** Null means no timer, which is what nearly every step means. See [RecipeDTO.RecipeStepDTO]. */
    var durationSeconds: Int? = null,

    @DbDefault("0")
    var sortOrder: Int = 0,

) : Model() {

    fun toDto() = RecipeDTO.RecipeStepDTO(text = text, durationSeconds = durationSeconds)

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
