package main.com.xavierclavel.other

import com.xavierclavel.ApplicationTest
import main.com.xavierclavel.utils.acceptFollowRequest
import main.com.xavierclavel.utils.chooseLocale
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.createUser
import main.com.xavierclavel.utils.follow
import main.com.xavierclavel.utils.listNotifications
import main.com.xavierclavel.utils.registerDevice
import org.junit.jupiter.api.Test
import shared.dto.RecipeDTO
import shared.dto.UserSettingsDTO
import shared.enums.Locale
import shared.enums.NotificationKind
import shared.enums.NotificationPlaceholder
import shared.utils.NotificationWordings
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The notifications the application emits itself, rather than the ones an operator sends.
 *
 * These are the ones with wording of their own, so what is worth pinning down is that the
 * right people are told, in their own language, and that nobody is ever told about something
 * they did themselves.
 *
 * Every one of them fans out on a background scope — the request that causes one has already
 * returned by the time the push is made — so the assertions wait on the send rather than
 * assuming it has happened. See `FakePushSender.awaitKind`.
 */
class NotificationEventsTest : ApplicationTest() {

    private val author = "author@mail.com"
    private val follower = "follower@mail.com"
    private val password = "password"

    // ------------------------------------------------------------- new recipe

    @Test
    fun `publishing a recipe tells the author's followers`() = runTest {
        var authorId = 0L
        var authorName = ""
        runAsAdmin {
            val created = client.createUser(author)
            authorId = created.id
            authorName = created.username
            client.createUser(follower)
        }

        runAs(follower, password) {
            // The account's language is what the wording is written in, and it disagrees
            // with the handset on purpose: the phone is in English, the person reads French
            client.chooseLocale(Locale.FR)
            client.registerDevice("follower-phone", locale = Locale.EN)
            client.follow(authorId)
        }

        runAs(author, password) {
            client.createRecipe(RecipeDTO(title = "Tarte aux pommes", description = "d"))
        }

        val pushed = fakePushSender.awaitKind(NotificationKind.NEW_RECIPE.key)
        assertEquals(1, pushed.size)

        // Rendered here rather than spelled out, so a reworded notification is not a
        // failing test — what is asserted is the language and the values, not the phrasing
        val (title, body) = NotificationWordings.render(
            NotificationKind.NEW_RECIPE,
            Locale.FR,
            mapOf(
                NotificationPlaceholder.USERNAME to authorName,
                NotificationPlaceholder.TITLE to "Tarte aux pommes",
            ),
        )
        assertEquals(title, pushed.single().title)
        assertEquals(body, pushed.single().body)
        assertTrue(pushed.single().data["link"]!!.startsWith("/recipe/view?id="))

        runAs(follower, password) {
            val listed = client.listNotifications().first { it.kind == NotificationKind.NEW_RECIPE }
            assertEquals(authorName, listed.actor?.username, "the author is who caused it")
        }
    }

    @Test
    fun `publishing a recipe tells nobody when there are no followers`() = runTest {
        runAsAdmin { client.createUser(author) }

        runAs(author, password) {
            client.registerDevice("author-phone")
            client.createRecipe(RecipeDTO(title = "Lonely loaf", description = "d"))
        }

        // Waits the full timeout on purpose: the claim is that nothing arrives, and a bare
        // assertion would pass simply by running before the fan-out could have
        assertEquals(emptyList(), fakePushSender.awaitKind(NotificationKind.NEW_RECIPE.key, timeoutMillis = 1_500))
    }

    /** A pending request is somebody the author has not let in yet. */
    @Test
    fun `a follower whose request is still pending is not told about new recipes`() = runTest {
        var authorId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            client.createUser(follower)
            // Private, and not auto-accepting: this is what holds a request pending
            userService.updateSettings(authorId, UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = false))
        }

        runAs(follower, password) {
            client.registerDevice("pending-phone")
            client.follow(authorId)
        }

        runAs(author, password) {
            client.createRecipe(RecipeDTO(title = "Not for you", description = "d"))
        }

        assertEquals(emptyList(), fakePushSender.awaitKind(NotificationKind.NEW_RECIPE.key, timeoutMillis = 1_500))
    }

    // ----------------------------------------------------------------- follows

    @Test
    fun `following someone who accepts outright tells them they have a follower`() = runTest {
        var authorId = 0L
        var followerName = ""
        runAsAdmin {
            authorId = client.createUser(author).id
            followerName = client.createUser(follower).username
        }

        runAs(author, password) { client.registerDevice("author-phone") }
        runAs(follower, password) { client.follow(authorId) }

        val pushed = fakePushSender.awaitKind(NotificationKind.NEW_FOLLOWER.key)
        assertEquals(1, pushed.size)
        assertTrue(pushed.single().body.contains(followerName))
        assertTrue(pushed.single().data["link"]!!.startsWith("/user/view?user="))
    }

    @Test
    fun `asking to follow a private account tells them it is a request`() = runTest {
        var authorId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            client.createUser(follower)
            userService.updateSettings(authorId, UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = false))
        }

        runAs(author, password) { client.registerDevice("private-phone") }
        runAs(follower, password) { client.follow(authorId) }

        // A request, not a follower: which one it is is the thing the recipient needs told
        assertEquals(1, fakePushSender.awaitKind(NotificationKind.FOLLOW_REQUEST.key).size)
        assertEquals(emptyList(), fakePushSender.sentOfKind(NotificationKind.NEW_FOLLOWER.key))
    }

    @Test
    fun `accepting a request tells whoever sent it`() = runTest {
        var authorId = 0L
        var followerId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            followerId = client.createUser(follower).id
            userService.updateSettings(authorId, UserSettingsDTO(autoAcceptFollowRequests = false, isAccountPublic = false))
        }

        runAs(follower, password) {
            client.registerDevice("follower-phone")
            client.follow(authorId)
        }

        runAs(author, password) { client.acceptFollowRequest(followerId) }

        assertEquals(1, fakePushSender.awaitKind(NotificationKind.FOLLOW_ACCEPTED.key).size)
    }

    // --------------------------------------------------------------- the actor

    @Test
    fun `nobody is notified of their own doing`() = runTest {
        var authorId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            client.createUser(follower)
        }

        // Somebody follows the author, then the author publishes: the follower is told, and
        // the author — who caused it — is not, even though they have a device of their own
        runAs(follower, password) { client.follow(authorId) }

        runAs(author, password) {
            client.registerDevice("author-phone")
            client.createRecipe(RecipeDTO(title = "Own recipe", description = "d"))
            // Give the fan-out the same window the positive cases get
            fakePushSender.awaitKind(NotificationKind.NEW_RECIPE.key, timeoutMillis = 1_500)

            assertTrue(
                fakePushSender.sent.none { it.token == "author-phone" },
                "the author is not told about their own recipe",
            )
            assertNull(
                client.listNotifications().firstOrNull { it.kind == NotificationKind.NEW_RECIPE },
                "and it is not in their own list either",
            )
        }
    }
}
