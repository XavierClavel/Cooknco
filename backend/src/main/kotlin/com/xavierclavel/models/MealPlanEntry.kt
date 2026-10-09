package com.xavierclavel.models

import io.ebean.Model
import io.ebean.annotation.ConstraintMode
import io.ebean.annotation.DbForeignKey
import io.ebean.annotation.Index
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import shared.dto.MEAL_PLAN_TITLE_MAX_LENGTH
import shared.enums.MealSlot
import shared.infodto.MealPlanEntryInfo
import shared.infodto.MealPlanRecipe
import java.time.LocalDate

/**
 * One dish in an account's meal plan: a recipe, or a few words for anything else.
 *
 * There is no row for a *meal*. A meal is a day and a [slot], and the dishes sharing both are
 * it — so an empty meal costs nothing, and moving a dish is changing two columns.
 *
 * **An entry outlives its recipe.** [title] is always set: the free text, or the recipe's
 * title as it was when it was planned. Recipes leave a reader's reach in several ways — their
 * owner deletes them (a soft delete), a moderator hides them, the account goes private, or
 * the account is deleted outright — and none of those places has to know a plan exists.
 * The plan asks, when it is read, which of its recipes the reader can still open
 * (`RecipeService.findReadable`), and shows the rest as their title with no link. That is
 * the same pull-not-push rule `logEdit`'s username follows: what changes elsewhere is
 * resolved here, rather than fanned out to from every place that changes it.
 *
 * The one case that cannot be resolved on read is the recipe row actually going: an account's
 * deletion takes its recipes with it for good, not softly. So [recipe] is `ON DELETE SET
 * NULL` rather than the `RESTRICT` Ebean writes by default — the other way round, one person
 * planning another's recipe would make that other person unable to delete their account.
 */
@Entity
@Table(name = "meal_plan_entries")
@Index(columnNames = ["owner_id", "date"])
class MealPlanEntry(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    /** Private to this account, and deleted with it (`User.mealPlanEntries`). */
    @ManyToOne(optional = false)
    var owner: User,

    /**
     * A calendar day with no time and no zone: see `shared.dto.MealPlanEntryDTO.date`.
     *
     * No `@Column` here or on [position]: the Kotlin type already makes them `not null`, and
     * the annotation's default length of 255 comes out as `date(255)`, which Postgres refuses.
     */
    var date: LocalDate,

    // Stored by name: adding or reordering slots must never rewrite existing rows.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var slot: MealSlot,

    /** Where it sits in its meal. Appended to the end; ties fall back to the id. */
    var position: Int = 0,

    @ManyToOne
    @DbForeignKey(onDelete = ConstraintMode.SET_NULL)
    var recipe: Recipe? = null,

    @Column(nullable = false, length = MEAL_PLAN_TITLE_MAX_LENGTH)
    var title: String,

    var servings: Int? = null,

) : Model() {

    /**
     * @param readableRecipe this entry's recipe if the reader can still open it, else null.
     *   Never read off [recipe] here: that is only a reference, and touching anything but its
     *   id would load it — one query per row, and one that ignores who is reading.
     */
    fun toInfo(readableRecipe: Recipe?) = MealPlanEntryInfo(
        id = id,
        date = date.toString(),
        slot = slot,
        position = position,
        title = readableRecipe?.title ?: title,
        servings = servings,
        recipe = readableRecipe?.let { MealPlanRecipe(id = it.id, version = it.imageVersion) },
    )
}
