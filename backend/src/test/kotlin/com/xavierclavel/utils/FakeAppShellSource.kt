package main.com.xavierclavel.utils

import com.xavierclavel.services.AppShellSource

/**
 * Stands in for the nginx that serves the built SPA, which no test has.
 *
 * The shell mirrors the shape of `frontend/index.html` — a default head block between the
 * two markers, and content on either side of it — so that the injection is exercised against
 * the real structure rather than a bare string.
 */
class FakeAppShellSource: AppShellSource {

    /** What a fetch returns. Set to null to exercise an unreadable shell. */
    var html: String? = SHELL

    override suspend fun fetch(): String? = html

    fun reset() {
        html = SHELL
    }

    companion object {
        /** Outside the markers, so a test can prove the rest of the document survives. */
        const val OUTSIDE_HEAD = """<link rel="icon" href="/favicon.ico" />"""
        const val OUTSIDE_BODY = """<div id="app"></div>"""

        /** Inside the markers, so a test can prove the default block is replaced, not added to. */
        const val DEFAULT_TITLE = "Cook&amp;Co"

        val SHELL = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                $OUTSIDE_HEAD
                <!--preview:start-->
                <title>$DEFAULT_TITLE</title>
                <meta property="og:title" content="$DEFAULT_TITLE">
                <meta property="og:image" content="https://cooknco.eu/og-default.png">
                <!--preview:end-->
            </head>
            <body>
                $OUTSIDE_BODY
                <script type="module" src="/src/main.ts"></script>
            </body>
            </html>
        """.trimIndent()

        /** A shell built before the markers existed, for the fallback insertion path. */
        val SHELL_WITHOUT_MARKERS = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                $OUTSIDE_HEAD
            </head>
            <body>$OUTSIDE_BODY</body>
            </html>
        """.trimIndent()
    }
}
