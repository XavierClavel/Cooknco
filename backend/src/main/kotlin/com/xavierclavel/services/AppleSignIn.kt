package com.xavierclavel.services

import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Who Apple says signed in.
 *
 * @property sub Apple's stable id for the account, scoped to our team: the one thing an account
 *   is found by on the way back. Never the mail, which the user can stop relaying.
 * @property email what Apple shares — often a `@privaterelay.appleid.com` address, which
 *   forwards to the real one and is as good as any for every mail this product sends.
 */
data class AppleIdentity(
    val sub: String,
    val email: String?,
)

/**
 * Sign in with Apple, from this backend's side: where to send the browser, and what the code it
 * comes back with is worth.
 *
 * An interface for the reason [RecipePhotoReader] is one: the real thing is somebody else's
 * service, which no test should reach and no developer's backend has a key for.
 */
interface AppleSignIn {
    /** Apple's authorization page, asking for the name and the mail, answering by `form_post`. */
    fun authorizeUrl(state: String, redirectUri: String): String

    /** Spends [code] at Apple's token endpoint and reads who it belongs to. */
    suspend fun identify(code: String, redirectUri: String): AppleIdentity

    companion object {
        const val AUTHORIZE_URL = "https://appleid.apple.com/auth/authorize"
        const val TOKEN_URL = "https://appleid.apple.com/auth/token"
        const val ISSUER = "https://appleid.apple.com"

        private val lenientJson = Json { ignoreUnknownKeys = true }

        /**
         * The name in the `user` field Apple posts alongside the code — on the **first**
         * authorization only, and never inside the ID token. A later sign-in carries none, so
         * the name is used to pick a username at creation and is not looked for afterwards.
         *
         * Null when absent or unreadable: a missing name is not a reason to refuse a sign-in.
         */
        fun nameFromUserField(user: String?): String? {
            if (user.isNullOrBlank()) return null
            return runCatching {
                val name = lenientJson.parseToJsonElement(user).jsonObject["name"]?.jsonObject ?: return null
                listOfNotNull(
                    name["firstName"]?.jsonPrimitive?.contentOrNull,
                    name["lastName"]?.jsonPrimitive?.contentOrNull,
                )
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .takeIf { it.isNotEmpty() }
            }.getOrNull()
        }

        /**
         * The claims of an ID token, checked for issuer, audience and expiry.
         *
         * The signature is **not** verified, and does not need to be here: the token is read
         * off the response of our own call to Apple's token endpoint, over TLS and authenticated
         * by our client secret, which OpenID Connect Core §3.1.3.7 accepts in place of the
         * signature. Do not reuse this for a token a *client* hands over — a native iOS sign-in,
         * say — where checking the signature against Apple's keys is the whole point.
         *
         * `internal` so the test reads tokens through the same code the callback does.
         */
        internal fun claimsOf(idToken: String, clientId: String, now: Instant = Instant.now()): AppleIdentity? {
            val parts = idToken.split('.')
            if (parts.size != 3) return null
            val claims = runCatching {
                lenientJson.parseToJsonElement(String(Base64.getUrlDecoder().decode(parts[1]))).jsonObject
            }.getOrNull() ?: return null

            if (claims.string("iss") != ISSUER) return null
            // `aud` is a string from Apple, but the spec allows an array and costs nothing to accept
            val audience = claims["aud"]
            val audiences = when (audience) {
                is JsonPrimitive -> listOfNotNull(audience.contentOrNull)
                is kotlinx.serialization.json.JsonArray -> audience.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                else -> emptyList()
            }
            if (clientId !in audiences) return null
            val exp = claims["exp"]?.jsonPrimitive?.longOrNull ?: return null
            if (exp <= now.epochSecond) return null

            val sub = claims.string("sub")?.takeIf { it.isNotBlank() } ?: return null
            // Apple sends `email_verified` as the string "true" as often as the boolean. An
            // unverified address is not one to create an account against.
            val verified = claims["email_verified"]?.jsonPrimitive?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
            val email = claims.string("email")?.takeIf { it.isNotBlank() && verified != false }
            return AppleIdentity(sub = sub, email = email)
        }

        private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    }
}

/**
 * What an install with no Apple key answers: a 503, from both ends of the flow, so a button that
 * shows anyway leads to an explicit refusal rather than to Apple's own error page.
 */
class UnconfiguredAppleSignIn : AppleSignIn {
    override fun authorizeUrl(state: String, redirectUri: String): String =
        throw ServiceUnavailableException(ServiceUnavailableCause.APPLE_SIGN_IN_NOT_CONFIGURED)

    override suspend fun identify(code: String, redirectUri: String): AppleIdentity =
        throw ServiceUnavailableException(ServiceUnavailableCause.APPLE_SIGN_IN_NOT_CONFIGURED)
}

/**
 * The real Sign in with Apple, over the web flow.
 *
 * The web flow and not the native one on purpose: it is the one every client can take — the
 * site, the Android app and the iOS app all open it in a browser exactly as they open Google's —
 * so there is one callback, one account lookup and one place to get it wrong.
 */
class AppleSignInClient(private val config: Configuration.OAuth.Apple) : AppleSignIn {

    /** Parsed once, so a malformed key fails the backend's start rather than somebody's sign-in. */
    private val privateKey: PrivateKey = parsePrivateKey(config.privateKey)

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    @Volatile private var cachedSecret: Pair<String, Instant>? = null

    override fun authorizeUrl(state: String, redirectUri: String): String =
        URLBuilder(AppleSignIn.AUTHORIZE_URL).apply {
            parameters.append("client_id", config.clientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("response_type", "code")
            // Asking for the name or the mail obliges `form_post`: Apple will not put either in
            // a query string. Which is why the callback is a POST.
            parameters.append("response_mode", "form_post")
            parameters.append("scope", "name email")
            parameters.append("state", state)
        }.buildString()

    override suspend fun identify(code: String, redirectUri: String): AppleIdentity {
        val response = try {
            client.submitForm(
                url = AppleSignIn.TOKEN_URL,
                formParameters = parameters {
                    append("client_id", config.clientId)
                    append("client_secret", clientSecret())
                    append("code", code)
                    append("grant_type", "authorization_code")
                    append("redirect_uri", redirectUri)
                },
            )
        } catch (e: Exception) {
            logger.warn(e) { "Apple's token endpoint could not be reached" }
            throw UnauthorizedException(UnauthorizedCause.OAUTH_FAILED)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // Apple's error body is `{"error": "invalid_grant"}` and the like: no secret in it
            logger.info { "Apple refused the authorization code: ${response.status} $body" }
            throw UnauthorizedException(UnauthorizedCause.OAUTH_FAILED)
        }
        val idToken = runCatching {
            Json.parseToJsonElement(body).jsonObject["id_token"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return idToken?.let { AppleSignIn.claimsOf(it, config.clientId) } ?: run {
            logger.info { "Apple's token response carried no usable ID token" }
            throw UnauthorizedException(UnauthorizedCause.OAUTH_FAILED)
        }
    }

    /**
     * The client secret: an ES256 JWT naming our team, key and Services ID.
     *
     * Apple accepts one for up to six months; it is minted for a day and reused until an hour
     * before that, so a sign-in costs no signature and a clock skew costs nothing.
     */
    internal fun clientSecret(): String {
        val now = Instant.now()
        cachedSecret?.let { (secret, expiry) -> if (now.isBefore(expiry.minusSeconds(3600))) return secret }
        val expiry = now.plusSeconds(SECRET_LIFETIME_SECONDS)
        val header = buildJsonObject {
            put("alg", "ES256")
            put("kid", config.keyId)
        }
        val payload = buildJsonObject {
            put("iss", config.teamId)
            put("iat", now.epochSecond)
            put("exp", expiry.epochSecond)
            put("aud", AppleSignIn.ISSUER)
            put("sub", config.clientId)
        }
        val signingInput = "${base64Url(header.toString().toByteArray())}.${base64Url(payload.toString().toByteArray())}"
        // P1363 is the raw r‖s a JWS wants; the default DER encoding is what every hand-rolled
        // ES256 gets wrong, and Apple answers `invalid_client` to it
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initSign(privateKey)
            update(signingInput.toByteArray())
            sign()
        }
        return "$signingInput.${base64Url(signature)}".also { cachedSecret = it to expiry }
    }

    companion object {
        private const val SECRET_LIFETIME_SECONDS = 24L * 60 * 60

        private fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        /** The `.p8` Apple hands out: a PKCS#8 EC key, PEM-armoured. */
        internal fun parsePrivateKey(pem: String): PrivateKey {
            val der = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .filterNot { it.isWhitespace() }
            return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(der)))
        }
    }
}
