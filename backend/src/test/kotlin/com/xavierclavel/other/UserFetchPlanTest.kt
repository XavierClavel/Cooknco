package main.com.xavierclavel.other

import com.xavierclavel.services.AdminService
import com.xavierclavel.services.FollowService
import io.ebean.Paging
import main.com.xavierclavel.utils.FetchPlanTest
import main.com.xavierclavel.utils.createUser
import org.junit.jupiter.api.Test
import org.koin.test.inject

/**
 * Guards the account read paths against N+1.
 *
 * A listed account states five numbers — its recipes, likes, cookbooks, followers and follows — and
 * every one of them is a collection. That is five round trips per row if none of them is fetched up
 * front, on a table the backoffice pages twenty at a time.
 */
class UserFetchPlanTest : FetchPlanTest() {
    private val adminService: AdminService by inject()
    private val followService: FollowService by inject()

    private val paging = Paging.of(0, 20)

    @Test
    fun `searching accounts costs the same whether there are few or many`() = runTest {
        assertQueryCountDoesNotGrow(
            what = "UserService.search",
            read = { userService.search(null, paging).second },
            grow = { repeat(4) { client.createUser(mail = "searched-$it@mail.com") } },
        )
    }

    @Test
    fun `the admin users table costs the same whether there are few accounts or many`() = runTest {
        assertQueryCountDoesNotGrow(
            what = "AdminService.searchUsers",
            read = { adminService.searchUsers(null, null, null, null, paging).second },
            grow = { repeat(4) { client.createUser(mail = "listed-$it@mail.com") } },
        )
    }

    /**
     * The two sides of a profile's follow lists. Each row names the account on the other end, which
     * is a `-to-one` and therefore batch-loaded — so what this guards is the day one of them starts
     * reading something else off that account, such as the counts a `UserOverview` does not carry
     * today.
     */
    @Test
    fun `listing followers costs the same whether there is one or several`() = runTest {
        val target = userService.findByMail(USER1)!!.id
        follow(target, from = client.createUser(mail = "follower-first@mail.com").id)
        assertQueryCountDoesNotGrow(
            what = "FollowService.getFollowers",
            read = { followService.getFollowers(target, paging) },
            grow = {
                repeat(2) { index ->
                    follow(target, from = client.createUser(mail = "follower-$index@mail.com").id)
                }
            },
        )
    }

    @Test
    fun `listing who an account follows costs the same whether it is one or several`() = runTest {
        val follower = userService.findByMail(USER1)!!.id
        follow(client.createUser(mail = "followed-first@mail.com").id, from = follower)
        assertQueryCountDoesNotGrow(
            what = "FollowService.getFollows",
            read = { followService.getFollows(follower, paging) },
            grow = {
                repeat(2) { index ->
                    follow(client.createUser(mail = "followed-$index@mail.com").id, from = follower)
                }
            },
        )
    }

    /**
     * The notification bell. Already clean — `NotificationService.list` fetches the actor — and it
     * ships as a regression guard on the one endpoint a signed-in client polls.
     */
    @Test
    fun `listing notifications costs the same whether there is one or several`() = runTest {
        val me = userService.findByMail(USER1)!!.id
        follow(me, from = client.createUser(mail = "actor-first@mail.com").id)
        assertQueryCountDoesNotGrow(
            what = "NotificationService.list",
            read = { notificationService.list(me, paging) },
            grow = {
                repeat(2) { index ->
                    follow(me, from = client.createUser(mail = "actor-$index@mail.com").id)
                }
            },
        )
    }

    /** A follow, and the notification it fans out, both settled before anything is measured. */
    private suspend fun follow(userId: Long, from: Long) {
        followService.createFollow(userId, from)
        notificationService.awaitDispatches()
    }
}
