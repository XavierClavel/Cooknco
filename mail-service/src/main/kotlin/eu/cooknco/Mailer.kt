package eu.cooknco

import jakarta.mail.Authenticator
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.SendFailedException
import jakarta.mail.Session
import jakarta.mail.Transport
import shared.utils.logger
import java.util.Properties

/**
 * The SMTP connection everything goes out over.
 *
 * `Transport.send` opens a connection per message, which means a TCP handshake, a TLS
 * negotiation and an AUTH round trip for every mail — most of the cost of sending one.
 * Holding the connection open instead turns a fan-out to a thousand followers from a
 * thousand logins into one, and it is the difference between a fan-out that finishes and
 * one that is still going an hour later.
 *
 * Sends are serialized, deliberately: SMTP is one request at a time per connection anyway,
 * and the consumer that calls this handles one record at a time. Sending from several
 * threads would need a connection each, not a lock each.
 */
object Mailer {
    val smtpEmail: String = System.getenv("COOKNCO_SMTP_ADDRESS")
    private val smtpPassword: String = System.getenv("COOKNCO_SMTP_PASSWORD")

    private val props = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.host", "smtp.gmail.com")
        put("mail.smtp.port", "587")
        // Without these a half-open connection hangs the consumer thread for good, and the
        // mails queued behind it never go out. Better a failure the consumer can retry.
        put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS)
        put("mail.smtp.timeout", READ_TIMEOUT_MS)
        put("mail.smtp.writetimeout", READ_TIMEOUT_MS)
    }

    private val session: Session = Session.getInstance(props, object : Authenticator() {
        override fun getPasswordAuthentication() = PasswordAuthentication(smtpEmail, smtpPassword)
    })

    private var transport: Transport? = null

    /**
     * Puts one mail on the wire, reusing the open connection or dialling a new one.
     *
     * Nothing is caught: a failure has to reach the consumer, which is what decides whether
     * to retry it. Swallowing one here would make every lost mail look exactly like a
     * delivered one.
     */
    @Synchronized
    fun deliver(mail: Mail) {
        val message = mail.toMessage(session)
        try {
            connection().sendMessage(message, message.allRecipients)
        } catch (e: SendFailedException) {
            // The address was refused, not the connection: keep it for the next mail
            throw e
        } catch (e: MessagingException) {
            // Anything else and the connection is suspect, so drop it rather than hand the
            // next mail a socket the server has already given up on
            disconnect()
            throw e
        }
    }

    private fun connection(): Transport {
        transport?.takeIf { it.isConnected }?.let { return it }
        disconnect()
        logger.info { "Opening SMTP connection to ${props["mail.smtp.host"]}" }
        return session.getTransport("smtp").also {
            it.connect(smtpEmail, smtpPassword)
            transport = it
        }
    }

    private fun disconnect() {
        try {
            transport?.close()
        } catch (e: MessagingException) {
            logger.debug { "SMTP connection was already gone: ${e.message}" }
        }
        transport = null
    }

    private const val CONNECT_TIMEOUT_MS = "10000"
    private const val READ_TIMEOUT_MS = "30000"
}
