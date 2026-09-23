package com.xavierclavel.services

import com.xavierclavel.controllers.RecipeController.imageService
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Recipe
import com.xavierclavel.models.RecipeStep
import com.xavierclavel.models.User
import com.xavierclavel.models.jointables.query.QCookbookRecipe
import com.xavierclavel.models.jointables.query.QLike
import com.xavierclavel.models.query.QRecipe
import io.ebean.DB
import com.xavierclavel.models.jointables.query.QRecipeStepIngredient
import com.xavierclavel.models.query.QRecipeStep
import com.xavierclavel.models.query.QUser
import com.xavierclavel.utils.DbTransaction.insertAndGet
import com.xavierclavel.utils.DbTransaction.updateAndGet
import com.xavierclavel.utils.logger
import com.xavierclavel.utils.sqlStringLiteral
import shared.RecipeFilter
import shared.dto.RecipeDTO
import shared.enums.DishClass
import shared.enums.Locale
import shared.enums.Sort
import shared.infodto.RecipeInfo
import shared.overviewdto.RecipeOverview
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import io.ebean.FetchConfig
import io.ebean.Paging
import java.time.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RecipeService: KoinComponent {
    val userService: UserService by inject()
    val likeService: LikeService by inject()
    val ingredientService: IngredientService by inject()

    fun countAll() =
        QRecipe().findCount()

    fun countHidden() =
        QRecipe().isHidden.eq(true).findCount()

    fun countCreatedSince(since: LocalDateTime) =
        QRecipe().creationDate.gt(since).findCount()

    fun countByOwner(username: String) =
        queryByOwner(username).findCount()


    fun findList(
        requestorId: Long?,
        paging: Paging,
        sort: Sort,
        recipeFilter: RecipeFilter,
    ) : List<RecipeOverview> {

        val recipes = QRecipe()
            // Every row states its author, so the owner is joined in rather than lazy-loaded. A
            // `-to-one` batch-loads, so this saves one query per page rather than one per row —
            // which is also why no fetch-plan test would catch its removal.
            .owner.fetch()
            .fetch(QRecipe.Alias.likes.toString(), "count(*)", FetchConfig.ofLazy()) // Aggregate likes
            .filter(recipeFilter)
            .filterOutDeletion(requestorId)
            .filterByVisibility(requestorId)
            .having().raw("count(${QRecipe.Alias.likes.id}) >= 0") // Ensure recipes with no likes are included
            .setPaging(paging)
            .sort(sort, recipeFilter)
            .findList()

        // The aggregate above is what `sort` orders by; it does *not* reach the mapper. Ebean drops
        // a `-to-many` from the fetch plan once `maxRows` is set, so `recipe.likes` is unloaded here
        // however it was asked for, and reading its size would cost a query per row.
        val likes = likeService.countLikesByRecipe(recipes.map { it.id })
        return recipes.map { it.toOverview(likesCount = likes[it.id] ?: 0) }
    }


    fun findEntityById(recipeId: Long) : Recipe? =
        QRecipe().id.eq(recipeId).findOne()

    /**
     * Maps every recipe owned by one of [ownerIds] to its owner, so callers can attribute
     * per-recipe data back to accounts without a query per recipe.
     */
    fun mapRecipeIdsToOwnerIds(ownerIds: Collection<Long>): Map<Long, Long> =
        QRecipe()
            .select(QRecipe.Alias.id)
            .owner.fetch(QUser.Alias.id)
            .owner.id.`in`(ownerIds)
            .findList()
            .mapNotNull { recipe -> recipe.owner?.let { recipe.id to it.id } }
            .toMap()

    fun getEntityById(recipeId: Long) : Recipe =
        findEntityById(recipeId)
            ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)

    /**
     * The newest recipe there is, for the backoffice to preview a document layout against
     * when the operator has not picked one. Hidden recipes included: this is only ever
     * reached from behind the admin gate, and a moderated recipe still shows a layout off.
     */
    fun findMostRecent() : Recipe =
        QRecipe()
            .orderBy().creationDate.desc()
            .setMaxRows(1)
            .findOne()
            ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)

    /**
     * Every recipe a cookbook holds, in the order the book prints them.
     *
     * By title rather than by when each was added: a book is read by looking a recipe up,
     * and the addition order is a fact about the cookbook's history that no reader of the
     * printed copy can see. Soft-deleted recipes drop out on their own — Ebean applies the
     * flag to its own queries — which is right, because a deleted recipe is gone rather
     * than hidden.
     *
     * A typed `-to-many` predicate rather than the `EXISTS` [filterByCookbook] uses: that
     * one exists because the join double-counts against the like aggregate in [findList],
     * and there is no aggregate here. The owner is fetched because every sheet prints a
     * byline; the steps and ingredients load per recipe, which is what a bounded export run once
     * can afford — the print itself costs orders of magnitude more. That is the one read path here
     * deliberately left scaling with its rows, so it has no guard in `RecipeFetchPlanTest`: the
     * `maxRows` this sets is exactly what stops Ebean fetching those collections, and getting them
     * back would mean loading the book twice or assigning the rows onto the beans by hand. What
     * does *not* scale is what `describeAll` adds — the like counts and the ingredient names — both
     * resolved for the whole book at once.
     *
     * @param limit how many rows to read at most. Callers asking whether a cookbook is
     *   within a bound pass the bound plus one and compare, which answers that in the one
     *   query that also fetches the recipes — rather than counting first and then reading,
     *   which loads nothing extra but can disagree with itself if a recipe is added in
     *   between.
     * @param visibleTo the account the book is being read for, whose visibility rules every
     *   recipe in it must pass. Null prints what the cookbook holds, hidden rows and all,
     *   and is only ever the moderator export — *not* an anonymous reader, who could not
     *   have got this far: every path here has a session behind it.
     */
    fun findByCookbook(cookbookId: Long, limit: Int, visibleTo: Long? = null): List<Recipe> =
        QRecipe()
            .owner.fetch()
            .cookbooks.cookbook.id.eq(cookbookId)
            .apply { if (visibleTo != null) filterByVisibility(visibleTo) }
            .orderBy().title.asc()
            .setMaxRows(limit)
            .findList()

    fun existsById(recipeId:Long, userId: Long?) =
        QRecipe()
            .id.eq(recipeId)
            .filterOutDeletion(userId)
            .exists()

    /**
     * Restricts a query to the recipes [userId] is allowed to see.
     *
     * Moderation is enforced here rather than per-endpoint: a hidden recipe, or any recipe
     * whose author is banned, drops out of every listing and lookup. The author keeps
     * seeing their own recipes either way, so a hidden recipe never silently vanishes on
     * the person who wrote it.
     */
    fun QRecipe.filterByVisibility(userId: Long?): QRecipe {

        if (userId == null) {
            // Anonymous users: only public, visible recipes
            return this.and()
                .isHidden.isFalse
                .owner.isBanned.isFalse
                .owner.isAccountPublic.isTrue
                .endAnd()
        }

        return this.or()
            .owner.id.eq(userId) // Owner
            .and()
                .isHidden.isFalse
                .owner.isBanned.isFalse
                .or()
                    .owner.isAccountPublic.isTrue // Public
                    .and() // Follower
                        .owner.followers.follower.id.eq(userId)
                        .owner.followers.pending.isFalse
                    .endAnd()
                    .raw("exists (select 1 from likes l where l.recipe_id = ${QRecipe.Alias.id} and l.user_id = ?)", userId)
                    .raw("""
                        exists (
                            select 1
                            from cookbook_recipes cr
                            join cookbooks c on c.id = cr.cookbook_id
                            join cookbook_users cu on cu.cookbook_id = c.id
                            where cr.recipe_id = ${QRecipe.Alias.id}
                            and cu.user_id = ?
                        )
                        """.trimIndent(), userId
                    )
                .endOr()
            .endAnd()
            .endOr()
    }

    fun getRawById(recipeId: Long, userId: Long?, locale: Locale): RecipeInfo =
        QRecipe()
            .id.eq(recipeId)
            .filterOutDeletion(userId)
            .findOne()
            ?.let { describe(it, locale) }
            ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)

    /**
     * One recipe's [RecipeInfo], with its like count resolved the way a list resolves a page of
     * them — so a single read and a listing cannot come to different numbers.
     */
    fun describe(recipe: Recipe, locale: Locale): RecipeInfo = describeAll(listOf(recipe), locale).single()

    /** [describe] for several recipes, at a fixed cost whatever the list holds. */
    fun describeAll(recipes: List<Recipe>, locale: Locale): List<RecipeInfo> {
        val likes = likeService.countLikesByRecipe(recipes.map { it.id })
        // Every line naming a catalogue entry reads what it is called, which lives in a collection
        // on the entry rather than on its row. Resolved once for the whole list: it is a query per
        // line otherwise, and the lines of a cookbook export run into the hundreds.
        val catalogueNames = ingredientService.namesOf(
            recipes.flatMap { recipe -> recipe.ingredients.mapNotNull { it.ingredient?.id } }.toSet(),
            locale,
        )
        return recipes.map { it.toInfo(locale, likesCount = likes[it.id] ?: 0, catalogueNames = catalogueNames) }
    }

    fun getById(userId: Long?, recipeId: Long, locale: Locale) : RecipeInfo {
        if (!existsById(recipeId, userId)) throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)
        if (!QRecipe()
            .id.eq(recipeId)
            .filterByVisibility(userId)
            .exists()) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_SEE_RECIPE)
        }
        return getRawById(recipeId, userId, locale)
    }

    fun deleteById(recipeId: Long) {
        QRecipe().id.eq(recipeId).delete()
    }


    fun getRecipeOwner(recipeId: Long) =
        getEntityById(recipeId).owner
            ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)

    fun createRecipe(recipeDTO: RecipeDTO, owner: User): RecipeInfo {
        val recipe = Recipe().mergeDTO(recipeDTO).setOwner(owner).insertAndGet()
        saveSteps(recipe.id, recipeDTO.steps)
        return describe(getEntityById(recipe.id), Locale.EN)
    }

    fun updateRecipe(id: Long, recipeDTO: RecipeDTO): RecipeInfo {
        getEntityById(id).mergeDTO(recipeDTO).update()
        saveSteps(id, recipeDTO.steps)
        return describe(getEntityById(id), Locale.EN)
    }

    /**
     * Brings a recipe's steps in line with what was sent, keeping the rows that are still
     * there.
     *
     * A step used to be rewritten on every save - a habit inherited from when steps were an
     * `@ElementCollection` of strings, which has no row identity to keep. Once a step is a row
     * other rows point at, that is a foreign key waiting to be tripped, and it leaves nothing
     * that can be hung off a step by id. So a step the client names by id is updated where it
     * is, one it does not is inserted, and one it has stopped mentioning is deleted.
     *
     * An id this recipe does not have is treated as a new step. It can only come from a client
     * working from a stale copy, and the alternatives are worse: refusing would fail a save
     * over something the cook cannot see, and honouring it would let one recipe write over
     * another's step.
     *
     * The links to ingredients go before the steps they hang off do - `recipe_step_ingredients`
     * restricts deletes at both ends, and the rows being deleted here are exactly the ones
     * still pointed at.
     */
    fun saveSteps(recipeId: Long, steps: List<RecipeDTO.RecipeStepDTO>) {
        val existing = QRecipeStep().recipe.id.eq(recipeId).findList().associateBy { it.id }
        val recipe = getEntityById(recipeId)

        DB.beginTransaction().use { transaction ->
            val kept = mutableSetOf<Long>()
            steps.forEachIndexed { index, dto ->
                val row = dto.id?.let { existing[it] }
                if (row == null) {
                    RecipeStep.of(dto, index).also { it.recipe = recipe }.insert()
                } else {
                    kept += row.id
                    row.mergeDto(dto, index).update()
                }
            }

            val removed = existing.keys - kept
            if (removed.isNotEmpty()) {
                QRecipeStepIngredient().step.id.`in`(removed).delete()
                QRecipeStep().id.`in`(removed).delete()
            }
            transaction.commit()
        }
    }

    fun deleteRecipe(id: Long) {
        QRecipe().id.eq(id).delete()
    }

    fun tagRecipeForDeletion(id: Long) {
        getEntityById(id).tagForDeletion().update()
    }

    /**
     * Takes a recipe out of the product without destroying it.
     *
     * A recipe that something still points at — a like, a cookbook — is kept whole instead,
     * because it is not only its owner's any more. Callers must check `taggedForDeletion`
     * themselves before calling this: it is the tag that says the owner asked, and nothing
     * here reads it (see `LikeController.deleteLike`, which learned that the hard way).
     *
     * `delete()` is a *soft* delete here, and it leaves the children alone: Ebean only
     * cascades a soft delete to a child that is soft-deletable itself
     * (`DefaultPersister.deleteManyDetails`: `if (deleteMode.isHard() || targetDesc.isSoftDelete())`,
     * commented "only cascade soft deletes when supported by target"). None of steps,
     * ingredients, likes or cookbook links carry the annotation, so all of them survive —
     * which is what makes the restore below give back a whole recipe rather than an empty one.
     *
     * The pictures survive for a different reason: nothing deletes them any more.
     * `StorageService` reads owning rows over raw SQL, which does not apply the flag, so a
     * deleted recipe still counts as their owner and they are never offered up as orphans.
     */
    fun tryDelete(id: Long) {
        val recipe = getEntityById(id)
        val referenced = QLike().recipe.id.eq(recipe.id).exists() ||
            QCookbookRecipe().recipe.id.eq(recipe.id).exists()
        logger.info { "Deleting recipe ${recipe.id} (${recipe.title}); has references: $referenced" }
        if (referenced) return
        recipe.delete()
    }

    /**
     * Puts a soft-deleted recipe back, with everything that was still attached to it.
     *
     * Clears `taggedForDeletion` as well as the flag, and both halves matter. The tag is
     * what `filterOutDeletion` hides an owner's own recipe by, so a recipe restored with it
     * still set is invisible to the one person who asked for it back; and it is what
     * `LikeController` reads before purging, so the next unlike would delete it again.
     *
     * Not reachable from the API — there is no screen for it — but this is the whole point
     * of the flag, and the operator running it from a console should be running tested code
     * rather than inventing the `update` on the spot.
     */
    fun restore(id: Long): Boolean {
        val recipe = QRecipe().setIncludeSoftDeletes().id.eq(id).findOne() ?: return false
        if (!recipe.deleted) return false
        recipe.deleted = false
        recipe.taggedForDeletion = false
        recipe.update()
        logger.info { "Restored recipe ${recipe.id} (${recipe.title})" }
        return true
    }

    private fun queryByOwner(username: String) =
        QRecipe().owner.username.eq(username)

    private fun QRecipe.filter(recipeFilter: RecipeFilter) =
        if (!recipeFilter.hasFilters()) this
        else this
            .and()
            .apply {
                if (recipeFilter.hasAdditiveFilters()) {
                    this.or()
                        .filterByLikes(recipeFilter.likedBy)
                        .filterByOwner(recipeFilter.user)
                        .filterByCookbook(recipeFilter.cookbook)
                        .filterByUserCookbooks(recipeFilter.cookbookUser)
                        .filterByFollowed(recipeFilter.followedBy)
                        .endOr()
                }
            }
            .filterByDishClass(recipeFilter.dishClasses)
            .filterByIngredient(recipeFilter.ingredient)
            .filterBySearch(recipeFilter.search)
            .endAnd()


    private fun QRecipe.filterByOwner(userId: Long?) =
        if (userId == null) this
        else this.where()
            .owner.id.eq(userId)

    private fun QRecipe.filterOutDeletion(userId: Long?) =
        if (userId == null) this
        else this.where()
            .and().not()
                .owner.id.eq(userId)
                .taggedForDeletion.eq(true)
            .endNot().endAnd()

    private fun QRecipe.filterByDishClass(dishClasses: Set<DishClass>) =
        if (dishClasses.isEmpty()) this
        else this.where().dishClass.`in`(dishClasses)

    private fun QRecipe.filterByLikes(userId: Long?) =
        if (userId == null) this
        else this//.likes.user.id.eq(userId)
            .where().raw("EXISTS (SELECT 1 FROM likes l WHERE l.recipe_id = ${QRecipe.Alias.id} AND l.user_id = ?)", userId)


    private fun QRecipe.filterByCookbook(cookbookId: Long?) =
        if (cookbookId == null) this
        else this
            .where().raw("""
                EXISTS (
                    SELECT 1 FROM cookbook_recipes cr 
                    WHERE cr.recipe_id = ${QRecipe.Alias.id}
                    AND cr.cookbook_id = ?
                )""".trimIndent(), cookbookId)
            //.where().cookbooks.cookbook.id.eq(cookbookId)

    private fun QRecipe.filterByUserCookbooks(userId: Long?) =
        if (userId == null) this
        else this.raw("""
            EXISTS(
                SELECT 1
                FROM cookbook_recipes cr
                JOIN cookbooks c ON cr.cookbook_id = c.id
                JOIN cookbook_users cu ON cu.cookbook_id = c.id
                WHERE cr.recipe_id = ${QRecipe.Alias.id}
                AND cu.user_id = ?
            )""".trimIndent(), userId)

    private fun QRecipe.filterByFollowed(followerId: Long?) =
        if (followerId == null) this
        else this.raw("""
            EXISTS(
                SELECT 1
                FROM follows f
                JOIN users u ON f.user_id = u.id
                WHERE f.follower_id = ?
                AND f.pending = false
                AND ${QRecipe.Alias.owner.id} = f.user_id
            )""".trimIndent(), followerId)

    private fun QRecipe.isPublic() =
        this.owner.isAccountPublic.eq(true)

    private fun QRecipe.isOwnerFollowed(userId: Long?) {
        if (userId == null) throw ForbiddenException(ForbiddenCause.ACCOUNT_NOT_PUBLIC)
        this.filterByFollowed(userId)
    }

    private fun QRecipe.filterByIngredient(ingredientsId: Set<Long>) =
        if (ingredientsId.isEmpty()) this
        else this.raw("""
            EXISTS(
                SELECT 1
                FROM recipe_ingredients ri
                JOIN ingredients i ON ri.ingredient_id = i.id
                WHERE ri.recipe_id = ${QRecipe.Alias.id}
                AND i.id = ANY(?)
                GROUP BY ri.recipe_id
                HAVING COUNT(DISTINCT i.id) = ?
        )""".trimIndent(), ingredientsId.toList(), ingredientsId.size)

    private fun QRecipe.filterBySearch(search: String?) =
        if (search.isNullOrEmpty()) this
        else this.apply {
            // word_similarity matches the query against the best-matching part of the
            // title, so short queries still match long titles (similarity would not)
            this.raw("word_similarity(unaccent(?), unaccent(${QRecipe.Alias.title})) > 0.3", search)
        }

    private fun QRecipe.sort(sort: Sort, recipeFilter: RecipeFilter) =
        when (sort) {
            Sort.NONE -> this
            Sort.NAME_ASCENDING -> this.orderBy().title.desc()
            Sort.NAME_DESCENDING -> this.orderBy().title.asc()
            Sort.LIKES_ASCENDING -> this.orderBy("count(${QRecipe.Alias.likes.id}) asc")
            Sort.LIKES_DESCENDING -> this.orderBy("count(${QRecipe.Alias.likes.id}) desc")
            Sort.DATE_ASCENDING -> this.orderBy().creationDate.asc()
            Sort.DATE_DESCENDING -> this.orderBy().creationDate.desc()
            Sort.RANDOM -> this.orderBy("random()")
            // Ebean copies orderBy strings into SQL verbatim (no parameter binding),
            // so the search term is inlined as an injection-proof hex literal
            Sort.BEST_MATCH -> this.orderBy(
                "word_similarity(unaccent(${sqlStringLiteral(recipeFilter.search.orEmpty())}), unaccent(${QRecipe.Alias.title})) desc"
            )
        }

}