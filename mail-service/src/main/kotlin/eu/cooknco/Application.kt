package eu.cooknco

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import eu.cooknco.models.Follower
import eu.cooknco.models.User
import eu.cooknco.models.query.QFollower
import eu.cooknco.models.query.QUser
import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.enums.MailPlaceholder
import shared.events.AccountVerificationRequestedEvent
import shared.events.FollowedUserEvent
import shared.events.KafkaEventConsumer
import shared.events.NewRecipeEvent
import shared.events.NotificationsToggledEvent
import shared.events.PasswordResetRequestedEvent
import shared.events.TestMailRequestedEvent
import shared.events.UnfollowedUserEvent
import shared.events.UserCreatedEvent
import shared.utils.EmailTemplates
import shared.utils.logger
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

fun main() {
    DatabaseManager.init()
    MailTemplateStore.init()

    logger.info { "Server started." }
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
    logger.info { "Server closed."}
}

private val aesKey = SecretKeySpec(System.getenv("AES_KEY").toByteArray(), "AES")
private val frontendUrl = System.getenv("FRONTEND_URL")

fun Application.module() {

    val kafkaConsumer = KafkaEventConsumer("mail-service", listOf("cooknco-users", "cooknco-auth", "cooknco-mails")) { event ->
        logger.info { "Received event: $event" }
        when (event) {
            is FollowedUserEvent -> handleFollow(event)
            is UnfollowedUserEvent -> handleUnfollow(event)
            is NotificationsToggledEvent -> handleNotifications(event)
            is UserCreatedEvent -> handleUserCreation(event)
            is PasswordResetRequestedEvent -> handlePasswordResetRequest(event)
            is AccountVerificationRequestedEvent -> handleAccountVerificationRequested(event)
            is TestMailRequestedEvent -> handleTestMailRequested(event)
            else -> {}
        }
    }

    launch {
        kafkaConsumer.start()
    }

    routing {
        get("/health") { call.respondText("OK") }
    }
}

private fun handleUserCreation(event: UserCreatedEvent) {
    User(
        id = event.id,
        username = event.username,
        encryptedMail = event.mail,
    ).save()
}

private fun handleFollow(e: FollowedUserEvent) {
    val follower = QUser().id.eq(e.followerId).findOne()!!
    val followed = QUser().id.eq(e.followedId).findOne()!!
    Follower(follower = follower, followed = followed).save()
}

private fun handleUnfollow(e: UnfollowedUserEvent) {
    QFollower()
        .follower.id.eq(e.followerId)
        .followed.id.eq(e.followedId)
        .delete()
}

private fun handleNotifications(e: NotificationsToggledEvent) {
    /*
    val user = QUser().id.eq(e.userId).findOne() ?: return
    user.notificationsEnabled = e.enabled
    user.save()
     */
}

private fun handleNewRecipe(e: NewRecipeEvent) {
    /*
    val authorId = e.authorId
    val followers = QFollower().followed.id.eq(authorId).findList()

    for (f in followers) {
        val user = QUser().id.eq(f.follower?.id).findOne() ?: continue
        if (user.notificationsEnabled) {
            eu.cooknco.Mail(user.email, PASSWORD_RESET_TITLE[user.locale], PASSWORD_RESET).send()
        }
    }

     */
}

private fun getMailAndLocale(id: Long): Pair<String, Locale> {
    val user = QUser().id.eq(id).findOne()!!
    return decrypt(user.encryptedMail) to user.locale
}


private fun getFollowersMail(id: Long): List<String> =
    QFollower().followed.id.eq(id).findList().map { decrypt(it.follower!!.encryptedMail) }


private fun handlePasswordResetRequest(e: PasswordResetRequestedEvent) =
    sendToUser(EmailTemplateKind.PASSWORD_RESET, e.userId, e.token)

private fun handleAccountVerificationRequested(e: AccountVerificationRequestedEvent) =
    sendToUser(EmailTemplateKind.ACCOUNT_VERIFICATION, e.userId, e.token)

/**
 * Sends one of the mails the app knows about, in the recipient's own locale.
 *
 * The wording is whatever is in service for that mail — what an operator saved in the
 * backoffice, or the copy packaged in the jar — and the link is built from the kind, so the
 * address in the mail and the one the backoffice previews are the same expression.
 */
private fun sendToUser(kind: EmailTemplateKind, userId: Long, token: String) {
    val (emailAddress, locale) = getMailAndLocale(userId)
    val (subject, body) = MailTemplateStore.get(kind, locale)
    val values = mapOf(MailPlaceholder.LINK to kind.link(frontendUrl, token))
    Mail(
        recipient = emailAddress,
        subject = EmailTemplates.render(subject, values),
        body = EmailTemplates.render(body, values),
    ).send()
}

/**
 * A mail an operator asked the backoffice for, to see a wording land in a real inbox.
 *
 * It goes out through the same store and the same sender as the real thing, which is the
 * whole point of routing the request through here. Only the values are samples: there is no
 * account behind a test, so a wording nothing fills shows its `{{placeholders}}` as they are.
 */
private fun handleTestMailRequested(e: TestMailRequestedEvent) {
    val wording = MailTemplateStore.get(e.templateKey, e.locale)
    if (wording == null) {
        logger.error { "No wording in service for ${e.templateKey}/${e.locale}: test mail dropped" }
        return
    }
    val (subject, body) = wording
    val values = EmailTemplates.sampleValues(EmailTemplateKind.of(e.templateKey), frontendUrl)
    Mail(
        recipient = decrypt(e.encryptedRecipient),
        subject = EmailTemplates.render(subject, values),
        body = EmailTemplates.render(body, values),
    ).send()
}

private fun decrypt(encryptedEmail: String): String {
    val decoded = Base64.getDecoder().decode(encryptedEmail)
    val ivBytes = decoded.copyOfRange(0, 16)
    val encryptedBytes = decoded.copyOfRange(16, decoded.size)

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(ivBytes))
    return String(cipher.doFinal(encryptedBytes))
}
