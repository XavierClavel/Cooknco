package com.xavierclavel.models

import com.xavierclavel.models.jointables.CookbookRecipe
import com.xavierclavel.models.jointables.Like
import com.xavierclavel.models.jointables.RecipeIngredient
import shared.dto.RecipeDTO
import shared.enums.DishClass
import shared.enums.Locale
import shared.infodto.AdminRecipeInfo
import shared.infodto.RecipeInfo
import shared.overviewdto.RecipeOverview
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import io.ebean.Model
import io.ebean.annotation.DbDefault
import io.ebean.annotation.SoftDelete
import jakarta.persistence.Column
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity
@Table(name = "recipes")
class Recipe (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @DbDefault("0")
    var imageVersion: Long = 0,

    var title: String = "",

    var description: String = "",

    var dishClass: DishClass = DishClass.MAIN_DISH,

    var creationDate: LocalDateTime = LocalDateTime.now(),

    var modificationDate: LocalDateTime = LocalDateTime.now(),

    @DbDefault("")
    @Column(length = 511)
    var tips: String = "",

    //metadata
    var yield: Int? = null,
    var preparationTime: Int? = null,
    var cookingTime: Int? = null,
    var cookingTemperature: Int? = null,


    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var steps: List<RecipeStep> = listOf(),

    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var ingredients: List<RecipeIngredient> = listOf(),


    @ManyToOne
    var owner: User? = null,

    @OneToMany(mappedBy = "recipe", cascade = [CascadeType.ALL], orphanRemoval = true)
    var cookbooks: MutableList<CookbookRecipe> = mutableListOf(),

    @OneToMany(mappedBy = "recipe", cascade = [CascadeType.ALL], orphanRemoval = true)
    var likes: MutableList<Like> = mutableListOf(),

    var taggedForDeletion: Boolean = false,

    /**
     * Set instead of removing the row — see [com.xavierclavel.services.RecipeService.tryDelete].
     *
     * Ebean hides a soft-deleted row from every query bean automatically, so a deleted
     * recipe disappears from feeds, searches, profiles and exports without a single query
     * gaining a predicate. What it does *not* do is destroy the recipe: the row, its steps,
     * its ingredients and everything pointing at it stay exactly where they were, so a
     * deletion made by mistake is one `update` away from being undone rather than gone.
     *
     * Raw SQL does not see this flag — Ebean can only apply it to its own queries — which is
     * why `StorageService` still counts a deleted recipe as the owner of its pictures and
     * leaves them alone.
     */
    @SoftDelete
    @DbDefault("false")
    var deleted: Boolean = false,

    //Moderation
    /** Hidden recipes stay in database but are only served to their owner and to admins. */
    @DbDefault("false")
    var isHidden: Boolean = false,

    @Column(length = 1023)
    @DbDefault("")
    var hiddenReason: String = "",

    ) : Model() {
    fun mergeDTO(recipeDTO: RecipeDTO) : Recipe = apply {
        this.title = recipeDTO.title
        this.description = recipeDTO.description
        this.dishClass = recipeDTO.dishClass

        // Position is carried by the row now rather than by the order it happened to be
        // written in, so the index the author put the step at is what is stored.
        this.steps = recipeDTO.steps.mapIndexed { index, step -> RecipeStep.of(step, index) }
        this.modificationDate = LocalDateTime.now()

        this.yield = recipeDTO.yield
        this.preparationTime = recipeDTO.preparationTime
        this.cookingTime = recipeDTO.cookingTime
        this.cookingTemperature = recipeDTO.cookingTemperature

        this.tips = recipeDTO.tips
    }

    fun setOwner(user: User): Recipe = apply {
        this.owner = user
    }

    fun increaseVersion() = apply {
        this.imageVersion++;
    }.update()

    fun resetVersion() = apply {
        this.imageVersion = 0
    }.update()

    /**
     * What a step's ingredient works out to when it names no amount, by ingredient position.
     *
     * A blank means "unspecified", not "all of it", and what it is worth depends on what the
     * recipe's *other* steps say about the same ingredient — which is why this is computed
     * here, over every step at once, rather than by a step that can only see its own links.
     *
     * One blank is the remainder: the line's amount less whatever the other steps spelled
     * out. With nothing spelled out that is the whole line, which is why a single step using
     * an ingredient needs no number typed; with 60 g of the 100 g named elsewhere it is 40 g,
     * without anyone having to do the subtraction.
     *
     * Two or more blanks share a remainder that nothing says how to divide, so they are worth
     * no number at all and a null is returned for them. Guessing an even split would be
     * inventing a measurement, and refusing would make "butter in these two steps, roughly"
     * unsayable — which is a normal thing for a recipe to mean.
     */
    private fun blankStepAmounts(): Map<Int, Float?> {
        val links = steps.flatMap { it.ingredientLinks }
        if (links.isEmpty()) return emptyMap()
        val listed = ingredients.associate { it.sortOrder to it.amount }

        return links.groupBy { it.ingredient?.sortOrder }
            .mapNotNull { (position, forIngredient) ->
                if (position == null) return@mapNotNull null
                if (forIngredient.count { it.amount == null } != 1) return@mapNotNull position to null
                val total = listed[position] ?: return@mapNotNull position to null
                val spelledOut = forIngredient.mapNotNull { it.amount }.sum()
                position to (total - spelledOut).takeIf { it > 0f }
            }
            .toMap()
    }

    fun toInfo(locale: Locale) = RecipeInfo(
        id = this.id,
        version = this.imageVersion,
        title = title,
        description = description,
        dishClass = dishClass,
        steps = blankStepAmounts().let { blanks ->
            steps.sortedBy { it.sortOrder }.map { it.toDto(blanks) }
        },
        ingredients = ingredients.sortedBy { it.sortOrder }.map { it.toInfo(locale) },

        owner = this.owner!!.toOverview(),
        creationDate = this.creationDate.toEpochSecond(ZoneOffset.UTC),
        editionDate = this.modificationDate.toEpochSecond(ZoneOffset.UTC),
        likesCount = this.likes.size,
        tips = this.tips,

        yield = this.yield,
        preparationTime = this.preparationTime,
        cookingTime = this.cookingTime,
        cookingTemperature = this.cookingTemperature,
        isHidden = this.isHidden,
    )

    fun toOverview() = RecipeOverview(
        id = this.id,
        version = this.imageVersion,
        title = title,
        dishClass = dishClass,
        owner = this.owner!!.toOverview(),
        likesCount = this.likes.size,
        creationDate = this.creationDate.toEpochSecond(ZoneOffset.UTC),
        isHidden = this.isHidden,
    )

    fun tagForDeletion(): Recipe = this.apply {
            taggedForDeletion = true
    }

    fun hide(reason: String): Recipe = this.apply {
        isHidden = true
        hiddenReason = reason.take(1023)
    }

    fun unhide(): Recipe = this.apply {
        isHidden = false
        hiddenReason = ""
    }

    fun toAdminInfo(pendingReportsCount: Int) = AdminRecipeInfo(
        id = this.id,
        version = this.imageVersion,
        title = this.title,
        owner = this.owner?.toOverview(),
        creationDate = this.creationDate.toEpochSecond(ZoneOffset.UTC),
        modificationDate = this.modificationDate.toEpochSecond(ZoneOffset.UTC),
        likesCount = this.likes.size,
        cookbooksCount = this.cookbooks.size,
        isHidden = this.isHidden,
        hiddenReason = this.hiddenReason,
        taggedForDeletion = this.taggedForDeletion,
        pendingReportsCount = pendingReportsCount,
    )

    fun hasReferences(): Boolean =
        likes.isNotEmpty() && cookbooks.isNotEmpty()
}