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
     * Where mail-service reads the wordings an operator saved.
     *
     * Deliberately outside `api/`: `frontend/nginx.conf` proxies only `/api/`, `/image/`
     * and the log stream, so nothing under this prefix is reachable from the internet, and
     * the only callers are pods on the cluster network.
     */
    const val INTERNAL_MAIL_TEMPLATES_URL = "internal/email-templates"
}