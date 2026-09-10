package com.xavierclavel.utils

import com.samskivert.mustache.Mustache

/**
 * The two pages the OAuth flow shows a person: the consent screen, and the dead end when a
 * request cannot be honoured.
 *
 * Rendered here rather than in the Vue app because they are part of the authorization server,
 * not part of the product: they must work with no app state, no API call and no JavaScript, and
 * what they say has to match what the server is about to do. That is also why they are not
 * operator-editable, unlike the mail wordings and document layouts — the sentence naming the
 * client and where its code will be sent is a security control, not copy.
 *
 * Mustache escapes every value, so a client that named itself `<script>` is shown as text. The
 * client name is the only thing on the page a stranger controls, which is why the registered
 * redirect URI is printed next to it: a client claiming to be something it is not cannot hide
 * where the code would actually go.
 */
object OAuthPages {
    private val compiler: Mustache.Compiler = Mustache.compiler().defaultValue("").escapeHTML(true)

    private val consentTemplate: String by lazy { load("/oauth/consent.html") }
    private val errorTemplate: String by lazy { load("/oauth/error.html") }

    private fun load(resource: String): String =
        javaClass.getResource(resource)?.readText()
            ?: error("OAuth page $resource is missing from the jar")

    fun consent(
        clientName: String,
        redirectUri: String,
        username: String,
        requestId: String,
        decisionUrl: String,
    ): String = compiler.compile(consentTemplate).execute(
        mapOf(
            "clientName" to clientName,
            "redirectUri" to redirectUri,
            "username" to username,
            "requestId" to requestId,
            "decisionUrl" to decisionUrl,
        ),
    )

    fun error(message: String): String =
        compiler.compile(errorTemplate).execute(mapOf("message" to message))
}
