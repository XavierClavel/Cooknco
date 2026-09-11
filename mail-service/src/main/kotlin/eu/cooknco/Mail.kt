package eu.cooknco

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage

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
    fun send() = Mailer.deliver(this)

    internal fun toMessage(session: Session): MimeMessage =
        MimeMessage(session).apply {
            setFrom(InternetAddress(Mailer.smtpEmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient))
            setSubject(subject)
            setText(body, "utf-8", "html")
        }
}
