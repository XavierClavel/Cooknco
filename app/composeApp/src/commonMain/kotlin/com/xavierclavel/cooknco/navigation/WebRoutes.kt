package com.xavierclavel.cooknco.navigation

import io.ktor.http.Parameters
import io.ktor.http.Url

/**
 * The one place a cooknco.eu web address is built, and the one place it becomes a route of
 * this app.
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
     * The scheme a shared address must carry.
     *
     * Not decoration, and not something a link may lose: neither platform's claim covers
     * `http`, and the ingress terminates TLS only — a plain-http address gets a 404 from
     * Traefik rather than a redirect to fix it up. A schemeless `cooknco.eu/…` is the same
     * failure once a mail or a messaging app linkifies it.
     */
    private const val SCHEME = "https"

    /**
     * The privacy policy, on the website, opened in a browser from the settings screen.
     *
     * Deliberately *not* a [Shareable]: claiming the path would open an app that has no
     * screen for it, and the policy has to be readable by somebody with no account — which
     * is what a browser does and a signed-in app cannot. The same holds for the deletion
     * notice at `/account-deletion`, which is the page the Play Console points at; the app
     * does not link that one, because the account is deleted from the settings screen
     * itself.
     */
    const val PRIVACY_POLICY_URL = "$SCHEME://$HOST/privacy"

    /**
     * What can be shared: the web path that serves it, the query parameter carrying the id,
     * and the app route it opens.
     *
     * One list, read in both directions — [urlFor] builds a shareable address from it and
     * [routeFor] places one — because they were two lists until a share button copied
     * `cooknco.eu/recipe?id=…`: a path the website does not serve, the manifest and the
     * association file do not claim, and this file could not place. It opened a generic
     * page, in a browser.
     *
     * These four are exactly what the manifest's `pathPrefix` entries and the
     * `apple-app-site-association` components list. The three have to agree.
     */
    enum class Shareable(
        val path: String,
        val parameter: String,
        private val screen: String,
        /**
         * Whether `?id=` is read as well as [parameter]. True only where a link carrying it
         * is genuinely in circulation — a notification stored before these paths named their
         * ids after what they hold. It is off for [USER] on purpose: the website serves
         * `?user=` there, so `/user/view?id=12` is a shape nothing mints, and placing it
         * would be guessing rather than accepting.
         */
        private val alsoReadsId: Boolean = false,
    ) {
        RECIPE("/recipe/view", "id", "recipe"),
        USER("/user/view", "user", "user"),
        // These two name their id after what they hold, which is the website's shape:
        // `toViewCookbook` in frontend/src/scripts/common.ts navigates to
        // `?cookbook=`, the view page reads `route.query.cookbook`, and
        // LinkPreviewController serves the `og:` tags off the same name. Minting `?id=`
        // here instead would hand out a link the website answers with an empty page.
        COOKBOOK("/cookbook/view", "cookbook", "cookbook", alsoReadsId = true),
        INGREDIENT("/ingredient/view", "ingredient", "ingredient", alsoReadsId = true),
        ;

        internal fun idIn(parameters: Parameters): String? =
            parameters[parameter] ?: if (alsoReadsId) parameters["id"] else null

        internal fun routeFor(id: String) = "$screen/$id"
    }

    /**
     * The address to share for [what] with [id] — the full URL, scheme included, of the page
     * the website serves and the app claims.
     */
    fun urlFor(what: Shareable, id: Long): String =
        "$SCHEME://$HOST${what.path}?${what.parameter}=$id"

    /**
     * Translates one of the backend's app-relative paths, query string included, such as
     * `/recipe/view?id=12`.
     */
    fun routeForPath(link: String?): String? {
        if (link.isNullOrBlank()) return null
        // Resolved against a base because the stored value is a path, which Url alone rejects
        val parsed = runCatching { Url("$SCHEME://$HOST${if (link.startsWith("/")) link else "/$link"}") }
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

    private fun routeFor(path: String, parameters: Parameters): String? {
        // Both shapes are in circulation for these — `/recipe/view?id=1` but
        // `/user/view/?user=1` — so the trailing slash is trimmed rather than matched.
        val trimmed = path.trimEnd('/')
        // Not a path the backend ever stores, and not one the website serves either — this
        // one is minted by the cook timer's own notification, which goes through here so
        // that a tap lands on a route rather than on a screen this file does not know
        // about. Nothing shares it, which is why it is not a Shareable.
        if (trimmed == "/recipe/cook") return parameters["id"]?.let { "recipe/$it/cook" }
        val shareable = Shareable.entries.firstOrNull { it.path == trimmed } ?: return null
        return shareable.idIn(parameters)?.let { shareable.routeFor(it) }
    }
}
