package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import shared.dto.IngredientDTO
import shared.dto.RecipeDTO
import shared.dto.RecipeDTO.RecipeIngredientDTO
import shared.enums.DishClass
import shared.enums.Sort
import shared.infodto.RecipeInfo
import shared.infodto.UserInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import main.com.xavierclavel.utils.addCookbookRecipe
import main.com.xavierclavel.utils.addCookbookUser
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createIngredient
import main.com.xavierclavel.utils.createLike
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.follow
import main.com.xavierclavel.utils.getMe
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.listRecipes
import main.com.xavierclavel.utils.unfollow

class RecipeFiltersTest : ApplicationTest() {

    @Test
    fun `filter recipes by liked`() = runTest {
        var user: UserInfo? = null
        var adminUser: UserInfo? = null
        var recipe: RecipeInfo? = null
        runAsAdmin {
            user = userService.findByMail(USER1).toInfo()
            adminUser = client.getMe()
        }
        runAs(USER1) {
            recipe = client.createRecipe()
        }
        runAsAdmin {
            val response1 = client.listRecipes(user = user!!.id)
            assertEquals(1, response1.size)

            val response2 = client.listRecipes(likedBy = adminUser!!.id)
            assertEquals(0, response2.size)

            client.createLike(recipe!!.id)
            val response3 = client.listRecipes(likedBy = adminUser.id)
            assertEquals(1, response3.size)
        }
    }

    @Test
    fun `filter recipes by  self liked`() = runTest {
        var adminUser: UserInfo? = null
        var recipe: RecipeInfo? = null
        runAsAdmin {
            adminUser = client.getMe()
            recipe = client.createRecipe()

            val response1 = client.listRecipes(user = adminUser.id)
            assertEquals(1, response1.size)

            val response2 = client.listRecipes(likedBy = adminUser.id)
            assertEquals(0, response2.size)

            client.createLike(recipe.id)
            val response3 = client.listRecipes(likedBy = adminUser.id)
            assertEquals(1, response3.size)
        }
    }

    @Test
    fun `filter recipes by liked and owned`() = runTest {
        var adminUser: UserInfo? = null
        var recipeOwned: RecipeInfo? = null
        var recipeLiked: RecipeInfo? = null
        runAsAdmin {
            adminUser = client.getMe()
            recipeOwned = client.createRecipe()
        }
        runAsUser1 {
            recipeLiked = client.createRecipe()
        }
        runAsAdmin {
            client.createLike(recipeLiked!!.id)

            val response1 = client.listRecipes(user = adminUser!!.id)
            assertEquals(1, response1.size)

            val response2 = client.listRecipes(likedBy = adminUser.id)
            assertEquals(1, response2.size)

            val response3 = client.listRecipes(user = adminUser.id, likedBy = adminUser.id)
            assertEquals(2, response3.size)
        }
    }

    @Test
    fun `filter recipes by user cookbooks`() = runTest {
        var user: UserInfo? = null
        var adminUser: UserInfo? = null
        var recipe1: RecipeInfo? = null
        var recipe2: RecipeInfo? = null
        var recipe3: RecipeInfo? = null
        runAsAdmin {
            user = userService.findByMail(USER1).toInfo()
            adminUser = client.getMe()
            recipe1 = client.createRecipe()
            recipe2 = client.createRecipe()
            recipe3 = client.createRecipe()

            val cookbook = client.createCookbook()
            client.addCookbookUser(cookbook.id, user.id, false)
            client.addCookbookRecipe(cookbook.id, recipe1.id)
            client.addCookbookRecipe(cookbook.id, recipe2.id)
        }

        runAs(user!!.username) {
            val response1 = client.listRecipes(cookbookUser = user.id)
            assertEquals(2, response1.size)
            assertEquals(setOf(recipe1!!.toOverview(), recipe2!!.toOverview()), response1.toSet())
        }

    }

    @Test
    fun `filter recipes by dish class`() = runTestAsAdmin {
        var adminUser = client.getMe()
        val recipeDTO1 = RecipeDTO(title = "", dishClass = DishClass.ENTREE)
        val recipeDTO2 = RecipeDTO(title = "", dishClass = DishClass.MAIN_DISH)
        val recipeDTO3 = RecipeDTO(title = "", dishClass = DishClass.DESERT)

        val recipe1 = client.createRecipe(recipeDTO1)
        val recipe2 = client.createRecipe(recipeDTO2)
        val recipe3 = client.createRecipe(recipeDTO3)

        val response1 = client.listRecipes(user = adminUser.id, dishClasses = setOf(DishClass.ENTREE))
        assertEquals(setOf(recipe1.toOverview()), response1.toSet())

        val response2 = client.listRecipes(user = adminUser.id, dishClasses = setOf(DishClass.MAIN_DISH))
        assertEquals(setOf(recipe2.toOverview()), response2.toSet())

        val response3 = client.listRecipes(user = adminUser.id, dishClasses = setOf(DishClass.DESERT))
        assertEquals(setOf(recipe3.toOverview()), response3.toSet())

        val response4 = client.listRecipes(user = adminUser.id, dishClasses = setOf(DishClass.ENTREE, DishClass.MAIN_DISH))
        assertEquals(setOf(recipe1.toOverview(), recipe2.toOverview()), response4.toSet())

        val response5 = client.listRecipes(user = adminUser.id, dishClasses = setOf(DishClass.ENTREE, DishClass.MAIN_DISH, DishClass.DESERT))
        assertEquals(setOf(recipe1.toOverview(), recipe2.toOverview(), recipe3.toOverview()), response5.toSet())

    }

    @Test
    fun `filter recipes by likes and dish class`() = runTestAsAdmin {
        var adminUser = client.getMe()
        val recipeDTO1 = RecipeDTO(title = "", dishClass = DishClass.MAIN_DISH)
        val recipeDTO2 = RecipeDTO(title = "", dishClass = DishClass.MAIN_DISH)
        val recipeDTO3 = RecipeDTO(title = "", dishClass = DishClass.DESERT)

        val recipe1 = client.createRecipe(recipeDTO1)
        val recipe2 = client.createRecipe(recipeDTO2)
        val recipe3 = client.createRecipe(recipeDTO3)

        client.createLike(recipe1.id)

        val recipe1Updated = client.getRecipe(recipe1.id)


        val response1 = client.listRecipes(user = adminUser.id, dishClasses = setOf(DishClass.MAIN_DISH))
        assertEquals(setOf(recipe1Updated.toOverview(), recipe2.toOverview()), response1.toSet())

        val response2 = client.listRecipes(likedBy = adminUser.id, dishClasses = setOf(DishClass.MAIN_DISH))
        assertEquals(setOf(recipe1Updated.toOverview()), response2.toSet())

    }

    @Test
    fun `filter recipes by ingredient`() = runTestAsAdmin {
        val adminUser = client.getMe()

        val ingredientDTO1 = IngredientDTO()
        val ingredientDTO2 = IngredientDTO()

        val ingredient1 = client.createIngredient(ingredientDTO1)
        val ingredient2 = client.createIngredient(ingredientDTO2)

        val recipeIngredient1 = RecipeIngredientDTO(ingredient1.id)
        val recipeIngredient2 = RecipeIngredientDTO(ingredient2.id)

        val recipeDTO1 = RecipeDTO(title = "", ingredients = mutableListOf(recipeIngredient1))
        val recipeDTO2 = RecipeDTO(title = "", ingredients = mutableListOf(recipeIngredient2))
        val recipeDTO3 = RecipeDTO(title = "", ingredients = mutableListOf(recipeIngredient1, recipeIngredient2))

        val recipe1 = client.createRecipe(recipeDTO1)
        val recipe2 = client.createRecipe(recipeDTO2)
        val recipe3 = client.createRecipe(recipeDTO3)

        val response1 = client.listRecipes(user = adminUser.id, ingredient = setOf(ingredient1.id))
        assertEquals(setOf(recipe1.toOverview(), recipe3.toOverview()), response1.toSet())

        val response2 = client.listRecipes(user = adminUser.id, ingredient = setOf(ingredient2.id))
        assertEquals(setOf(recipe2.toOverview(), recipe3.toOverview()), response2.toSet())

        val response3 = client.listRecipes(user = adminUser.id, ingredient = setOf(ingredient1.id, ingredient2.id))
        assertEquals(setOf(recipe3.toOverview()), response3.toSet())
    }

    @Test
    fun `filter recipes by title`() = runTestAsAdmin {
        val adminUser = client.getMe()
        val recipeDTO1 = RecipeDTO(title = "Tarte aux pommes")
        val recipeDTO2 = RecipeDTO(title = "Crumble aux pommes")
        val recipeDTO3 = RecipeDTO(title = "Raclette")

        val recipe1 = client.createRecipe(recipeDTO1)
        val recipe2 = client.createRecipe(recipeDTO2)
        val recipe3 = client.createRecipe(recipeDTO3)

        // full-length query with a typo
        val response1 = client.listRecipes(user = adminUser.id, search = "torte aux pommes")
        assertEquals(setOf(recipe1.toOverview(), recipe2.toOverview()), response1.toSet())

        // single word of a longer title
        val response2 = client.listRecipes(user = adminUser.id, search = "pommes")
        assertEquals(setOf(recipe1.toOverview(), recipe2.toOverview()), response2.toSet())

        // prefix typed while searching
        val response3 = client.listRecipes(user = adminUser.id, search = "tart")
        assertEquals(setOf(recipe1.toOverview()), response3.toSet())

        // no false positives
        val response4 = client.listRecipes(user = adminUser.id, search = "raclette")
        assertEquals(setOf(recipe3.toOverview()), response4.toSet())

        // best match ordering: exact title first, partial match second
        val response5 = client.listRecipes(user = adminUser.id, search = "tarte aux pommes", sort = Sort.BEST_MATCH)
        assertEquals(listOf(recipe1.toOverview(), recipe2.toOverview()), response5)
    }

    @Test
    fun `filter recipes by users followed`() = runTest {
        var user1: UserInfo? = null
        var user2: UserInfo? = null
        var adminUser: UserInfo? = null
        var recipe0: RecipeInfo? = null
        var recipe11: RecipeInfo? = null
        var recipe12: RecipeInfo? = null
        var recipe21: RecipeInfo? = null
        runAsAdmin {
            user1 = userService.findByMail(USER1).toInfo()
            user2 = userService.findByMail(USER2).toInfo()
            adminUser = client.getMe()
            recipe0 = client.createRecipe()
        }
        adminUser!!
        runAsUser1 {
            recipe11 = client.createRecipe()
            recipe12 = client.createRecipe()
        }
        runAsUser2 {
            recipe21 = client.createRecipe()
        }
        recipe0!!
        recipe11!!
        recipe12!!
        recipe21!!
        runAsAdmin {
            client.follow(user1!!.id)
            val response1 = client.listRecipes(followedBy = adminUser.id)
            assertEquals(setOf(recipe11.toOverview(), recipe12.toOverview()), response1.toSet())

            client.follow(user2!!.id)
            val response2 = client.listRecipes(followedBy = adminUser.id)
            assertEquals(setOf(recipe11.toOverview(), recipe12.toOverview(), recipe21.toOverview()), response2.toSet())

            client.unfollow(user1!!.id)
            val response3 = client.listRecipes(followedBy = adminUser.id)
            assertEquals(setOf(recipe21.toOverview()), response3.toSet())
        }
    }
}