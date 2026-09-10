package shared.utils

object URL {
    const val USER_URL = "api/v1/user"
    const val NOTIFICATION_URL = "api/v1/notification"
    const val INGREDIENT_URL = "api/v1/ingredient"
    const val UNIT_URL = "api/v1/unit"
    const val RECIPE_URL = "api/v1/recipe"
    const val RECIPE_NOTES_URL = "api/v1/recipe-notes"
    const val LIKE_URL = "api/v1/like"
    const val DASHBOARD_URL = "api/v1/dashboard"
    const val COOKBOOK_URL = "api/v1/cookbook"
    const val FOLLOW_URL = "api/v1/follow"
    const val AUTH_URL = "api/v1/auth"
    const val IMAGE_URL = "image"
    const val EXPORT_URL = "api/v1/export"
    const val TEST_URL = "api/v1/test"
    const val HEALTH_URL = "api/v1/health"
    const val REPORT_URL = "api/v1/report"
    const val ADMIN_URL = "api/v1/admin"

    /**
     * What a mobile build asks before it lets anyone in.
     *
     * Unauthenticated, and it has to stay that way: the check runs at launch, before
     * there is a session, and a build old enough to be blocked is exactly the one whose
     * sign-in flow we can least rely on.
     */
    const val APP_VERSION_URL = "api/v1/app-version"

    /**
     * The Model Context Protocol endpoint, for MCP clients registered against this server.
     *
     * Outside `api/` because `/mcp` is where MCP clients look, and it is what
     * `frontend/nginx.conf` publishes. See [com.xavierclavel.controllers.McpController].
     */
    const val MCP_URL = "mcp"

    /**
     * The public app routes whose HTML document the backend renders itself, so that a shared
     * link carries the entity's own title, description and image in its `og:` tags.
     *
     * These are *app* paths, not API paths: `frontend/nginx.conf` sends them here instead of
     * to `index.html`, and the response is that same shell with the head block swapped out
     * (`LinkPreviewController`). Crawlers never run the SPA's JavaScript, so tags the app sets
     * after mounting come too late for them.
     *
     * The id is a query parameter rather than a path segment because that is the shape the app
     * already builds and users copy out of the address bar (`toViewRecipe` and friends in
     * `frontend/src/scripts/common.ts`).
     */
    const val RECIPE_VIEW_URL = "recipe/view"
    const val USER_VIEW_URL = "user/view"
    const val COOKBOOK_VIEW_URL = "cookbook/view"
    const val INGREDIENT_VIEW_URL = "ingredient/view"

    /**
     * Where mail-service reads the wordings an operator saved.
     *
     * Deliberately outside `api/`: `frontend/nginx.conf` proxies only `/api/`, `/image/`
     * and the log stream, so nothing under this prefix is reachable from the internet, and
     * the only callers are pods on the cluster network.
     */
    const val INTERNAL_MAIL_TEMPLATES_URL = "internal/email-templates"
}