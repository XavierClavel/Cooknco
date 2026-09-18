package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import main.com.xavierclavel.utils.assertRecipeDoesNotExist
import main.com.xavierclavel.utils.assertRecipeExists
import main.com.xavierclavel.utils.assertUserDoesNotExist
import main.com.xavierclavel.utils.assertUserExists
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createLike
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.deleteMyAccount
import main.com.xavierclavel.utils.follow
import main.com.xavierclavel.utils.getMe
import main.com.xavierclavel.utils.listUsers
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class UserControllerTest : ApplicationTest() {
    @Test
    fun `create user`() = runTestAsAdmin {
        val mail = "test_user@mail.fr"
        val user = client.createUser(mail = mail)
        client.assertUserExists(user.id)
    }

    /*
    @Test
    fun `delete user`() = runTestAsAdmin {
        val username = "test_user"
        val user = it.createUser(username = username)
        it.assertUserExists(user.id)
        it.deleteUser(user.id)
    }

     */

    /**
     * An account deletes itself, and takes what it owns with it.
     *
     * Deliberately an account that has been *used*, because an empty one proves nothing
     * here: every table holding a user's things points at `users` with an `on delete
     * restrict` foreign key, and a recipe is soft-deleted rather than removed — so an
     * account with a recipe, a like, a follow and a cookbook membership is where a delete
     * fails, and the empty one is the case that always worked.
     *
     * This is the promise cooknco.eu/account-deletion makes, and the URL the Play Console
     * carries; the app's settings screen and the website's both call exactly this endpoint.
     * What breaks if this goes red is the whole feature, not a corner of it.
     */
    @Test
    fun `an account that has been used deletes itself, and its recipes with it`() = runTest {
        var otherUserId = 0L
        var otherRecipeId = 0L
        runAsUser2 {
            otherUserId = client.getMe().id
            otherRecipeId = client.createRecipe().id
        }

        var userId = 0L
        var ownRecipeId = 0L
        runAsUser1 {
            userId = client.getMe().id
            ownRecipeId = client.createRecipe().id
            client.createLike(otherRecipeId)
            client.follow(otherUserId)
            client.createCookbook()
            client.deleteMyAccount()
        }

        // Read from somebody else's session: the one that did the deleting has no account
        // behind it any more, and what matters is what the rest of the product still sees
        runAsUser2 {
            client.assertUserDoesNotExist(userId)
            client.assertRecipeDoesNotExist(ownRecipeId)
            // Liking somebody's recipe is not a claim on it: it outlives the account that
            // liked it, minus the like
            client.assertRecipeExists(otherRecipeId)
        }
    }

    @Test
    fun `list users`() = runTestAsAdmin {
        val emails = setOf("test_user1@mail.com", "test_user2@mail.com")
        val users = emails.map { mail -> client.createUser(mail = mail) }
        val response = client.listUsers().items.map { it.id }.toSet()
        for (user in users) {
            assertTrue { user.id in response }
        }
    }
}