package main.com.xavierclavel.other

import com.xavierclavel.ApplicationTest
import com.xavierclavel.controllers.CookbookController.cookbookService
import com.xavierclavel.controllers.FollowController.followService
import com.xavierclavel.controllers.LikeController.likeService
import com.xavierclavel.utils.logger
import shared.dto.CookbookDTO
import shared.dto.UserSettingsDTO
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.getRecipeRaw
import main.com.xavierclavel.utils.listRecipes
import main.com.xavierclavel.utils.recipeDTO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecipeVisibilityTest: ApplicationTest() {

    @Test
    fun `anyone can see recipe of a public account`() = runTest{
        var recipeId: Long? = null
        runAsUser1 {
            recipeId = client.createRecipe(recipeDTO).id
        }
        runAsUser2 {
            client.getRecipe(recipeId!!)
        }
    }

    @Test
    fun `anyone can list recipes of a public account`() = runTest{
        runAsUser1 {
            client.createRecipe(recipeDTO).id
        }
        runAsUser2 {
            val result = client.listRecipes(user = userService.findByMail(USER1)!!.id)
            assertEquals(1, result.size)
        }
    }

    @Test
    fun `users not logged in can see recipe of a public account`() = runTest{
        var recipeId: Long? = null
        runAsUser1 {
            recipeId = client.createRecipe(recipeDTO).id
        }
        client.getRecipe(recipeId!!)
    }

    @Test
    fun `users not logged in can list recipes of a public account`() = runTest{
        runAsUser1 {
            client.createRecipe(recipeDTO).id
        }
        val result = client.listRecipes(user = userService.findByMail(USER1)!!.id)
        assertEquals(1, result.size)
    }

    @Test
    fun `private account can see their own recipe`() = runTest{
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            val recipeId = client.createRecipe(recipeDTO).id
            client.getRecipe(recipeId)
        }
    }

    @Test
    fun `private account can list their own recipes`() = runTest{
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            client.createRecipe(recipeDTO).id
            val result = client.listRecipes(user = userService.findByMail("user3")!!.id)
            assertEquals(1, result.size)
        }
    }

    @Test
    fun `recipes liked from a private account can be seen`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        likeService.createLike(recipeId!!, userService.findByMail(USER1)!!.id)
        logger.info {likeService.countByRecipe(recipeId)}
        runAsUser1 {
            client.getRecipe(recipeId)
        }
    }

    @Test
    fun `followers can see private recipe`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        recipeId!!
        followService.createFollow(userService.findByMail("user3")!!.id, userService.findByMail(USER1)!!.id)
        followService.acceptFollowRequest(userService.findByMail("user3")!!.id, userService.findByMail(USER1)!!.id)
        runAsUser1 {
            client.getRecipe(recipeId)
        }
    }

    @Test
    fun `pending followers cannot see private recipe`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        recipeId!!
        followService.createFollow(userService.findByMail("user3")!!.id, userService.findByMail(USER1)!!.id)
        runAsUser1 {
            client.getRecipeRaw(recipeId).apply {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
        }
    }

    @Test
    fun `followers can list recipes of private account`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        recipeId!!
        followService.createFollow(userService.findByMail("user3")!!.id, userService.findByMail(USER1)!!.id)
        followService.acceptFollowRequest(userService.findByMail("user3")!!.id, userService.findByMail(USER1)!!.id)
        runAsUser1 {
            val result = client.listRecipes(user = userService.findByMail("user3")!!.id)
            assertEquals(1, result.size)
        }
    }

    @Test
    fun `pending followers cannot list recipes of private account`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        recipeId!!
        followService.createFollow(userService.findByMail("user3")!!.id, userService.findByMail(USER1)!!.id)
        runAsUser1 {
            val result = client.listRecipes(user = userService.findByMail("user3")!!.id)
            assertEquals(0, result.size)
        }
    }

    @Test
    fun `members of cookbook can see private recipe`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        recipeId!!
        val cookbookInfo = cookbookService.createCookbook(CookbookDTO("cookbook"))
        cookbookService.addRecipeToCookbook(cookbookInfo.id, recipeId, userService.findByMail("user3")!!.id)
        cookbookService.addUserToCookbook(cookbookInfo.id, userService.findByMail(USER1)!!.id, false)
        runAsUser1 {
            client.getRecipe(recipeId)
        }
    }


    @Test
    fun `other users cannot see private recipe`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        runAsUser1 {
            client.getRecipeRaw(recipeId!!).apply {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
        }
    }

    @Test
    fun `other users cannot list recipes of private account`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        runAsUser1 {
            val result = client.listRecipes(user = userService.findByMail("user3")!!.id)
            assertEquals(0, result.size)
        }
    }

    @Test
    fun `users not logged in cannot see private recipe`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
        client.getRecipeRaw(recipeId!!).apply {
            assertEquals(HttpStatusCode.Forbidden, status)
        }
    }

    @Test
    fun `users not logged in cannot list recipes of private account`() = runTest{
        var recipeId: Long? = null
        setupTestUser("user3", UserSettingsDTO(isAccountPublic = false))
        runAs("user3") {
            recipeId = client.createRecipe(recipeDTO).id
        }
            val result = client.listRecipes(user = userService.findByMail("user3")!!.id)
            assertEquals(0, result.size)
    }

    //TODO: cookbook recipes visibility/rights
}