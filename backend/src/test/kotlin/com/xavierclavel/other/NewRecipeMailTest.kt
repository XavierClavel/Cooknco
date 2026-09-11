package main.com.xavierclavel.other

import com.xavierclavel.ApplicationTest
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.createUser
import io.ebean.test.LoggedSql
import main.com.xavierclavel.utils.follow
import main.com.xavierclavel.utils.registerDevice
import main.com.xavierclavel.utils.signup
import org.junit.jupiter.api.Test
import shared.dto.RecipeDTO
import shared.dto.UserSettingsDTO
import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.enums.MailPlaceholder
import shared.events.UserMailRequestedEvent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Who gets mailed when a recipe is published.
 *
 * The audience is worked out here, in the service that owns the follow graph, and reaches
 * mail-service as finished addresses on the event — which is what lets that service keep no
 * copy of `users` and `followers`. So what these pin down is the filtering: mails are
 * opt-out by default, and every case expecting none opts its follower in first, or it would
 * pass on the default alone and prove nothing about the rule it names.
 *
 * Fan-out runs on a background scope, so each assertion waits on it rather than assuming it
 * has happened.
 */
class NewRecipeMailTest : ApplicationTest() {

    private val author = "author@mail.com"
    private val follower = "follower@mail.com"
    private val password = "password"

    /** The settings a follower who wants these mails has. */
    private val subscribed = UserSettingsDTO(
        autoAcceptFollowRequests = true,
        isAccountPublic = true,
        mailNotificationsEnabled = true,
    )

    @Test
    fun `a follower who opted in is mailed about a new recipe`() = runTest {
        var authorId = 0L
        var authorName = ""
        var followerId = 0L
        runAsAdmin {
            val created = client.createUser(author)
            authorId = created.id
            authorName = created.username
            followerId = client.createUser(follower).id
        }
        userService.updateSettings(followerId, subscribed)

        runAs(follower, password) { client.follow(authorId) }

        mockEventProducer.clear()
        var recipeId = 0L
        runAs(author, password) {
            recipeId = client.createRecipe(RecipeDTO(title = "Tarte aux pommes", description = "d")).id
        }
        notificationService.awaitDispatches()

        val mail = newRecipeMails().single()
        assertEquals(followerId, mail.recipientId)
        assertEquals(follower, encryptionService.decrypt(mail.encryptedRecipient))
        assertEquals(authorName, mail.values[MailPlaceholder.USERNAME])
        assertEquals("Tarte aux pommes", mail.values[MailPlaceholder.TITLE])
        assertTrue { mail.values[MailPlaceholder.LINK]!!.endsWith("/recipe/view?id=$recipeId") }
    }

    @Test
    fun `a follower who never opted in is not mailed`() = runTest {
        var authorId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            client.createUser(follower)
        }

        runAs(follower, password) { client.follow(authorId) }

        mockEventProducer.clear()
        runAs(author, password) { client.createRecipe() }
        notificationService.awaitDispatches()

        assertEquals(emptyList(), newRecipeMails())
    }

    @Test
    fun `a follower whose request is still pending is not mailed`() = runTest {
        var authorId = 0L
        var followerId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            followerId = client.createUser(follower).id
        }
        // A private account with no auto-accept holds the follow pending, and a pending
        // follower has not been let in yet — mailing them the recipe would let them in
        userService.updateSettings(authorId, UserSettingsDTO(
            autoAcceptFollowRequests = false,
            isAccountPublic = false,
        ))
        userService.updateSettings(followerId, subscribed)

        runAs(follower, password) { client.follow(authorId) }

        mockEventProducer.clear()
        runAs(author, password) { client.createRecipe() }
        notificationService.awaitDispatches()

        assertEquals(emptyList(), newRecipeMails())
    }

    @Test
    fun `the author is not mailed about their own recipe`() = runTest {
        var authorId = 0L
        runAsAdmin { authorId = client.createUser(author).id }
        userService.updateSettings(authorId, subscribed)

        mockEventProducer.clear()
        runAs(author, password) { client.createRecipe() }
        notificationService.awaitDispatches()

        assertEquals(emptyList(), newRecipeMails())
    }

    @Test
    fun `an account mail is sent whatever the notification setting says`() = runTest {
        // Verification and password resets answer something the reader just did, so the
        // opt-out must not reach them: it would lock people out of their own accounts
        var userId = 0L
        runAsAdmin { userId = client.createUser(follower).id }
        userService.updateSettings(userId, UserSettingsDTO(
            autoAcceptFollowRequests = true,
            isAccountPublic = true,
            mailNotificationsEnabled = false,
        ))

        mockEventProducer.clear()
        userService.requestPasswordReset(follower)

        val mail = mailsOfKind(EmailTemplateKind.PASSWORD_RESET).single()
        assertEquals(userId, mail.recipientId)
        assertEquals(follower, encryptionService.decrypt(mail.encryptedRecipient))
    }

    @Test
    fun `fanning out does not cost a query per follower`() = runTest {
        // Asserted as "the same for four followers as for one" rather than as a fixed
        // number, because the number is not the point and would only pin down today's
        // query plan. What matters is that it does not grow: a per-recipient lookup is
        // invisible at two followers and fatal at a thousand.

        /** Publishes one recipe and counts the queries against `devices` it took. */
        suspend fun deviceQueriesFanningOutTo(followerCount: Int, tag: String): Int {
            var authorId = 0L
            val authorMail = "author-$tag@mail.com"
            runAsAdmin { authorId = client.createUser(authorMail).id }

            repeat(followerCount) { index ->
                val followerMail = "follower-$tag-$index@mail.com"
                var followerId = 0L
                // No language of their own, so resolving one actually has to consult their
                // device — which is the lookup this test exists to keep off the per-recipient
                // path. An audience that has all reported touches `devices` not at all.
                runAsAdmin { followerId = client.createUser(followerMail, locale = null).id }
                userService.updateSettings(followerId, subscribed)
                runAs(followerMail, password) {
                    client.registerDevice("$tag-$index-phone", locale = Locale.FR)
                    client.follow(authorId)
                }
            }

            // Following raises notifications of its own; let them finish so that only the
            // publish below is measured
            notificationService.awaitDispatches()
            mockEventProducer.clear()

            LoggedSql.start()
            runAs(authorMail, password) { client.createRecipe() }
            notificationService.awaitDispatches()
            val executed = LoggedSql.stop()

            assertEquals(followerCount, newRecipeMails().size, "every follower should be mailed")
            return executed.count { it.contains(" from devices") }
        }

        val one = deviceQueriesFanningOutTo(1, "one")
        val four = deviceQueriesFanningOutTo(4, "four")
        assertEquals(one, four, "queries against devices grew with the number of followers")
    }

    @Test
    fun `a follower with no language of their own is written to in their device's`() = runTest {
        var authorId = 0L
        var frenchId = 0L
        var englishId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            frenchId = client.createUser("french@mail.com", locale = null).id
            englishId = client.createUser("english@mail.com", locale = null).id
        }
        userService.updateSettings(frenchId, subscribed)
        userService.updateSettings(englishId, subscribed)

        runAs("french@mail.com", password) {
            client.registerDevice("french-phone", locale = Locale.FR)
            client.follow(authorId)
        }
        runAs("english@mail.com", password) {
            client.registerDevice("english-phone", locale = Locale.EN)
            client.follow(authorId)
        }

        mockEventProducer.clear()
        runAs(author, password) { client.createRecipe() }
        notificationService.awaitDispatches()

        // Resolving the whole audience at once must still give each their own language
        val byRecipient = newRecipeMails().associate { it.recipientId to it.locale }
        assertEquals(mapOf(frenchId to Locale.FR, englishId to Locale.EN), byRecipient)
    }

    @Test
    fun `a mail is addressed in the language the account signed up in`() = runTest {
        // A web-only account registers no device, so the locale it signed up with is the
        // only thing saying how to write to it. Without it every such mail would be French.
        mockEventProducer.clear()
        client.signup(mail = "english@mail.com", password = password)

        val mail = mailsOfKind(EmailTemplateKind.ACCOUNT_VERIFICATION).single()
        assertEquals(Locale.EN, mail.locale)
    }

    @Test
    fun `the account's own language wins over the device it last used`() = runTest {
        // A handset in another language is not a decision the user made; what they chose,
        // or what the first client to say anything reported, is. Mail has to resolve that
        // the same way a push does, or one recipe would arrive in two languages.
        var authorId = 0L
        var followerId = 0L
        runAsAdmin {
            authorId = client.createUser(author).id
            followerId = client.createUser(follower, locale = Locale.FR).id
        }
        userService.updateSettings(followerId, subscribed)
        runAs(follower, password) {
            client.registerDevice("borrowed-english-phone", locale = Locale.EN)
            client.follow(authorId)
        }

        mockEventProducer.clear()
        runAs(author, password) { client.createRecipe() }
        notificationService.awaitDispatches()

        assertEquals(Locale.FR, newRecipeMails().single().locale)
    }

    private fun newRecipeMails() = mailsOfKind(EmailTemplateKind.NEW_RECIPE)

    private fun mailsOfKind(kind: EmailTemplateKind) =
        mockEventProducer.eventsProduced
            .filterIsInstance<UserMailRequestedEvent>()
            .filter { it.templateKey == kind.key }
}
