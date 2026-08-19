package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.utils.logger
import shared.infodto.UserInfo
import shared.overviewdto.UserOverview
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.follow
import main.com.xavierclavel.utils.getFollowers
import main.com.xavierclavel.utils.getFollows
import main.com.xavierclavel.utils.getUser
import main.com.xavierclavel.utils.unfollow
import org.junit.jupiter.api.Test
import shared.dto.UserSettingsDTO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowControllerTest : ApplicationTest() {
    @Test
    fun `follow user`() = runTest {
        var user: UserInfo? = null
        val usersToFollow = mutableListOf<UserInfo>()
        val mail = "xyz@mail.com"
        runAsAdmin {
            user = client.createUser(mail)
            repeat(2) {
                usersToFollow.add(client.createUser())
            }
            val follows1 = client.getFollows(user.id)
            logger.info {"done"}
            assertEquals(setOf<UserOverview>(), follows1.map {it.user}.toSet())
        }
        user!!

        runAs(mail, "password") {
            client.follow(usersToFollow[0].id)
            val follows1 = client.getFollows(user.id)
            assertEquals(setOf(usersToFollow[0].toOverview()), follows1.map {it.user}.toSet())

            client.follow(usersToFollow[1].id)
            val follows2 = client.getFollows(user.id)
            assertEquals(usersToFollow.map { it.toOverview() }.toSet(), follows2.map {it.user}.toSet())
        }
    }

    @Test
    fun `unfollow user`() = runTest {
        var user: UserInfo? = null
        val usersToFollow = mutableListOf<UserInfo>()
        val mail = "my.mail@mail.com"
        runAsAdmin {
            user = client.createUser(mail)
            repeat(2) {
                usersToFollow.add(client.createUser())
            }
            val follows1 = client.getFollows(user.id)
            logger.info {"done"}
            assertEquals(setOf<UserOverview>(), follows1.map {it.user}.toSet())
        }
        user!!


        runAs(mail, "password") {
            client.follow(usersToFollow[0].id)
            val follows1 = client.getFollows(user.id)
            assertEquals(setOf(usersToFollow[0].toOverview()), follows1.map {it.user}.toSet())

            client.follow(usersToFollow[1].id)
            val follows2 = client.getFollows(user.id)
            assertEquals(usersToFollow.map { it.toOverview() }.toSet(), follows2.map {it.user}.toSet())

            client.unfollow(usersToFollow[0].id)
            val follows3 = client.getFollows(user.id)
            assertEquals(setOf(usersToFollow[1].toOverview()), follows3.map {it.user}.toSet())
        }
    }

    @Test
    fun `get followers`() = runTest {
        var user: UserInfo? = null
        var follower1: UserInfo? = null
        var follower2: UserInfo? = null
        val mail = "abc@mail.com"
        runAsAdmin {
            user = client.createUser(mail)
            follower1 = userService.findByMail(USER1).toInfo()
            follower2 = userService.findByMail(USER2).toInfo()

            val follows1 = client.getFollows(user.id)
            assertEquals(setOf<UserOverview>(), follows1.map {it.user}.toSet())
        }
        user!!
        follower1!!
        follower2!!


        runAsUser1 {
            client.follow(user.id)
            val followers = client.getFollowers(user.id)
            assertEquals(setOf(follower1.toOverview()), followers.map {it.user}.toSet())
        }

        runAsUser2 {
            client.follow(user.id)
            val followers = client.getFollowers(user.id)
            assertEquals(setOf(follower1.toOverview(), follower2.toOverview()), followers.map {it.user}.toSet())
        }

        runAsUser1 {
            client.unfollow(user.id)
            val followers = client.getFollowers(user.id)
            assertEquals(setOf(follower2.toOverview()), followers.map {it.user}.toSet())
        }

    }

    @Test
    fun `count followers`() = runTest {
        var user: Long = setupTestUser("user3")
        runAsAdmin {
            val result = client.getUser(user)
            assertEquals(0, result.followersCount)
        }
        user

        runAsUser1 {
            client.follow(user)
            val userInfo = client.getUser(user)
            logger.info { userInfo }
            assertEquals(1, userInfo.followersCount)
        }

        runAsUser2 {
            client.follow(user)
            val userInfo = client.getUser(user)
            assertEquals(2, userInfo.followersCount)
        }

        runAsUser1 {
            client.unfollow(user)
            val userInfo = client.getUser(user)
            assertEquals(1, userInfo.followersCount)
        }
    }

    @Test
    fun `following a public account is accepted immediately`() = runTest {
        val user = setupTestUser("user3", UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = true))
        runAsUser1 {
            assertFalse(client.follow(user).pending)
            assertEquals(1, client.getUser(user).followersCount)
        }
    }

    @Test
    fun `following a private account stays pending`() = runTest {
        val user = setupTestUser("user3", UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = false))
        runAsUser1 {
            assertTrue(client.follow(user).pending)
            assertEquals(0, client.getUser(user).followersCount)
        }
    }

    @Test
    fun `switching an account to public accepts its pending follow requests`() = runTest {
        val user = setupTestUser("user3", UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = false))
        runAsUser1 {
            assertTrue(client.follow(user).pending)
        }
        userService.updateSettings(user, UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = true))
        runAsUser1 {
            assertEquals(1, client.getUser(user).followersCount)
            assertEquals(listOf(false), client.getFollowers(user).map { it.pending })
        }
    }
}
