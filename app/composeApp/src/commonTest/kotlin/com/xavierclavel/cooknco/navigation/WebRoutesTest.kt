package com.xavierclavel.cooknco.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a share button copies has to be what a tapped link can be placed by, and what the two
 * platforms claim.
 *
 * The round trip below is the point of the file: the share buttons used to build their own
 * address — `cooknco.eu/recipe?id=1` — which the website does not serve, the manifest and the
 * association file do not claim, and [WebRoutes] could not place. Nothing failed; the link
 * simply opened a generic page in a browser.
 */
class WebRoutesTest {

    @Test
    fun `every shared address is placed back on the screen it names`() {
        val expected = mapOf(
            WebRoutes.Shareable.RECIPE to "recipe/12",
            WebRoutes.Shareable.USER to "user/12",
            WebRoutes.Shareable.COOKBOOK to "cookbook/12",
            WebRoutes.Shareable.INGREDIENT to "ingredient/12",
        )
        WebRoutes.Shareable.entries.forEach { shareable ->
            val url = WebRoutes.urlFor(shareable, 12)
            assertEquals(expected.getValue(shareable), WebRoutes.routeForUrl(url), url)
        }
    }

    @Test
    fun `a shared address carries the scheme and host the platforms claim`() {
        WebRoutes.Shareable.entries.forEach { shareable ->
            val url = WebRoutes.urlFor(shareable, 1)
            // Neither claim covers http, and the ingress answers it with a 404 rather than a
            // redirect, so a share that loses the scheme is a link that reaches nothing.
            assertTrue(url.startsWith("https://${WebRoutes.HOST}/"), url)
            // `*/view`, which is what the manifest's pathPrefix entries and the association
            // file's components list. A path that is not one of those opens no app.
            assertTrue(url.substringBefore('?').endsWith("/view"), url)
        }
    }

    @Test
    fun `the paths and parameters are the ones the backend stores`() {
        assertEquals("https://cooknco.eu/recipe/view?id=12", WebRoutes.urlFor(WebRoutes.Shareable.RECIPE, 12))
        // `user`, not `id` — NotificationService stores `/user/view?user=…`
        assertEquals("https://cooknco.eu/user/view?user=12", WebRoutes.urlFor(WebRoutes.Shareable.USER, 12))
        assertEquals("https://cooknco.eu/cookbook/view?id=12", WebRoutes.urlFor(WebRoutes.Shareable.COOKBOOK, 12))
        assertEquals("https://cooknco.eu/ingredient/view?id=12", WebRoutes.urlFor(WebRoutes.Shareable.INGREDIENT, 12))
    }

    @Test
    fun `a stored notification path lands on the same screen as the link`() {
        assertEquals("recipe/12", WebRoutes.routeForPath("/recipe/view?id=12"))
        assertEquals("user/7", WebRoutes.routeForPath("/user/view?user=7"))
        // Both shapes are in circulation
        assertEquals("user/7", WebRoutes.routeForPath("/user/view/?user=7"))
        assertEquals("recipe/12", WebRoutes.routeForUrl("https://cooknco.eu/recipe/view/?id=12"))
    }

    @Test
    fun `the path the cook timer mints is placed too`() {
        assertEquals("recipe/12/cook", WebRoutes.routeForPath("/recipe/cook?id=12"))
    }

    @Test
    fun `what cannot be placed is dropped rather than guessed at`() {
        // The shape the share buttons used to copy. It must not resolve: were it to, the app
        // would open on a link the website answers with its generic shell.
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/recipe?id=12"))
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/user?id=12"))
        // The id is what the route is made of, so a path without one is nothing to open
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/recipe/view"))
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/user/view?id=12"))
        // Another host, and a claim the app does not make
        assertNull(WebRoutes.routeForUrl("https://example.test/recipe/view?id=12"))
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/oauth/authorize?client_id=1"))
        // The OAuth callback reaches the same entry point, and is not a web address
        assertNull(WebRoutes.routeForUrl("cooknco://login?token=abc"))
        assertNull(WebRoutes.routeForPath(null))
        assertNull(WebRoutes.routeForPath(""))
    }
}
