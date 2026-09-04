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
import shared.dto.CookbookDTO
import shared.dto.CookbookUserDTO
import shared.enums.Sort
import shared.enums.Visibility
import shared.infodto.CookbookInfo
import shared.infodto.CookbookRecipeInfo
import shared.infodto.CookbookUserInfo
import shared.overviewdto.CookbookRecipeOverview
import io.ebean.FetchConfig
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CookbookService: KoinComponent {
    val userService: UserService by inject()
    val recipeService: RecipeService by inject()


    fun countAll() =
        QCookbook().findCount()

    fun existsById(id: Long) = QCookbook().id.eq(id).exists()

    fun findEntityById(id: Long) : Cookbook? =
        QCookbook().id.eq(id).findOne()

    fun getEntityById(id: Long): Cookbook =
        findEntityById(id) ?: throw NotFoundException(NotFoundCause.COOKBOOK_NOT_FOUND)


    fun createCookbook(cookbookDTO: CookbookDTO): CookbookInfo =
        Cookbook.from(cookbookDTO).insertAndGet().toInfo()

    fun getCookbook(cookbookId: Long, currentUserId: Long?): CookbookInfo {
        if (!existsById(cookbookId)) throw NotFoundException(NotFoundCause.COOKBOOK_NOT_FOUND)
        if (!QCookbook()
                .id.eq(cookbookId)
                .filterByVisibility(currentUserId)
                .exists()) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_SEE_COOKBOOK)
        }
        return getEntityById(cookbookId).toInfo()
    }

    fun listCookbooks(paging: Paging, sort:Sort, user: Long?, recipe: Long?, search: String?, currentUser: Long?) : List<CookbookInfo> =
        QCookbook()
            .users.fetchLazy()
            //.fetch(QCookbook.Alias.users.toString(), "count(*)", FetchConfig.ofLazy())
            //.having().raw("count(${QCookbook.Alias.users.user.id}) >= 0")
            .filterByUser(user)
            .filterByRecipe(recipe)
            .filterBySearch(search)
            .filterByVisibility(currentUser)
            .setPaging(paging)
            .findList()
            .map { it.toInfo() }

    fun getRecipeStatusInUserCookbooks(user: Long, recipe: Long) : List<CookbookRecipeOverview> {
        val cookbooks = QCookbook()
            .users.fetchLazy()
            .recipes.fetchLazy()
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
        getEntityById(id)
            .merge(cookbookDTO)
            .updateAndGet()
            .toInfo()

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