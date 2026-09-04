package eu.cooknco

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import shared.utils.logger
import java.util.Properties

/**
 * One mail, ready to go out.
 *
 * It takes the subject and body already filled in rather than a template to read: where
 * the wording came from — a backoffice edit or the copy packaged in the jar — is
 * [MailTemplateStore]'s business, and this only has to put it on the wire.
 */
class Mail(
    val recipient: String,
    val subject: String,
    val body: String,
) {
    val props = Properties().apply {
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.host", "smtp.gmail.com")
        put("mail.smtp.port", "587")
    }

    companion object {
        val smtpEmail: String = System.getenv("COOKNCO_SMTP_ADDRESS")
        val smtpPassword: String = System.getenv("COOKNCO_SMTP_PASSWORD")
    }

    fun send() {
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(smtpEmail, smtpPassword)
            }
        })

        try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(smtpEmail))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
                setSubject(subject)
                setText(body, "utf-8", "html")
            }
            Transport.send(message)
        } catch (e: MessagingException) {
            logger.error(e) { "Could not send \"$subject\"" }
        }
    }
}
