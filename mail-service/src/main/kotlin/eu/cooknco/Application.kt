package eu.cooknco

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import jakarta.mail.internet.AddressException
import jakarta.mail.SendFailedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shared.enums.EmailTemplateKind
import shared.events.KafkaEventConsumer
import shared.events.MailEvent
import shared.events.TestMailRequestedEvent
import shared.events.UserMailRequestedEvent
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

    val kafkaConsumer = KafkaEventConsumer("mail-service", listOf(MailEvent.MAILS_TOPIC)) { event ->
        when (event) {
            is UserMailRequestedEvent -> deliverable { handleUserMailRequested(event) }
            is TestMailRequestedEvent -> deliverable { handleTestMailRequested(event) }
            else -> logger.debug { "Ignoring event of unhandled type: $event" }
        }
    }

    // A dedicated IO thread: start() blocks on poll() and again on every SMTP round trip,
    // and must not sit on a dispatcher thread the health endpoint shares.
    launch(Dispatchers.IO) {
        kafkaConsumer.start()
    }

    routing {
        get("/health") { call.respondText("OK") }
    }
}

/**
 * Runs a send, telling a mail that will never go out apart from one that might.
 *
 * A refused or malformed address is the recipient's problem and no amount of retrying
 * changes it, so it is dropped here; anything else — a refused login, a timeout, SMTP
 * down — is thrown on, which is how the consumer knows to keep the offset and try again.
 */
private fun deliverable(send: () -> Unit) =
    try {
        send()
    } catch (e: SendFailedException) {
        logger.error(e) { "Address refused by SMTP: mail dropped" }
    } catch (e: AddressException) {
        logger.error(e) { "Malformed recipient address: mail dropped" }
    }

/**
 * Sends one of the mails the app knows about, in the locale it was addressed in.
 *
 * The event carries the address and every value the wording fills in, so this looks nothing
 * up: it reads the wording in service for that kind — what an operator saved in the
 * backoffice, or the copy packaged in the jar — and puts it on the wire.
 */
private fun handleUserMailRequested(e: UserMailRequestedEvent) {
    val wording = MailTemplateStore.get(e.templateKey, e.locale)
    if (wording == null) {
        logger.error { "No wording in service for ${e.templateKey}/${e.locale}: mail dropped [${e.dedupeKey}]" }
        return
    }
    val (subject, body) = wording
    Mail(
        recipient = decrypt(e.encryptedRecipient),
        subject = EmailTemplates.render(subject, e.values),
        body = EmailTemplates.render(body, e.values),
    ).send()
    logger.info { "Sent ${e.templateKey} to user ${e.recipientId} [${e.dedupeKey}]" }
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
