package com.xavierclavel.services

import com.xavierclavel.models.query.QUser
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.EmailTemplateKind
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The way out of the notification mails, carried by the mails themselves.
 *
 * A notification mail arrives because somebody *else* published a recipe, so it has to say
 * how to stop — and it has to work from the inbox, in one click, from a mail that may be a
 * year old by the time it is read. An unsubscribe that first asked the reader to remember a
 * password is one they would reasonably answer with the spam button instead.
 *
 * The token is therefore **derived, not stored**: `<user id>.<signature>`, the signature an
 * HMAC over the id. Three things follow from that, and they are the reasons for it:
 *
 * - **Nothing expires and nothing has to be minted in advance.** A row per account would
 *   have to exist before the first mail goes out, survive every mail already sent, and be
 *   backfilled for every account that predates the feature.
 * - **No new secret.** It is keyed on `encryption.key`, which the backend already has, so
 *   this adds nothing to `cooknco-config` — the same reasoning that keeps the OAuth codes
 *   opaque in Redis rather than signed JWTs.
 * - **A forgery is refused rather than acted on.** Without the key, a token for somebody
 *   else's account cannot be written, and the id in front of the signature is not a secret
 *   worth hiding — it is the signature that authorises, and it authorises one thing.
 *
 * That one thing is switching these mails **off**. Never on: an attacker holding a token
 * could otherwise put an address they had already silenced back on the list, and the account
 * would have no idea. Re-subscribing is the settings page, behind a real session.
 *
 * This is deliberately *not* `users.token`, which resets passwords and confirms addresses. A
 * link in every notification mail is the widest distribution any credential of ours gets;
 * the one it carries must be worth nothing beyond an unsubscribe.
 */
class UnsubscribeService: KoinComponent {
    private val configuration: Configuration by inject()

    companion object {
        private const val HMAC = "HmacSHA256"

        /**
         * Domain separation. The key signs one kind of thing here, and prefixing what is
         * signed means a signature minted for anything else it is ever asked to sign cannot
         * be replayed as an unsubscribe, nor the other way round.
         */
        private const val PURPOSE = "unsubscribe:"
    }

    /** The address the unsubscribe link in [recipientId]'s mails points at. */
    fun linkFor(recipientId: Long): String =
        EmailTemplateKind.unsubscribeLink(configuration.frontend.url, tokenFor(recipientId))

    fun tokenFor(recipientId: Long): String = "$recipientId.${signature(recipientId)}"

    /**
     * Spends a token: stops mailing the account it names about what other people are up to.
     *
     * Idempotent, because the same link is in every mail already sent and in the reader's
     * archive for good — a second press has to answer the same as the first. An account that
     * has since been deleted counts as done too: it is receiving nothing either way, and the
     * signature proves the request came from a mail we sent rather than from a guess.
     *
     * @return false only when the token is not one this backend minted
     */
    fun unsubscribe(token: String): Boolean {
        val recipientId = verify(token) ?: return false
        val user = QUser().id.eq(recipientId).findOne() ?: return true
        if (!user.mailNotificationsEnabled) return true

        user.mailNotificationsEnabled = false
        user.update()
        logger.info { "Account $recipientId unsubscribed from notification mails" }
        return true
    }

    /** The account a token names, or null when the signature is not ours. */
    private fun verify(token: String): Long? {
        val recipientId = token.substringBefore('.').toLongOrNull() ?: return null
        val presented = token.substringAfter('.', "")
        // Constant-time: comparing byte by byte would tell a guesser how much of a forgery
        // was already right, which is all it takes to build the rest one byte at a time
        if (!MessageDigest.isEqual(presented.toByteArray(), signature(recipientId).toByteArray())) return null
        return recipientId
    }

    private fun signature(recipientId: Long): String {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(configuration.encryption.key.toByteArray(), HMAC))
        // URL-safe and unpadded: this ends up in a query parameter a mail client has to
        // reproduce exactly, and '+', '/' and '=' are all things one may decide to escape
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$PURPOSE$recipientId".toByteArray()))
    }
}
