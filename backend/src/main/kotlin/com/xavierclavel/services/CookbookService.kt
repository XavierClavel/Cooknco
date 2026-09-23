package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Cookbook
import com.xavierclavel.models.jointables.CookbookRecipe
import com.xavierclavel.models.jointables.CookbookUser
import com.xavierclavel.models.jointables.query.QCookbookRecipe
import com.xavierclavel.models.jointables.query.QCookbookUser
import com.xavierclavel.models.query.QCookbook
import com.xavierclavel.utils.DbTransaction.insertAndGet
import com.xavierclavel.utils.DbTransaction.updateAndGet
import com.xavierclavel.utils.Extensions.page
import com.xavierclavel.utils.countByParent
import shared.dto.CookbookDTO
import shared.dto.CookbookUserDTO
import shared.enums.Sort
import shared.enums.Visibility
import shared.infodto.CookbookInfo
import shared.infodto.CookbookRecipeInfo
import shared.infodto.CookbookUserInfo
import shared.overviewdto.CookbookRecipeOverview
import shared.overviewdto.UserOverview
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CookbookService: KoinComponent {
    val userService: UserService by inject()
    val recipeService: RecipeService by inject()


    fun countAll() =
        QCookbook().findCount()

    /**
     * How many recipes each of [cookbookIds] holds, in one query — what a listing uses instead of
     * reading `cookbook.recipes`. See [com.xavierclavel.utils.countByParent].
     *
     * @return count per cookbook id; an empty cookbook is absent from the map
     */
    fun countRecipesByCookbook(cookbookIds: Collection<Long>): Map<Long, Int> =
        if (cookbookIds.isEmpty()) emptyMap()
        else QCookbookRecipe()
            .select("${QCookbookRecipe.Alias.cookbook.id}, count(*)")
            .cookbook.id.`in`(cookbookIds)
            .query()
            .countByParent()

    /** How many cookbooks each of [recipeIds] appears in, counted the same way. */
    fun countCookbooksByRecipe(recipeIds: Collection<Long>): Map<Long, Int> =
        if (recipeIds.isEmpty()) emptyMap()
        else QCookbookRecipe()
            .select("${QCookbookRecipe.Alias.recipe.id}, count(*)")
            .recipe.id.`in`(recipeIds)
            .query()
            .countByParent()

    /**
     * Everything a listing needs about each cookbook's membership: how many members it has, and the
     * first few of them by name.
     *
     * One query for the rows and one for the count, rather than the count being derived from the
     * rows — the rows are cut to [CookbookInfo.MEMBERS_SHOWN] per book, so counting them would make
     * a cookbook of forty report ten.
     */
    fun membershipOf(cookbookIds: Collection<Long>): Map<Long, Membership> {
        if (cookbookIds.isEmpty()) return emptyMap()
        val counts = countUsersByCookbook(cookbookIds)
        val members = QCookbookUser()
            .user.fetch()
            .cookbook.fetch(QCookbook.Alias.id)
            .cookbook.id.`in`(cookbookIds)
            .orderBy().joinDate.asc()
            .findList()
            .groupBy { it.cookbook.id }
        return cookbookIds.associateWith { id ->
            Membership(
                count = counts[id] ?: 0,
                shown = members[id].orEmpty().take(CookbookInfo.MEMBERS_SHOWN).map { it.user.toOverview() },
            )
        }
    }

    /** What [membershipOf] resolves per cookbook. */
    data class Membership(val count: Int, val shown: List<UserOverview>)

    private fun countUsersByCookbook(cookbookIds: Collection<Long>): Map<Long, Int> =
        QCookbookUser()
            .select("${QCookbookUser.Alias.cookbook.id}, count(*)")
            .cookbook.id.`in`(cookbookIds)
            .query()
            .countByParent()

    fun existsById(id: Long) = QCookbook().id.eq(id).exists()

    fun findEntityById(id: Long) : Cookbook? =
        QCookbook().id.eq(id).findOne()

    fun getEntityById(id: Long): Cookbook =
        findEntityById(id) ?: throw NotFoundException(NotFoundCause.COOKBOOK_NOT_FOUND)

    /**
     * The newest cookbook there is, for the backoffice to preview a document layout against
     * when the operator has not picked one. Private ones included, for the reason
     * [RecipeService.findMostRecent] includes hidden recipes: this is only reached from
     * behind the admin gate, and a layout shows off the same either way.
     */
    fun findMostRecent(): Cookbook =
        QCookbook()
            .orderBy().creationDate.desc()
            .setMaxRows(1)
            .findOne()
            ?: throw NotFoundException(NotFoundCause.COOKBOOK_NOT_FOUND)


    fun createCookbook(cookbookDTO: CookbookDTO): CookbookInfo =
        describe(Cookbook.from(cookbookDTO).insertAndGet())

    /**
     * One cookbook's [CookbookInfo], counted the same way a listing counts a page of them. Three
     * queries for one row rather than the two lazy loads it would otherwise cost — the point is
     * that there is one way to build the DTO, so a listing cannot drift from a single read.
     */
    fun describe(cookbook: Cookbook): CookbookInfo = describeAll(listOf(cookbook)).single()

    /** [describe] for a whole page, at a fixed cost whatever the page holds. */
    fun describeAll(cookbooks: List<Cookbook>): List<CookbookInfo> {
        val ids = cookbooks.map { it.id }
        val recipeCounts = countRecipesByCookbook(ids)
        val membership = membershipOf(ids)
        return cookbooks.map {
            it.toInfo(
                recipesCount = recipeCounts[it.id] ?: 0,
                membership = membership[it.id] ?: Membership(0, emptyList()),
            )
        }
    }

    fun getCookbook(cookbookId: Long, currentUserId: Long?): CookbookInfo {
        if (!existsById(cookbookId)) throw NotFoundException(NotFoundCause.COOKBOOK_NOT_FOUND)
        if (!QCookbook()
                .id.eq(cookbookId)
                .filterByVisibility(currentUserId)
                .exists()) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_SEE_COOKBOOK)
        }
        return describe(getEntityById(cookbookId))
    }

    fun listCookbooks(paging: Paging, sort:Sort, user: Long?, recipe: Long?, search: String?, currentUser: Long?) : List<CookbookInfo> =
        describeAll(
            QCookbook()
                .filterByUser(user)
                .filterByRecipe(recipe)
                .filterBySearch(search)
                .filterByVisibility(currentUser)
                .setPaging(paging)
                .findList()
        )

    fun getRecipeStatusInUserCookbooks(user: Long, recipe: Long) : List<CookbookRecipeOverview> {
        // The recipes are what the mark below is read off, so they are fetched rather than left to
        // lazy-load per cookbook. A join works here only because nothing pages this query: a
        // `-to-many` fetch path is dropped as soon as `maxRows` is set.
        val cookbooks = QCookbook()
            .recipes.fetch()
            .filterByUser(user)
            .orderBy().title.desc()
            .findList()

        val partition = cookbooks.partition { it.recipes.any { it.recipe.id == recipe } }
        val cookbooksContainingRecipe = partition.first.map { it.toRecipeOverview(true) }
        val cookbooksMissingRecipe = partition.second.map { it.toRecipeOverview(false) }

        return (cookbooksContainingRecipe + cookbooksMissingRecipe).sortedBy { it.title }
    }



    fun getCookbookUsers(id: Long, paging: Paging): List<CookbookUserInfo> =
        getEntityById(id)
            .users
            .map { it.toInfo() }
            .page(paging)

    fun getCookbookRecipes(id: Long, paging: Paging): List<CookbookRecipeInfo> =
        getEntityById(id)
            .recipes
            .map { it.toInfo() }
            .page(paging)

    fun doesCookbookHaveRecipe(cookbookId: Long, recipeId: Long): Boolean =
        QCookbook()
            .id.eq(cookbookId)
            .filterByRecipe(recipeId)
            .exists()

    fun updateCookbook(id: Long, cookbookDTO: CookbookDTO) =
        describe(getEntityById(id).merge(cookbookDTO).updateAndGet())

    fun deleteCookbook(id: Long): Boolean =
        getEntityById(id).delete()

    fun setCookbookUsers(cookbookId: Long, userInput: List<CookbookUserDTO>) {
        val currentUsers = getEntityById(cookbookId).users
        currentUsers.forEach{ current ->
            val newUser = userInput.find { it.id == current.user.id }
            if (newUser == null) { current.delete() }
            else if (newUser.isAdmin != current.isAdmin) {
                current
                    .apply { isAdmin = newUser.isAdmin }
                    .update()
            }
        }
        userInput.forEach { new ->
            val currentUser = currentUsers.find { new.id == it.user.id }
            if (currentUser == null) {
                CookbookUser(
                    user = userService.getEntityById(new.id),
                    cookbook = getEntityById(cookbookId),
                    isAdmin = new.isAdmin,
                ).insert()
            }

        }
        deleteIfNoMembers(cookbookId)
    }

    fun addUserToCookbook(cookbookId: Long, userId: Long, isAdmin: Boolean) {
        val cookbook = getEntityById(cookbookId)
        val user = userService.getEntityById(userId)
        val cookbookUser = CookbookUser(
            user = user,
            isAdmin = isAdmin,
            cookbook = cookbook,
        ).insertAndGet()
    }

    fun addRecipeToCookbook(cookbookId: Long, recipeId: Long, userId: Long) {
        val cookbook = getEntityById(cookbookId)
        val recipe = recipeService.getEntityById(recipeId)
        val cookbookRecipe = CookbookRecipe(
            cookbook = cookbook,
            addedBy = userService.getEntityById(userId),
            recipe = recipe,
        ).insertAndGet()

    }


    fun removeRecipeFromCookbook(cookbookId: Long, recipeId: Long): Boolean? =
        QCookbookRecipe()
            .recipe.id.eq(recipeId)
            .cookbook.id.eq(cookbookId)
            .findOne()
            ?.delete()
            ?: throw BadRequestException(BadRequestCause.RECIPE_NOT_IN_COOKBOOK)


    fun removeUserFromCookbook(cookbookId: Long, userId: Long): Boolean? =
        QCookbookUser()
            .user.id.eq(userId)
            .and()
            .cookbook.id.eq(cookbookId)
            .findOne()
            ?.delete()
            ?.apply { deleteIfNoMembers(cookbookId) }

    private fun QCookbook.filterByUser(userId: Long?) =
        if (userId == null) this else this.where().users.user.id.eq(userId)

    private fun QCookbook.filterByVisibility(currentUserId: Long?) =
        if (currentUserId == null) this.where().visibility.eq(Visibility.PUBLIC)
        else this.or()
            .visibility.eq(Visibility.PUBLIC)
            .users.user.id.eq(currentUserId)
            .and()
            .visibility.eq(Visibility.PROTECTED)
            .users.user.followers.follower.id.eq(currentUserId)
            .users.user.followers.pending.eq(false)
            .endAnd()
            .endOr()

    private fun QCookbook.filterByRecipe(recipeId: Long?) =
        if (recipeId == null) this else this.where().recipes.recipe.id.eq(recipeId)

    private fun QCookbook.filterBySearch(searchString: String?) =
        if (searchString == null) this else this.title.ilike("%$searchString%")

    fun isMemberOfCookbook(cookbookId: Long, userId: Long): Boolean =
        QCookbookUser()
            .cookbook.id.eq(cookbookId)
            .user.id.eq(userId)
            .exists()

    fun isAdminOfCookbook(cookbookId: Long, userId: Long): Boolean =
        QCookbookUser()
            .cookbook.id.eq(cookbookId)
            .user.id.eq(userId)
            .isAdmin.isTrue
            .exists()

    fun getCookbookRecipeAdder(cookbookId: Long, recipeId: Long) =
        QCookbookRecipe()
            .cookbook.id.eq(cookbookId)
            .recipe.id.eq(recipeId)
            .findOne()
            ?.addedBy?.id
            ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)

    fun deleteIfNoMembers(cookbookId: Long) {
        val users = getEntityById(cookbookId).users
        if (users.isEmpty()) {
            deleteCookbook(cookbookId)
        } else if (users.count { it.isAdmin } == 0) {
            val oldestUser = users.minBy { it.joinDate }
            oldestUser.isAdmin = true
            oldestUser.update()
        }

    }

}