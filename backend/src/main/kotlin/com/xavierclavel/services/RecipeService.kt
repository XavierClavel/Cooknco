package com.xavierclavel.services

import com.xavierclavel.controllers.RecipeController.imageService
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Recipe
import com.xavierclavel.models.User
import com.xavierclavel.models.jointables.query.QCookbookRecipe
import com.xavierclavel.models.query.QRecipe
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

        return QRecipe()
            .fetch(QRecipe.Alias.likes.toString(), "count(*)", FetchConfig.ofLazy()) // Aggregate likes
            .filter(recipeFilter)
            .filterOutDeletion(requestorId)
            .filterByVisibility(requestorId)
            .having().raw("count(${QRecipe.Alias.likes.id}) >= 0") // Ensure recipes with no likes are included
            .setPaging(paging)
            .sort(sort, recipeFilter)
            .findList()
            .map { it.toOverview() }
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
            ?.toInfo(locale)
            ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)

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

    fun createRecipe(recipeDTO: RecipeDTO, owner: User): RecipeInfo =
        Recipe()
            .mergeDTO(recipeDTO)
            .setOwner(owner)
            .insertAndGet()
            .toInfo(Locale.EN)


    fun updateRecipe(id: Long, recipeDTO: RecipeDTO): RecipeInfo =
        getEntityById(id).mergeDTO(recipeDTO).updateAndGet().toInfo(Locale.EN)

    fun deleteRecipe(id: Long) {
        QRecipe().id.eq(id).delete()
    }

    fun tagRecipeForDeletion(id: Long) {
        getEntityById(id).tagForDeletion().update()
    }

    fun tryDelete(id: Long) {
        val recipe = getEntityById(id)
        val recipeInfo = recipe.toInfo(Locale.EN)
        logger.info {recipe}
        logger.info {"Has references: ${recipeInfo.likesCount > 0 || QCookbookRecipe().recipe.id.eq(recipe.id).exists()}"}
        if (recipeInfo.likesCount > 0 || QCookbookRecipe().recipe.id.eq(recipe.id).exists()) return
        recipe.delete()
        imageService.deleteImage(RECIPES_IMG_PATH, id, recipe.imageVersion)
        imageService.deleteImage(RECIPES_THUMBNAIL_PATH, id, recipe.imageVersion)
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