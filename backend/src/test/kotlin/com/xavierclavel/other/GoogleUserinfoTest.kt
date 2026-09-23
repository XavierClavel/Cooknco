package main.com.xavierclavel.other

import com.xavierclavel.controllers.parseGoogleUserinfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What Google answers the userinfo request with, and what the sign-in makes of it.
 *
 * This is the whole of the Google flow that can be tested here — the rest of it is a browser,
 * a consent screen and a redirect Google builds itself — and it is also where it broke: a
 * Workspace account carries an `hd` claim the strict parser refused, so `oauth_failed` came
 * back before anything had looked at who was signing in.
 *
 * The payloads are real responses rather than minimal ones, because the bug was in a field
 * nobody thought to write down.
 */
class GoogleUserinfoTest {

    /**
     * A Workspace account, in the shape `openidconnect.googleapis.com/v1/userinfo` answers:
     * every claim a real response carried, with fixture values in place of somebody's identity.
     */
    private val workspaceAccount = """
        {
          "sub": "100000000000000000001",
          "name": "Alex Martin",
          "given_name": "Alex",
          "family_name": "Martin",
          "picture": "https://lh3.googleusercontent.com/a/AAAAAAAAAAAAAAAAAAAA\u003ds96-c",
          "email": "alex.martin@example.com",
          "email_verified": true,
          "hd": "example.com"
        }
    """.trimIndent()

    /** The same account with no Workspace behind it: no `hd`, and the case that always worked. */
    private val personalAccount = """
        {
          "sub": "100000000000000000001",
          "name": "Alex Martin",
          "given_name": "Alex",
          "family_name": "Martin",
          "picture": "https://lh3.googleusercontent.com/a/AAAAAAAAAAAAAAAAAAAA",
          "email": "alex.martin@gmail.com",
          "email_verified": true
        }
    """.trimIndent()

    @Test
    fun `a workspace account is read, hd and all`() {
        val info = parseGoogleUserinfo(workspaceAccount)

        assertEquals("100000000000000000001", info?.sub)
        assertEquals("alex.martin@example.com", info?.email)
        assertEquals("Alex Martin", info?.name)
    }

    @Test
    fun `a personal account is read the same way`() {
        val info = parseGoogleUserinfo(personalAccount)

        assertEquals("100000000000000000001", info?.sub)
        assertEquals("alex.martin@gmail.com", info?.email)
    }

    /**
     * `hd` is the claim that broke it, but naming only `hd` would leave the next one to break
     * it too: OpenID Connect lets a provider send whatever it likes, and Google does.
     */
    @Test
    fun `claims this product has never heard of are ignored`() {
        val info = parseGoogleUserinfo(
            """
            {
              "sub": "1",
              "email": "cook@example.com",
              "locale": "fr",
              "nickname": "cook",
              "profile": "https://plus.google.com/1",
              "updated_at": 1700000000,
              "a_claim_invented_after_this_test_was_written": {"nested": [1, 2, 3]}
            }
            """.trimIndent()
        )

        assertEquals("1", info?.sub)
        assertEquals("cook@example.com", info?.email)
    }

    /**
     * The other half of the fix: lenient about what it does not need, and unchanged about what
     * it does. `sub` is the identity an account is found by and `email` is what one is created
     * with, so a document short of either is refused rather than made into a user with holes.
     */
    @Test
    fun `a document with no sub is refused`() {
        assertNull(parseGoogleUserinfo("""{"email": "cook@example.com"}"""))
    }

    @Test
    fun `a document with no email is refused`() {
        assertNull(parseGoogleUserinfo("""{"sub": "1"}"""))
    }

    @Test
    fun `an answer that is not the document at all is refused`() {
        assertNull(parseGoogleUserinfo("<html><body>502 Bad Gateway</body></html>"))
    }

    /** The picture arrives JSON-escaped; a caller gets the URL, not the escape. */
    @Test
    fun `escapes in the payload are decoded`() {
        // Guards the fixture before it guards the parser: Google escapes the `=`, and a
        // fixture that quietly lost the escape would assert nothing at all.
        assertTrue(workspaceAccount.contains("""\u003d"""), "fixture lost its JSON escape")

        val picture = parseGoogleUserinfo(workspaceAccount)?.picture

        assertTrue(picture!!.endsWith("=s96-c"), "expected a decoded '=', got: $picture")
    }
}
