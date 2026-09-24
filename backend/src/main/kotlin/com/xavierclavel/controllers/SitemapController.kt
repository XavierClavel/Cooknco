package com.xavierclavel.controllers

import com.xavierclavel.services.SitemapService
import com.xavierclavel.utils.Controller
import shared.utils.URL.SITEMAP_URL
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

/**
 * `GET /sitemap.xml`, the list of pages worth indexing.
 *
 * Answered by the backend rather than shipped with the SPA because it is a view of the
 * catalogue: a static file would name the recipes that existed when the frontend was last
 * built, which is the wrong set within a day of shipping.
 *
 * Unauthenticated, and it has to be — a crawler has no account. Nothing here describes
 * anything the anonymous visitor could not already open; see [SitemapService].
 *
 * `frontend/nginx.conf` publishes exactly this one path. Note that `robots.txt` is *not*
 * here: it never changes, so it ships as a static file with the SPA and nginx serves it off
 * disk. Both had been answering with the app's `index.html` — `try_files` catches every path
 * that is not a real file — which is why a 200 from either one proves nothing on its own.
 */
object SitemapController: Controller() {
    private val sitemapService: SitemapService by inject(SitemapService::class.java)

    override fun Route.routes() {
        get("/$SITEMAP_URL") {
            // Matches the service's own regeneration window: a crawler that revisits sooner
            // gets the copy it already had, and one that revisits later gets a fresh document.
            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respondText(sitemapService.sitemap(), ContentType.Text.Xml)
        }
    }
}
