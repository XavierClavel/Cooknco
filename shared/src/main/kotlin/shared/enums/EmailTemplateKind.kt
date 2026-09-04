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
    ;

    /**
     * Packaged under `shared/src/main/resources`, so the same file is in the backend jar,
     * which shows it in the backoffice, and in the mail-service jar, which sends it.
     */
    fun resource(locale: Locale) = "/emails/${key}_${locale.name.lowercase()}.html"

    /**
     * The address the mail's one link points at.
     *
     * Defined here rather than at each call site because the backoffice previews the link
     * and mail-service sends it, and a preview pointing somewhere else would be a lie.
     */
    fun link(frontendUrl: String, token: String) = when (this) {
        ACCOUNT_VERIFICATION -> "$frontendUrl/user/verify?token=$token"
        PASSWORD_RESET -> "$frontendUrl/password/reset/new?token=$token"
    }

    companion object {
        /** The built-in kind a key names, or null when an operator added it. */
        fun of(key: String): EmailTemplateKind? = entries.find { it.key == key }
    }
}

/**
 * The names a wording may fill in as `{{name}}`.
 *
 * Its own object rather than constants on [EmailTemplateKind]: an enum entry cannot read
 * its own companion, which is not yet initialised when the entries are built.
 */
object MailPlaceholder {
    /** The one placeholder the built-in mails use: the thing the reader has to click. */
    const val LINK = "link"
}
