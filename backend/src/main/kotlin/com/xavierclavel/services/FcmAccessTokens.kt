package com.xavierclavel.services

import com.xavierclavel.utils.logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.util.Base64

/**
 * The half of a Google service account key this needs.
 *
 * Read with `ignoreUnknownKeys`, so the file downloaded from the Firebase console is used
 * as it is rather than trimmed to fit — an operator rotating a key replaces the whole file.
 */
@Serializable
private data class ServiceAccountKey(
    @SerialName("project_id") val projectId: String = "",
    @SerialName("client_email") val clientEmail: String = "",
    @SerialName("private_key") val privateKey: String = "",
    @SerialName("token_uri") val tokenUri: String = "https://oauth2.googleapis.com/token",
)

@Serializable
private data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
)

/**
 * Mints the OAuth2 access tokens the FCM HTTP v1 API is called with.
 *
 * Hand-rolled rather than pulled in with `google-auth-library`, which would bring the
 * Google HTTP client and Guava along for what is one signed JWT and one form POST. The
 * exchange is the documented service-account flow: sign a short-lived assertion with the
 * key's private half, and trade it for an access token.
 *
 * Tokens are cached until shortly before they expire, so a broadcast to a thousand devices
 * is one token exchange rather than a thousand.
 */
class FcmAccessTokens(
    credentialsPath: String,
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) {
    companion object {
        private const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
        private const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"

        /** How long the assertion is valid. Google's documented maximum is an hour. */
        private val ASSERTION_LIFETIME = Duration.ofMinutes(60)

        /**
         * How long before expiry a cached token is treated as spent.
         *
         * Generous because the cost of being wrong is asymmetric: refreshing early wastes
         * one cheap request, refreshing late fails a push that is already out of the caller's
         * hands.
         */
        private val REFRESH_MARGIN = Duration.ofMinutes(5)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val key: ServiceAccountKey = run {
        val file = File(credentialsPath)
        require(file.isFile) { "No push credentials at $credentialsPath" }
        json.decodeFromString<ServiceAccountKey>(file.readText()).also {
            require(it.clientEmail.isNotBlank() && it.privateKey.isNotBlank()) {
                "Push credentials at $credentialsPath are missing client_email or private_key"
            }
        }
    }

    /** The project the credentials belong to, so nothing has to state it twice. */
    val projectId: String get() = key.projectId

    @Volatile
    private var cached: String? = null

    @Volatile
    private var expiresAtMillis: Long = 0

    /**
     * A usable access token, minted if the one in hand is spent.
     *
     * @throws IllegalStateException when Google refuses the assertion — a revoked key, a
     *   clock far out of step — which the caller reports as a failed push rather than
     *   retrying, since neither resolves on its own.
     */
    fun get(): String {
        cached?.takeIf { System.currentTimeMillis() < expiresAtMillis }?.let { return it }
        synchronized(this) {
            // Another thread may have refreshed while this one waited on the lock
            cached?.takeIf { System.currentTimeMillis() < expiresAtMillis }?.let { return it }
            val (token, lifetime) = exchange()
            cached = token
            expiresAtMillis = System.currentTimeMillis() + lifetime.toMillis() - REFRESH_MARGIN.toMillis()
            return token
        }
    }

    /** Drops the cached token, so the next call mints a fresh one. Used after a 401. */
    fun invalidate() {
        cached = null
        expiresAtMillis = 0
    }

    private fun exchange(): Pair<String, Duration> {
        val form = "grant_type=${encode(GRANT_TYPE)}&assertion=${encode(assertion())}"
        val request = HttpRequest.newBuilder(URI.create(key.tokenUri))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            // The body names the reason (invalid_grant, invalid_client) and holds no secret
            "Google refused the push credentials: ${response.statusCode()} ${response.body()}"
        }

        val body = json.decodeFromString<AccessTokenResponse>(response.body())
        check(body.accessToken.isNotBlank()) { "Google returned no access token" }
        logger.info { "Minted an FCM access token, valid ${body.expiresIn}s" }
        return body.accessToken to Duration.ofSeconds(body.expiresIn)
    }

    /** The signed JWT traded for an access token. */
    private fun assertion(): String {
        val now = System.currentTimeMillis() / 1000
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val claims = """{"iss":"${key.clientEmail}","scope":"$SCOPE","aud":"${key.tokenUri}",""" +
            """"iat":$now,"exp":${now + ASSERTION_LIFETIME.seconds}}"""
        val signingInput = "${base64Url(header.toByteArray())}.${base64Url(claims.toByteArray())}"
        return "$signingInput.${base64Url(sign(signingInput))}"
    }

    private fun sign(input: String): ByteArray =
        Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey())
            update(input.toByteArray(StandardCharsets.UTF_8))
        }.sign()

    /** The PEM in the key file is PKCS#8, which is what [KeyFactory] wants once unwrapped. */
    private fun privateKey() = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(
                key.privateKey
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace(Regex("\\s"), "")
            )
        )
    )

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
