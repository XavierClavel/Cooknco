package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.services.AppleIdentity
import com.xavierclavel.services.AppleSignIn
import com.xavierclavel.services.AppleSignInClient
import com.xavierclavel.utils.Configuration
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.parameters
import org.junit.jupiter.api.Test
import shared.enums.Locale
import shared.utils.URL.AUTH_URL
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sign in with Apple, from the redirect to Apple to the session it ends in.
 *
 * Apple itself is [main.com.xavierclavel.utils.FakeAppleSignIn]; everything on this side of it —
 * the state, the browser cookie, the account lookup and creation — is the real code.
 */
class AppleSignInTest : ApplicationTest() {

    private val appleUser = """{"name":{"firstName":"Alex","lastName":"Martin"},"email":"alex@privaterelay.appleid.com"}"""

    /** Starts a sign-in and answers the state Apple would be handed. */
    private suspend fun HttpClient.startAppleSignIn(query: String = ""): String {
        val response = get("$AUTH_URL/login-oauth-apple$query")
        assertEquals(HttpStatusCode.Found, response.status, response.bodyAsText())
        val location = Url(response.headers[HttpHeaders.Location]!!)
        assertTrue(location.toString().startsWith(AppleSignIn.AUTHORIZE_URL))
        return location.parameters["state"]!!
    }

    /** What Apple's `form_post` sends back to the callback. */
    private suspend fun HttpClient.appleCallback(
        state: String,
        code: String? = null,
        user: String? = null,
        error: String? = null,
    ): HttpResponse = submitForm(
        url = "$AUTH_URL/callback-oauth-apple",
        formParameters = parameters {
            append("state", state)
            code?.let { append("code", it) }
            user?.let { append("user", it) }
            error?.let { append("error", it) }
        },
    )

    @Test
    fun `a first Apple sign-in creates a verified account and hands the app a session`() = runTest {
        fakeAppleSignIn.identities["code-1"] = AppleIdentity(sub = "apple-sub-1", email = "alex@privaterelay.appleid.com")

        val state = noRedirectClient.startAppleSignIn("?redirect=app&locale=en")
        val response = noRedirectClient.appleCallback(state, code = "code-1", user = appleUser)

        assertEquals(HttpStatusCode.Found, response.status, response.bodyAsText())
        val location = response.headers[HttpHeaders.Location]!!
        assertTrue(location.startsWith("cooknco://login/callback?token="), location)
        val token = location.substringAfter("token=")

        val user = assertNotNull(userService.findEntityByAppleId("apple-sub-1"))
        assertEquals("Alex Martin", user.username)
        assertTrue(user.isVerified)
        assertNull(user.passwordHash)
        assertEquals(Locale.EN, user.locale)
        assertEquals(user.id, userService.findEntityByMail("alex@privaterelay.appleid.com")?.id)

        // The token the app is handed is a working session
        client.get("$AUTH_URL/me") { bearerAuth(token) }.apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains("Alex Martin"))
        }
    }

    @Test
    fun `a returning Apple sign-in finds the account by sub, with no name and no mail`() = runTest {
        fakeAppleSignIn.identities["first"] = AppleIdentity(sub = "apple-sub-2", email = "sam@example.com")
        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn(), code = "first", user = appleUser)
        val created = assertNotNull(userService.findEntityByAppleId("apple-sub-2"))
        val count = userService.countAll()

        // Apple sends the name on the first authorization only, and the mail may have stopped
        // being relayed: neither is needed to find the account again
        fakeAppleSignIn.identities["second"] = AppleIdentity(sub = "apple-sub-2", email = null)
        val response = noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn(), code = "second")

        assertEquals(HttpStatusCode.Found, response.status, response.bodyAsText())
        assertEquals("${configuration.frontend.url}/home", response.headers[HttpHeaders.Location])
        assertEquals(count, userService.countAll())
        noRedirectClient.get("$AUTH_URL/me").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(bodyAsText().contains(created.username))
        }
    }

    @Test
    fun `a callback from another browser than the one that started is refused`() = runTest {
        fakeAppleSignIn.identities["code"] = AppleIdentity(sub = "apple-sub-3", email = "victim@example.com")
        val state = noRedirectClient.startAppleSignIn()

        // The state and code are valid; only the browser cookie is missing — login CSRF
        val elsewhere = newNoRedirectClient()
        elsewhere.appleCallback(state, code = "code").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.OAUTH_FAILED.key, bodyAsText())
        }
        assertNull(userService.findEntityByAppleId("apple-sub-3"))
    }

    @Test
    fun `a state is spent by its first callback`() = runTest {
        fakeAppleSignIn.identities["code"] = AppleIdentity(sub = "apple-sub-4", email = "once@example.com")
        val state = noRedirectClient.startAppleSignIn()
        assertEquals(HttpStatusCode.Found, noRedirectClient.appleCallback(state, code = "code").status)

        noRedirectClient.appleCallback(state, code = "code").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.OAUTH_FAILED.key, bodyAsText())
        }
    }

    @Test
    fun `an Apple mail that already belongs to an account is refused, not merged`() = runTest {
        // USER1 is the mail of an account the fixture created with a password
        fakeAppleSignIn.identities["code"] = AppleIdentity(sub = "apple-sub-5", email = USER1)

        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn(), code = "code").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.OAUTH_NOT_SETUP.key, bodyAsText())
        }
        assertNull(userService.findEntityByAppleId("apple-sub-5"))
    }

    @Test
    fun `a banned Apple account is refused`() = runTest {
        fakeAppleSignIn.identities["code"] = AppleIdentity(sub = "apple-sub-6", email = "banned@example.com")
        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn(), code = "code")
        userService.findEntityByAppleId("apple-sub-6")!!.apply { isBanned = true }.update()

        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn(), code = "code").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertEquals(UnauthorizedCause.ACCOUNT_BANNED.key, bodyAsText())
        }
    }

    @Test
    fun `cancelling on Apple's page goes back to the login page, signed out`() = runTest {
        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn(), error = "user_cancelled_authorize").apply {
            assertEquals(HttpStatusCode.Found, status)
            assertEquals("${configuration.frontend.url}/login", headers[HttpHeaders.Location])
        }
        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn("?redirect=app"), error = "user_cancelled_authorize").apply {
            assertEquals("cooknco://login/callback", headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `only a same-origin path is followed after signing in`() = runTest {
        fakeAppleSignIn.identities["a"] = AppleIdentity(sub = "apple-sub-7", email = "r@example.com")
        fakeAppleSignIn.identities["b"] = AppleIdentity(sub = "apple-sub-7", email = "r@example.com")

        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn("?redirect=/oauth/authorize?x=1"), code = "a").apply {
            assertEquals("/oauth/authorize?x=1", headers[HttpHeaders.Location])
        }
        noRedirectClient.appleCallback(noRedirectClient.startAppleSignIn("?redirect=//evil.example"), code = "b").apply {
            assertEquals("${configuration.frontend.url}/home", headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `the client secret is an ES256 JWT Apple can verify`() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keys.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"
        val client = AppleSignInClient(Configuration.OAuth.Apple(clientId = "eu.cooknco.signin", teamId = "TEAM123456", keyId = "KEY1234567", privateKey = pem))

        val (header, payload, signature) = client.clientSecret().split('.')
        val decode = { part: String -> String(Base64.getUrlDecoder().decode(part)) }
        assertTrue(decode(header).contains("\"alg\":\"ES256\"") && decode(header).contains("\"kid\":\"KEY1234567\""))
        assertTrue(decode(payload).contains("\"iss\":\"TEAM123456\""))
        assertTrue(decode(payload).contains("\"sub\":\"eu.cooknco.signin\""))
        assertTrue(decode(payload).contains("\"aud\":\"https://appleid.apple.com\""))

        val raw = Base64.getUrlDecoder().decode(signature)
        assertEquals(64, raw.size, "a JWS ES256 signature is r‖s, not DER")
        val verified = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initVerify(keys.public)
            update("$header.$payload".toByteArray())
            verify(raw)
        }
        assertTrue(verified)
    }

    @Test
    fun `an ID token is checked for issuer, audience, expiry and a verified mail`() {
        val now = Instant.parse("2026-11-23T12:00:00Z")
        fun token(claims: String) = "e30.${Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())}.sig"
        val exp = now.epochSecond + 600

        val good = AppleSignIn.claimsOf(
            token("""{"iss":"https://appleid.apple.com","aud":"cid","exp":$exp,"sub":"s1","email":"a@b.c","email_verified":"true","is_private_email":"true"}"""),
            "cid", now,
        )
        assertEquals(AppleIdentity("s1", "a@b.c"), good)

        assertNull(AppleSignIn.claimsOf(token("""{"iss":"https://evil","aud":"cid","exp":$exp,"sub":"s1"}"""), "cid", now))
        assertNull(AppleSignIn.claimsOf(token("""{"iss":"https://appleid.apple.com","aud":"other","exp":$exp,"sub":"s1"}"""), "cid", now))
        assertNull(AppleSignIn.claimsOf(token("""{"iss":"https://appleid.apple.com","aud":"cid","exp":${now.epochSecond - 1},"sub":"s1"}"""), "cid", now))
        assertNull(AppleSignIn.claimsOf("not-a-jwt", "cid", now))

        // An unverified mail is dropped, not trusted: the account is still found by sub
        assertEquals(
            AppleIdentity("s1", null),
            AppleSignIn.claimsOf(token("""{"iss":"https://appleid.apple.com","aud":"cid","exp":$exp,"sub":"s1","email":"a@b.c","email_verified":false}"""), "cid", now),
        )
    }

    @Test
    fun `the name comes from the user field, when there is one`() {
        assertEquals("Alex Martin", AppleSignIn.nameFromUserField(appleUser))
        assertEquals("Alex", AppleSignIn.nameFromUserField("""{"name":{"firstName":"Alex"}}"""))
        assertNull(AppleSignIn.nameFromUserField(null))
        assertNull(AppleSignIn.nameFromUserField("""{"email":"a@b.c"}"""))
        assertNull(AppleSignIn.nameFromUserField("not json"))
    }
}
