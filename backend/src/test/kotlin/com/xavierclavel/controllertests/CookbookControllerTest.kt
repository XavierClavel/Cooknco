package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.utils.logger
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.addCookbookRecipe
import main.com.xavierclavel.utils.addCookbookUser
import main.com.xavierclavel.utils.assertCookbookDoesNotExist
import main.com.xavierclavel.utils.assertCookbookExists
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.deleteCookbook
import main.com.xavierclavel.utils.deleteCookbookRecipe
import main.com.xavierclavel.utils.deleteCookbookUser
import main.com.xavierclavel.utils.getCookbook
import main.com.xavierclavel.utils.getCookbookRaw
import main.com.xavierclavel.utils.getCookbookRecipes
import main.com.xavierclavel.utils.getCookbookUsers
import main.com.xavierclavel.utils.getRecipeUserCookbooks
import main.com.xavierclavel.utils.leaveCookbook
import main.com.xavierclavel.utils.listCookbooks
import main.com.xavierclavel.utils.listRecipes
import org.junit.jupiter.api.Test
import shared.infodto.CookbookInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CookbookControllerTest : ApplicationTest() {
    @Test
    fun `create cookbook`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)
    }

    @Test
    fun `creating cookbook grants admin access to it`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)
        val users = client.getCookbookUsers(cookbook.id)
        assertEquals(1, users.size)
        assertEquals(true, users.first().isAdmin)
    }

    @Test
    fun `delete cookbook`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)
        client.deleteCookbook(cookbook.id)
        client.assertCookbookDoesNotExist(cookbook.id)
    }

    @Test
    fun `add user to cookbook`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)
        val user = client.createUser("user3@mail.com")
        client.addCookbookUser(cookbook.id, user.id, false)
        val users = client.getCookbookUsers(cookbook.id)
        assertEquals(2, users.size)
        assertTrue(users.any { it.id == user.id })
    }

    @Test
    fun `add multiple users to cookbook`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)

        repeat(3) {
            val user = client.createUser()
            client.addCookbookUser(cookbook.id, user.id, false)
        }
        val users = client.getCookbookUsers(cookbook.id)
        assertEquals(4, users.size)
    }

    @Test
    fun `delete cookbook user`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)

        val user = userService.findByMail(USER1)
        client.addCookbookUser(cookbook.id, user.id, false)
        val users = client.getCookbookUsers(cookbook.id)
        assertEquals(2, users.size)
        assertTrue(users.any { it.id == user.id})

        client.deleteCookbookUser(cookbook.id, user.id)
        val users2 = client.getCookbookUsers(cookbook.id)
        assertEquals(1, users2.size)
        assertTrue(users2.none { it.id == user.id })
    }

    @Test
    fun `add recipes to cookbook`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)

        repeat(30) {
            val recipe = client.createRecipe()
            client.addCookbookRecipe(cookbook.id, recipe.id)
        }

        val recipes = client.getCookbookRecipes(cookbook.id)
        assertEquals(20, recipes.size)
    }

    @Test
    fun `list user cookbooks`() = runTestAsAdmin {
        val cookbook1 = client.createCookbook()
        val cookbook2 = client.createCookbook()
        val cookbook3 = client.createCookbook()
        val cookbook4 = client.createCookbook()

        val user1 = client.createUser()
        val user2 = client.createUser()
        val user3 = client.createUser()

        client.addCookbookUser(cookbook1.id, user1.id, false)
        client.addCookbookUser(cookbook2.id, user1.id, false)
        client.addCookbookUser(cookbook2.id, user2.id, false)
        client.addCookbookUser(cookbook3.id, user2.id, false)

        val result1 = client.listCookbooks(user1.id).map { it.id }.toSet()
        val result2 = client.listCookbooks(user2.id).map { it.id }.toSet()
        val result3 = client.listCookbooks(user3.id).map { it.id }.toSet()

        val expected1 = setOf(cookbook1.id, cookbook2.id)
        val expected2 = setOf(cookbook2.id, cookbook3.id)
        val expected3 = setOf<Long>()

        assertEquals(expected1, result1)
        assertEquals(expected2, result2)
        assertEquals(expected3, result3)
    }

    @Test
    fun `count cookbook users and recipes`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        client.assertCookbookExists(cookbook.id)

        repeat(2) {
            val user = client.createUser()
            client.addCookbookUser(cookbook.id, user.id, false)
        }

        repeat(2) {
            val recipe = client.createRecipe()
            client.addCookbookRecipe(cookbook.id, recipe.id)
        }

        val result = client.getCookbook(cookbook.id)
        assertEquals(3, result.usersCount)
        assertEquals(2, result.recipesCount)
        // The count and the names come from two different queries, so they are asserted together:
        // the names are cut at CookbookInfo.MEMBERS_SHOWN and the count must survive the cut
        assertEquals(3, result.members.size)
        assertEquals(
            client.getCookbookUsers(cookbook.id).map { it.username }.toSet(),
            result.members.map { it.username }.toSet(),
        )
    }

    /** A cookbook with more members than it names still says how many there are. */
    @Test
    fun `a cookbook names only its first members and counts them all`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        repeat(CookbookInfo.MEMBERS_SHOWN + 2) {
            client.addCookbookUser(cookbook.id, client.createUser().id, false)
        }

        val result = client.getCookbook(cookbook.id)
        assertEquals(CookbookInfo.MEMBERS_SHOWN + 3, result.usersCount, "the creator counts too")
        assertEquals(CookbookInfo.MEMBERS_SHOWN, result.members.size)
    }

    @Test
    fun `get cookbook recipes`() = runTestAsAdmin {
        val cookbook1 = client.createCookbook()
        client.assertCookbookExists(cookbook1.id)

        val cookbook2 = client.createCookbook()
        client.assertCookbookExists(cookbook1.id)

        val recipe1 = client.createRecipe()
        val recipe2 = client.createRecipe()
        val recipe3 = client.createRecipe()
        val recipe4 = client.createRecipe()

        client.addCookbookRecipe(cookbook1.id, recipe1.id)
        client.addCookbookRecipe(cookbook1.id, recipe2.id)

        client.addCookbookRecipe(cookbook2.id, recipe2.id)
        client.addCookbookRecipe(cookbook2.id, recipe3.id)
        client.addCookbookRecipe(cookbook2.id, recipe4.id)


        val result1 = client.listRecipes(cookbook = cookbook1.id)
        assertEquals(2, result1.size)

        val result2 = client.listRecipes(cookbook = cookbook2.id)
        assertEquals(3, result2.size)
    }

    @Test
    fun `list recipe user cookbooks`() = runTestAsAdmin {
        val user = userService.getUserByUsername("admin")!!

        val cookbook1 = client.createCookbook()
        val cookbook2 = client.createCookbook()
        val cookbook3 = client.createCookbook()
        val cookbook4 = client.createCookbook()

        val recipe1 = client.createRecipe()
        val recipe2 = client.createRecipe()
        val recipe3 = client.createRecipe()
        val recipe4 = client.createRecipe()

        client.addCookbookRecipe(cookbook1.id, recipe1.id)
        client.addCookbookRecipe(cookbook2.id, recipe1.id)
        val result1 = client.getRecipeUserCookbooks(user.id, recipe1.id)
        logger.info {result1}
        assertEquals(4, result1.size)
        assertEquals(2, result1.filter { it.hasRecipe }.size)

        client.deleteCookbookRecipe(cookbook2.id, recipe1.id)
        val result2 = client.getRecipeUserCookbooks(user.id, recipe1.id)
        logger.info {result2}
        assertEquals(4, result2.size)
        assertEquals(1, result2.filter { it.hasRecipe }.size)

        client.addCookbookRecipe(cookbook3.id, recipe1.id)
        val result3 = client.getRecipeUserCookbooks(user.id, recipe1.id)
        logger.info {result3}
        assertEquals(4, result3.size)
        assertEquals(2, result3.filter { it.hasRecipe }.size)

        client.addCookbookRecipe(cookbook3.id, recipe2.id)
        val result4 = client.getRecipeUserCookbooks(user.id, recipe2.id)
        assertEquals(4, result4.size)
        assertEquals(1, result4.filter { it.hasRecipe }.size)

        client.deleteCookbookRecipe(cookbook1.id, recipe1.id)
        val result5 = client.getRecipeUserCookbooks(user.id, recipe1.id)
        assertEquals(4, result5.size)
        assertEquals(1, result5.filter { it.hasRecipe }.size)
    }

    @Test
    fun `adding recipes to cookbook already present returns HTTP Bad Request`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        val recipe = client.createRecipe()


        client.addCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.addCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `removing recipes from cookbook that are not present returns HTTP Bad Request`() = runTestAsAdmin {
        val cookbook = client.createCookbook()
        val recipe = client.createRecipe()

        client.addCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.deleteCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.deleteCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `empty cookbooks are deleted`() = runTestAsUser {
        val cookbook = client.createCookbook()
        val recipe = client.createRecipe()

        client.addCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.leaveCookbook(cookbook.id)

        client.getCookbookRaw(cookbook.id).apply {
            assertEquals(HttpStatusCode.NotFound, status)
        }
    }

    @Test
    fun `non empty cookbooks are not deleted`() = runTestAsUser {
        val cookbook = client.createCookbook()
        val recipe = client.createRecipe()

        client.addCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.addCookbookUser(cookbook.id, userService.findByMail(USER2).id, false)

        client.leaveCookbook(cookbook.id)

        client.getCookbookRaw(cookbook.id).apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun `new cookbook admin is assigned if no admin left`() = runTestAsUser {
        val cookbook = client.createCookbook()
        val recipe = client.createRecipe()

        client.addCookbookRecipe(cookbook.id, recipe.id).apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        client.addCookbookUser(cookbook.id, userService.findByMail(USER2).id, false)

        client.leaveCookbook(cookbook.id)

        val users = client.getCookbookUsers(cookbook.id)
        assertEquals(1, users.size)
        assertEquals(true, users.first().isAdmin)

    }

}