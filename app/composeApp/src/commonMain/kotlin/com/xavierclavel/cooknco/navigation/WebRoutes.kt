package com.xavierclavel.cooknco.navigation

import io.ktor.http.Parameters
import io.ktor.http.Url

/**
 * The one place a cooknco.eu web address becomes a route of this app.
 *
 * Three things arrive here, and they share the mapping on purpose: a tapped notification
 * (an app-relative path the backend stored), the cook timer's own notification, and an
 * https link the system handed us because the app claims the domain. A link and a
 * notification pointing at the same recipe have to land on the same screen, and the
 * backend stores a *web* path because one value has to serve both clients — the web app
 * routes to it as it stands, and this maps it. Keeping the mapping in the app rather than
 * in the payload means a new screen is a change here rather than a migration of every
 * notification already sent.
 *
 * Anything this cannot place is dropped rather than guessed at. For a notification that
 * means opening the app on its own screen; for a link it means the browser keeps it, which
 * is the better of the two — the page exists.
 */
object WebRoutes {

    /** The one host the app answers for. See the manifest and the entitlement. */
    const val HOST = "cooknco.eu"

    /**
     * Translates one of the backend's app-relative paths, query string included, such as
     * `/recipe/view?id=12`.
     */
    fun routeForPath(link: String?): String? {
        if (link.isNullOrBlank()) return null
        // Resolved against a base because the stored value is a path, which Url alone rejects
        val parsed = runCatching { Url("https://$HOST${if (link.startsWith("/")) link else "/$link"}") }
            .getOrNull() ?: return null
        return routeFor(parsed.encodedPath, parsed.parameters)
    }

    /**
     * Translates a full web address — what an App Link or a Universal Link carries.
     *
     * The host is checked again here even though the platforms only ever hand over a URL
     * that matched what the app declared: this is also reached by the OAuth callback's
     * entry point, which sees every URL sent to the app whatever its scheme.
     */
    fun routeForUrl(url: String): String? {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return null
        if (parsed.protocol.name != "https" && parsed.protocol.name != "http") return null
        if (!parsed.host.equals(HOST, ignoreCase = true)) return null
        return routeFor(parsed.encodedPath, parsed.parameters)
    }

    private fun routeFor(path: String, parameters: Parameters): String? =
        // Both shapes are in circulation for these — `/recipe/view?id=1` but
        // `/user/view/?user=1` — so the trailing slash is trimmed rather than matched.
        when (path.trimEnd('/')) {
            "/recipe/view" -> parameters["id"]?.let { "recipe/$it" }
            // Not a path the backend ever stores, and not one the website serves either —
            // this one is minted by the cook timer's own notification, which goes through
            // here so that a tap lands on a route rather than on a screen this file does
            // not know about.
            "/recipe/cook" -> parameters["id"]?.let { "recipe/$it/cook" }
            "/user/view" -> parameters["user"]?.let { "user/$it" }
            "/cookbook/view" -> parameters["id"]?.let { "cookbook/$it" }
            "/ingredient/view" -> parameters["id"]?.let { "ingredient/$it" }
            else -> null
        }
}
