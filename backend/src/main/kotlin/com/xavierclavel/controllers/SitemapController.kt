package com.xavierclavel.controllers

import com.xavierclavel.services.SitemapService
import com.xavierclavel.services.SitemapService.SitemapDocument
import com.xavierclavel.utils.Controller
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.koin.java.KoinJavaComponent.inject

/**
 * `GET /sitemap.xml`, the index, and the three documents it names — the list of pages worth
 * indexing, one document per kind of page.
 *
 * Answered by the backend rather than shipped with the SPA because it is a view of the
 * catalogue: a static file would name the recipes that existed when the frontend was last
 * built, which is the wrong set within a day of shipping.
 *
 * Unauthenticated, and it has to be — a crawler has no account. Nothing here describes
 * anything the anonymous visitor could not already open; see [SitemapService].
 *
 * `frontend/nginx.conf` publishes exactly these paths. Note that `robots.txt` is *not*
 * here: it never changes, so it ships as a static file with the SPA and nginx serves it off
 * disk. Both had been answering with the app's `index.html` — `try_files` catches every path
 * that is not a real file — which is why a 200 from either one proves nothing on its own.
 */
object SitemapController: Controller() {
    private val sitemapService: SitemapService by inject(SitemapService::class.java)

    override fun Route.routes() {
        SitemapDocument.entries.forEach { document ->
            get("/${document.path}") { respondSitemap(document) }
            // Declared for the same reason LinkPreviewController declares it, and learned the same
            // way: nginx answered this path from disk before there was a route here, where a HEAD
            // got a 200 like any other file. Ktor answers 405 for a verb a route does not declare,
            // and Google probes a submitted sitemap with HEAD before reading it — so the 405 was
            // reported as "couldn't read sitemap" while a browser fetched the document perfectly.
            // Same handler, so a HEAD reports exactly what a GET would.
            head("/${document.path}") { respondSitemap(document) }
        }
    }

    private suspend fun RoutingContext.respondSitemap(document: SitemapDocument) {
        // Matches the service's own regeneration window: a crawler that revisits sooner
        // gets the copy it already had, and one that revisits later gets a fresh document.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
        call.respondText(sitemapService.sitemap(document), ContentType.Text.Xml)
    }
}
