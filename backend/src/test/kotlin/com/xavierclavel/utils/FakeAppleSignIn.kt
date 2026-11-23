package main.com.xavierclavel.utils

import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.services.AppleIdentity
import com.xavierclavel.services.AppleSignIn
import io.ktor.http.URLBuilder

/**
 * Stands in for Apple, which no test can reach: [identities] says who each authorization code
 * belongs to, and a code not in it is refused the way Apple's token endpoint refuses one.
 *
 * The authorize URL is real in shape — it carries the state — so a test reads the state off the
 * redirect exactly as Apple would, rather than being handed it.
 */
class FakeAppleSignIn : AppleSignIn {
    val identities = java.util.concurrent.ConcurrentHashMap<String, AppleIdentity>()

    override fun authorizeUrl(state: String, redirectUri: String): String =
        URLBuilder(AppleSignIn.AUTHORIZE_URL).apply {
            parameters.append("redirect_uri", redirectUri)
            parameters.append("state", state)
        }.buildString()

    override suspend fun identify(code: String, redirectUri: String): AppleIdentity =
        identities[code] ?: throw UnauthorizedException(UnauthorizedCause.OAUTH_FAILED)

    fun reset() = identities.clear()
}
