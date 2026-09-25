package com.xavierclavel.services

import com.xavierclavel.utils.Configuration
import shared.utils.URL.RECIPE_VIEW_URL
import shared.utils.URL.SITEMAP_PAGES_URL
import shared.utils.URL.SITEMAP_RECIPES_URL
import shared.utils.URL.SITEMAP_URL
import shared.utils.URL.SITEMAP_USERS_URL
import shared.utils.URL.USER_VIEW_URL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * The sitemaps a search engine reads to find the pages worth indexing.
 *
 * They exist because nothing on this site links to a recipe from anywhere a crawler can reach:
 * every listing page is behind the login, so without them the public recipe pages are
 * orphans — reachable only by guessing an id, which no crawler does. The landing page links
 * to a handful of them; these name all of them.
 *
 * `/sitemap.xml` is a sitemap *index* naming one document per kind of page — [SitemapDocument].
 * Split so that recipes and members each get the protocol's whole 50 000-URL budget rather than
 * sharing one, and so that Search Console reports indexing per kind: "recipes discovered vs
 * indexed" is the figure worth watching, and a combined file buries it under the profiles. The
 * index keeps the path robots.txt and the Search Console submission already name.
 *
 * Only the pages the app actually serves to a logged-out visitor are listed. That is a
 * shorter list than the routes the backend renders preview tags for: a cookbook's recipes
 * come from an authenticated endpoint, so `/cookbook/view` would load into an empty page for
 * a crawler, and an advertised page that renders nothing is worse for a site than one that
 * was never advertised. Adding a route here means making its page work signed out first.
 */
class SitemapService: KoinComponent {
    private val configuration: Configuration by inject()
    private val recipeService: RecipeService by inject()
    private val userService: UserService by inject()

    companion object {
        /**
         * What one sitemap document may hold, per the sitemap protocol. Crossing it does not
         * degrade — a search engine rejects the whole file — so each list is cut rather than
         * allowed to grow past it. Reaching it for one kind is the signal to page that kind
         * (`sitemap-recipes-2.xml`), which is a change to make when it is needed and not before.
         */
        const val MAX_URLS = 50_000

        /** How long a generated document is served before the catalogue is read again. */
        const val TTL_MILLIS = 60 * 60 * 1000L

        private val LASTMOD: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        private const val NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9"

        /**
         * The public pages that are not an entity, so nothing in the catalogue can name them.
         *
         * Short, and it has to stay short: these are frontend routes spelled out in the backend,
         * which only holds while the list is the handful of pages that render with no account at
         * all. The two legal documents are on it because the Play Console points at them and a
         * reviewer opens them signed out — the same reason they sit in `noLoginRedirect`.
         */
        val STATIC_PAGES = listOf("", "privacy", "account-deletion")
    }

    /** The documents served, each at its own path at the site root. */
    enum class SitemapDocument(val path: String) {
        INDEX(SITEMAP_URL),
        PAGES(SITEMAP_PAGES_URL),
        RECIPES(SITEMAP_RECIPES_URL),
        USERS(SITEMAP_USERS_URL),
    }

    private class Cached(val xml: String, val builtAt: Long)

    private val cached = ConcurrentHashMap<SitemapDocument, Cached>()

    /**
     * The document, generated at most once per [TTL_MILLIS].
     *
     * Cached because the endpoints are public and unauthenticated, and generating one walks a
     * table: a crawler asking hourly is the intended traffic, but nothing stops anyone else
     * asking in a loop.
     */
    fun sitemap(document: SitemapDocument, now: Long = System.currentTimeMillis()): String {
        cached[document]?.takeIf { now - it.builtAt < TTL_MILLIS }?.let { return it.xml }
        return build(document).also { cached[document] = Cached(it, now) }
    }

    /** Drops every cached document, so the next request rebuilds it. */
    fun invalidate() = cached.clear()

    private val siteUrl get() = configuration.frontend.url.trimEnd('/')

    private fun build(document: SitemapDocument): String = when (document) {
        SitemapDocument.INDEX -> renderIndex(
            // No `lastmod` on the children. Stating one would mean reading both tables to build a
            // document whose whole job is to be cheap, and a child is re-read on its own schedule
            // anyway — the dates that matter are the ones inside it.
            listOf(SitemapDocument.PAGES, SitemapDocument.RECIPES, SitemapDocument.USERS)
                .map { "$siteUrl/${it.path}" }
        )
        // Always with the slash, the homepage included. `https://cooknco.eu` and
        // `https://cooknco.eu/` are the same page to a browser, but the first has an empty path
        // and every example in the sitemap protocol has a path — and a parser strict about it
        // abandons the file, which reads as "couldn't read sitemap" with nothing discovered.
        SitemapDocument.PAGES -> renderUrls(STATIC_PAGES.map { Entry(location = "$siteUrl/$it") })
        SitemapDocument.RECIPES -> renderUrls(
            recipeService.findPublicForSitemap(MAX_URLS).map { recipe ->
                Entry(
                    location = "$siteUrl/$RECIPE_VIEW_URL?id=${recipe.id}",
                    lastModified = recipe.modificationDate,
                )
            }
        )
        SitemapDocument.USERS -> renderUrls(
            userService.findPublicForSitemap(MAX_URLS).map { user ->
                // No `lastmod`: the only date a profile carries is the member's last activity,
                // and a page whose stated modification date moves every time somebody signs in
                // teaches a crawler to stop believing the field.
                Entry(location = "$siteUrl/$USER_VIEW_URL?user=${user.id}")
            }
        )
    }

    private class Entry(val location: String, val lastModified: LocalDateTime? = null)

    private fun renderUrls(entries: List<Entry>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<urlset xmlns="$NAMESPACE">""").append('\n')
        entries.forEach { entry ->
            append("  <url>\n")
            append("    <loc>").append(escape(entry.location)).append("</loc>\n")
            entry.lastModified?.let {
                append("    <lastmod>").append(it.atOffset(ZoneOffset.UTC).format(LASTMOD)).append("</lastmod>\n")
            }
            append("  </url>\n")
        }
        append("</urlset>\n")
    }

    private fun renderIndex(locations: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<sitemapindex xmlns="$NAMESPACE">""").append('\n')
        locations.forEach { location ->
            append("  <sitemap>\n")
            append("    <loc>").append(escape(location)).append("</loc>\n")
            append("  </sitemap>\n")
        }
        append("</sitemapindex>\n")
    }

    /**
     * The URLs are built here out of numeric ids, so nothing in them can be hostile today.
     * Escaped anyway, because the sitemap protocol requires it of a `<loc>` and the day a
     * path gains a slug is the day an unescaped `&` silently invalidates the whole document.
     */
    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
