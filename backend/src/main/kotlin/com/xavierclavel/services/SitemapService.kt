package com.xavierclavel.services

import com.xavierclavel.utils.Configuration
import shared.utils.URL.RECIPE_VIEW_URL
import shared.utils.URL.USER_VIEW_URL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/**
 * The sitemap a search engine reads to find the pages worth indexing.
 *
 * It exists because nothing on this site links to a recipe from anywhere a crawler can reach:
 * every listing page is behind the login, so without this the public recipe pages are
 * orphans — reachable only by guessing an id, which no crawler does. The landing page links
 * to a handful of them; this names all of them.
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
         * degrade — a search engine rejects the whole file — so the list is cut rather than
         * allowed to grow past it. Reaching this is the signal to split into a sitemap index,
         * which is a change to make when it is needed and not before.
         */
        const val MAX_URLS = 50_000

        /** How long a generated document is served before the catalogue is read again. */
        const val TTL_MILLIS = 60 * 60 * 1000L

        private val LASTMOD: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

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

    private class Cached(val xml: String, val builtAt: Long)

    private val cached = AtomicReference<Cached?>(null)

    /**
     * The document, generated at most once per [TTL_MILLIS].
     *
     * Cached because the endpoint is public and unauthenticated, and generating it walks two
     * tables: a crawler asking for it hourly is the intended traffic, but nothing stops
     * anyone else asking for it in a loop.
     */
    fun sitemap(now: Long = System.currentTimeMillis()): String {
        cached.get()?.takeIf { now - it.builtAt < TTL_MILLIS }?.let { return it.xml }
        return build().also { cached.set(Cached(it, now)) }
    }

    /** Drops the cached document, so the next request rebuilds it. */
    fun invalidate() = cached.set(null)

    private fun build(): String {
        val siteUrl = configuration.frontend.url.trimEnd('/')
        // Always with the slash, the homepage included. `https://cooknco.eu` and
        // `https://cooknco.eu/` are the same page to a browser, but the first has an empty path
        // and every example in the sitemap protocol has a path — and this is the document's
        // *first* entry, so a parser strict about it abandons the file before reaching a single
        // recipe. That reads as "couldn't read sitemap" with nothing discovered, which is a much
        // worse symptom than one dropped URL.
        val pages = STATIC_PAGES.map { Entry(location = "$siteUrl/$it") }
        val budget = MAX_URLS - pages.size

        // Recipes next, and given the whole remaining budget before members are considered: they
        // are the pages that answer a search, and a profile is worth listing only once they fit.
        val recipes = recipeService.findPublicForSitemap(budget).map { recipe ->
            Entry(
                location = "$siteUrl/$RECIPE_VIEW_URL?id=${recipe.id}",
                lastModified = recipe.modificationDate,
            )
        }
        val remaining = budget - recipes.size
        val users =
            if (remaining <= 0) emptyList()
            else userService.findPublicForSitemap(remaining).map { user ->
                // No `lastmod`: the only date a profile carries is the member's last activity,
                // and a page whose stated modification date moves every time somebody signs in
                // teaches a crawler to stop believing the field.
                Entry(location = "$siteUrl/$USER_VIEW_URL?user=${user.id}")
            }

        return render(pages + recipes + users)
    }

    private class Entry(val location: String, val lastModified: LocalDateTime? = null)

    private fun render(entries: List<Entry>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""").append('\n')
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
