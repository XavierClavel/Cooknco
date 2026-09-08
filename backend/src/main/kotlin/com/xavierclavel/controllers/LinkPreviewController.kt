package com.xavierclavel.controllers

import com.xavierclavel.services.LinkPreview
import com.xavierclavel.services.LinkPreviewService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getIdQueryParam
import shared.enums.Locale
import shared.utils.URL.COOKBOOK_VIEW_URL
import shared.utils.URL.INGREDIENT_VIEW_URL
import shared.utils.URL.RECIPE_VIEW_URL
import shared.utils.URL.USER_VIEW_URL
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import org.koin.java.KoinJavaComponent.inject

/**
 * Serves the HTML document for the public app routes people share, with the shared entity's
 * own title, description and picture in its `og:` tags.
 *
 * Unauthenticated by design, and unauthenticated in effect: [LinkPreviewService] looks every
 * entity up as an anonymous visitor, so nothing here can describe something the public may
 * not already open.
 *
 * `frontend/nginx.conf` is what routes these paths here instead of to `index.html`; the app
 * itself is unchanged, and a human following the link gets the same SPA as before. Both the
 * bare and trailing-slash forms are declared because the app builds both
 * (`/recipe/view?id=1` but `/user/view/?user=1`) and Ktor does not treat them as one.
 */
object LinkPreviewController: Controller() {
    private val linkPreviewService: LinkPreviewService by inject(LinkPreviewService::class.java)

    override fun Route.routes() {
        preview(RECIPE_VIEW_URL) { linkPreviewService.recipePreview(getIdQueryParam("id"), it) }
        preview(USER_VIEW_URL) { linkPreviewService.userPreview(getIdQueryParam("user"), it) }
        preview(COOKBOOK_VIEW_URL) { linkPreviewService.cookbookPreview(getIdQueryParam("cookbook"), it) }
        preview(INGREDIENT_VIEW_URL) { linkPreviewService.ingredientPreview(getIdQueryParam("ingredient"), it) }
    }

    private fun Route.preview(route: String, describe: RoutingContext.(Locale) -> LinkPreview) {
        listOf(route, "$route/").forEach { path ->
            get(path) { respondPreview(describe(previewLocale())) }
            // nginx answered these paths from disk before this feature, where a HEAD got a
            // 200 like any other static file. Ktor answers 405 for a verb a route does not
            // declare, and unfurlers and uptime monitors do probe with HEAD, so the 405
            // would cost the very preview this exists for. Ktor's AutoHeadResponse is
            // application-scoped, and giving every API route a HEAD is not this feature's
            // business — hence the explicit pairing. Same handler, so a HEAD reports the
            // same status a GET would, 502 included.
            head(path) { respondPreview(describe(previewLocale())) }
        }
    }

    private suspend fun RoutingContext.respondPreview(preview: LinkPreview) {
        val document = linkPreviewService.renderDocument(preview)
        if (document == null) {
            // nginx answers this with the static index.html (`error_page` in
            // frontend/nginx.conf), so the visitor still gets a working app — only the
            // entity's own tags are missing, and only while the shell is unreadable.
            return call.respondText("App shell unavailable", status = HttpStatusCode.BadGateway)
        }
        // Long enough to absorb a crawler fetching the same link repeatedly, short enough
        // that the asset names in the shell cannot outlive a frontend deploy by much.
        call.response.header(HttpHeaders.CacheControl, "public, max-age=60")
        call.respondText(document, ContentType.Text.Html)
    }

    /**
     * Which wording the fallback description uses. Tolerant on purpose — an unreadable
     * `?locale=` in a shared link is no reason to answer a crawler with a 400, unlike the
     * API's [com.xavierclavel.utils.getLocale].
     */
    private fun RoutingContext.previewLocale(): Locale =
        call.request.queryParameters["locale"]
            ?.let { param -> enumValues<Locale>().find { it.name.equals(param, ignoreCase = true) } }
            ?: call.request.headers[HttpHeaders.AcceptLanguage]
                ?.takeIf { it.startsWith("fr", ignoreCase = true) }
                ?.let { Locale.FR }
            ?: Locale.EN
}
