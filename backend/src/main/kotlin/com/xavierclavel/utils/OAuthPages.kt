package com.xavierclavel.utils

import com.samskivert.mustache.Mustache
import com.samskivert.mustache.Template
import java.io.StringReader

/**
 * The three pages the OAuth flow shows a person: the consent screen, the dead end when a
 * request cannot be honoured, and the note that a request was answered already.
 *
 * Rendered here rather than in the Vue app because they are part of the authorization server,
 * not part of the product: they must work with no app state, no API call and no JavaScript, and
 * what they say has to match what the server is about to do. That is also why they are not
 * operator-editable, unlike the mail wordings and document layouts — the sentence naming the
 * client and where its code will be sent is a security control, not copy.
 *
 * They do still have to *look* like the product, which is what `oauth/theme.css` is for: both
 * pages pull it in as a partial, so the site's palette and borders are stated once. A partial
 * rather than a `{{{raw}}}` value so that nothing on these pages is ever rendered unescaped.
 *
 * Mustache escapes every value, so a client that named itself `<script>` is shown as text. The
 * client name is the only thing on the page a stranger controls, which is why the registered
 * redirect URI is printed next to it: a client claiming to be something it is not cannot hide
 * where the code would actually go.
 */
object OAuthPages {
    private val compiler: Mustache.Compiler = Mustache.compiler()
        .defaultValue("")
        .escapeHTML(true)
        .withLoader { name -> StringReader(load("/oauth/$name")) }

    // Compiled once, which also reads the partials once: these are files in the jar, and a
    // consent screen should not re-parse a stylesheet per request. Safe to share across the
    // concurrent requests Ktor serves — a compiled jmustache Template is final but for its
    // variable-fetcher cache, which is a ConcurrentHashMap.
    private val consentTemplate: Template by lazy { compile("/oauth/consent.html") }
    private val errorTemplate: Template by lazy { compile("/oauth/error.html") }
    private val answeredTemplate: Template by lazy { compile("/oauth/answered.html") }

    private fun compile(resource: String): Template = compiler.compile(load(resource))

    private fun load(resource: String): String =
        javaClass.getResource(resource)?.readText()
            ?: error("OAuth page $resource is missing from the jar")

    fun consent(
        clientName: String,
        redirectUri: String,
        username: String,
        requestId: String,
        decisionUrl: String,
    ): String = consentTemplate.execute(
        mapOf(
            "clientName" to clientName,
            "redirectUri" to redirectUri,
            "username" to username,
            "requestId" to requestId,
            "decisionUrl" to decisionUrl,
        ),
    )

    /**
     * [note] is what the page says under the reason, and it is not always the same reassurance:
     * a request that was refused granted nothing, while one that is merely no longer the
     * pending one may well have granted everything already.
     */
    fun error(
        message: String,
        note: String = "Nothing has been shared, and no access has been granted. You can close this page.",
    ): String = errorTemplate.execute(mapOf("message" to message, "note" to note))

    /** Not an error: the request was answered, and this says which way. */
    fun answered(heading: String, message: String): String =
        answeredTemplate.execute(mapOf("heading" to heading, "message" to message))
}
