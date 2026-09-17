package com.xavierclavel.cooknco.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * That a cooknco.eu link the app claims lands on the screen it names — and, above all, that
 * the links this app *hands out* are links it can place.
 *
 * The failure this rules out is quiet and one-directional. A shared link the app cannot map
 * is dropped, which is the right answer for a notification and the right answer for a link
 * the browser still holds; but on a *verified* install the system has already taken the link
 * away from the browser by then, so dropping it opens the app on its own home screen — worse
 * than never having claimed the path. The share sheet mints these URLs, so the mapping and
 * the minting have to agree, and nothing but this says so.
 */
class WebRoutesTest {

    @Test
    fun `a shared recipe link routes to the recipe`() {
        assertEquals("recipe/12", WebRoutes.routeForUrl(WebRoutes.recipeUrl(12)))
    }

    @Test
    fun `a shared profile link routes to the profile`() {
        assertEquals("user/3", WebRoutes.routeForUrl(WebRoutes.userUrl(3)))
    }

    /**
     * The scheme is what makes a recipient's system treat the text as a link at all, and
     * what hands it to the app rather than the browser. It went missing once already —
     * "cooknco.eu/recipe?id=1" was what the clipboard used to get.
     */
    @Test
    fun `a shared link is absolute`() {
        assertEquals("https://cooknco.eu/recipe/view?id=1", WebRoutes.recipeUrl(1))
        assertEquals("https://cooknco.eu/user/view?user=1", WebRoutes.userUrl(1))
    }

    /**
     * Each route names its id after what it holds, and the two that do not say `id` are the
     * two that were read as `id` anyway — so every cookbook and ingredient link the website
     * produces was dropped.
     */
    @Test
    fun `a link names its id the way the website does`() {
        assertEquals("cookbook/5", WebRoutes.routeForPath("/cookbook/view?cookbook=5"))
        assertEquals("ingredient/7", WebRoutes.routeForPath("/ingredient/view?ingredient=7"))
        assertEquals("user/3", WebRoutes.routeForPath("/user/view?user=3"))
        assertEquals("recipe/12", WebRoutes.routeForPath("/recipe/view?id=12"))
    }

    /** `?id=` stays accepted, because a notification already sent may carry it. */
    @Test
    fun `a bare id is still accepted`() {
        assertEquals("cookbook/5", WebRoutes.routeForPath("/cookbook/view?id=5"))
        assertEquals("ingredient/7", WebRoutes.routeForPath("/ingredient/view?id=7"))
    }

    /** Both shapes are in circulation — `/recipe/view?id=1` but `/user/view/?user=1`. */
    @Test
    fun `a trailing slash is trimmed rather than matched`() {
        assertEquals("user/3", WebRoutes.routeForUrl("https://cooknco.eu/user/view/?user=3"))
    }

    @Test
    fun `an unplaceable link is dropped rather than guessed at`() {
        // A claimed path with no id: the recipe it names is missing, not zero.
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/recipe/view"))
        // Paths deliberately left to the browser. /oauth/authorize is the consent screen an
        // MCP client sends the user to, and /login is where the app sends people to sign in
        // with Google; opening either in the app is opening it on a screen that does not exist.
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/oauth/authorize?client_id=x"))
        assertNull(WebRoutes.routeForUrl("https://cooknco.eu/login"))
        // Another host claiming our shapes. The platforms only ever hand over a URL that
        // matched the declaration, but the OAuth callback's entry point reaches this too.
        assertNull(WebRoutes.routeForUrl("https://evil.example/recipe/view?id=1"))
        assertNull(WebRoutes.routeForUrl("cooknco://login?code=1"))
        assertNull(WebRoutes.routeForPath(null))
        assertNull(WebRoutes.routeForPath(" "))
    }
}
