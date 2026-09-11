package shared.enums

/**
 * A mail the application itself knows how to send.
 *
 * Stored templates are addressed by [key] rather than by enum name, so a kind an operator
 * added from the backoffice is nothing but rows. The entries here are the ones backend code
 * actually emits, which is what earns them their privileges: they declare the placeholders
 * their wording may use, they have a wording packaged in the jar to fall back on, and they
 * cannot be removed.
 */
enum class EmailTemplateKind(val key: String, val placeholders: List<String>) {
    ACCOUNT_VERIFICATION("account_verification", listOf(MailPlaceholder.LINK)),
    PASSWORD_RESET("password_reset", listOf(MailPlaceholder.LINK)),
    NEW_RECIPE(
        "new_recipe",
        listOf(
            MailPlaceholder.LINK,
            MailPlaceholder.USERNAME,
            MailPlaceholder.TITLE,
            MailPlaceholder.UNSUBSCRIBE,
        ),
    ),
    ;

    /**
     * Whether the mail goes out whatever the recipient's notification setting says.
     *
     * True for the ones they asked for by using the app — an address to confirm, a password
     * to reset — which are the answer to something they just did. A notification mail is the
     * opposite: it exists because somebody *else* did something, so it is theirs to switch
     * off. Turning off the former would lock people out of their own accounts.
     */
    val isTransactional: Boolean get() = this != NEW_RECIPE

    /**
     * Packaged under `shared/src/main/resources`, so the same file is in the backend jar,
     * which shows it in the backoffice, and in the mail-service jar, which sends it.
     */
    fun resource(locale: Locale) = "/emails/${key}_${locale.name.lowercase()}.html"

    /**
     * The address the mail's link points at.
     *
     * Defined here rather than at each call site because the backoffice previews the link
     * and mail-service sends it, and a preview pointing somewhere else would be a lie.
     *
     * [target] is whatever the kind's link is *about* — a one-use token for the mails that
     * carry one, the recipe id for the one that announces a recipe.
     */
    fun link(frontendUrl: String, target: String) = when (this) {
        ACCOUNT_VERIFICATION -> "$frontendUrl/user/verify?token=$target"
        PASSWORD_RESET -> "$frontendUrl/password/reset/new?token=$target"
        NEW_RECIPE -> "$frontendUrl/recipe/view?id=$target"
    }

    companion object {
        /** The built-in kind a key names, or null when an operator added it. */
        fun of(key: String): EmailTemplateKind? = entries.find { it.key == key }

        /**
         * Where the way out of these mails points.
         *
         * Not per kind, unlike [link]: it is the same page whatever the mail announced, and
         * only the mails somebody can say no to carry one at all — [isTransactional] is what
         * decides. Here for the reason [link] is: the backoffice previews this link and
         * mail-service sends it, so a preview that built it differently would be a lie.
         *
         * [token] names the account and is signed, so the page needs no session — see
         * `UnsubscribeService`.
         */
        fun unsubscribeLink(frontendUrl: String, token: String) = "$frontendUrl/user/unsubscribe?token=$token"
    }
}

/**
 * The names a wording may fill in as `{{name}}`.
 *
 * Its own object rather than constants on [EmailTemplateKind]: an enum entry cannot read
 * its own companion, which is not yet initialised when the entries are built.
 */
object MailPlaceholder {
    /** The thing the reader has to click. Every built-in mail has one. */
    const val LINK = "link"

    /** Whoever caused the mail — never the recipient. */
    const val USERNAME = "username"

    /** The title of the recipe the mail is about. */
    const val TITLE = "title"

    /**
     * Where the reader turns these mails off, without signing in first.
     *
     * Declared by every kind that is not [EmailTemplateKind.isTransactional], which is what
     * makes the backoffice refuse a notification wording that drops it: a mail somebody
     * else's doing put in an inbox has to carry its own way out. A transactional mail
     * declares it nowhere — there is nothing to unsubscribe from a password reset, and
     * offering it would be an offer we could not keep.
     */
    const val UNSUBSCRIBE = "unsubscribe"
}
