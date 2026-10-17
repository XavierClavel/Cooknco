package shared.utils

import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.enums.MailPlaceholder

/**
 * Reading and filling in mail templates.
 *
 * This lives in `shared` because both halves of the feature have to agree on it exactly:
 * the backoffice renders its preview in the backend, and mail-service renders what it
 * actually sends. A template the two filled in differently would make the preview a lie.
 */
object EmailTemplates {
    /** Longest wording the backoffice accepts, and so the width of the columns holding one. */
    const val MAX_SUBJECT_LENGTH = 255
    const val MAX_BODY_LENGTH = 16383

    /** A key an operator may give a template of their own: what a path segment can carry. */
    val KEY_FORMAT = Regex("[a-z][a-z0-9_]{2,62}")

    private val PLACEHOLDER = Regex("\\{\\{([A-Za-z0-9_]+)}}")

    /** Subject and body of the wording packaged in the jar. */
    fun packaged(kind: EmailTemplateKind, locale: Locale): Pair<String, String> {
        val resource = kind.resource(locale)
        val text = javaClass.getResource(resource)?.readText()
            ?: error("Mail template $resource is missing from the jar")
        return parse(text)
    }

    /**
     * Splits a packaged template, which carries its subject on the first line so that one
     * file is one whole mail. Saved templates keep the two apart and never come through here.
     */
    fun parse(text: String): Pair<String, String> {
        val lines = text.lines()
        val subject = lines.first().substringAfter("<subject>").substringBefore("</subject>")
        return subject to lines.drop(1).joinToString("\n")
    }

    /**
     * Substitutes `{{name}}` for each value given.
     *
     * A placeholder with no value is left as it is rather than blanked: an operator seeing
     * `{{foo}}` in a preview is being told nothing fills it, which for a template they added
     * themselves is the truth worth knowing.
     */
    fun render(text: String, values: Map<String, String>): String =
        values.entries.fold(text) { rendered, (name, value) -> rendered.replace("{{$name}}", value) }

    /** Every `{{name}}` a wording refers to, in the order they first appear. */
    fun placeholdersIn(text: String): List<String> =
        PLACEHOLDER.findAll(text).map { it.groupValues[1] }.distinct().toList()

    /**
     * What to fill a preview or a test mail with.
     *
     * Only a built-in kind has values to offer, since only a built-in is sent by code that
     * knows what they mean. No token here is a real one — a preview link has to be inert,
     * and a test mail goes to whoever asked for it, not to an account. That matters most
     * for the unsubscribe link, which is the one sample that would otherwise *do* something:
     * signed for nobody, it is refused rather than switching an operator's mails off from
     * under them.
     */
    fun sampleValues(kind: EmailTemplateKind?, frontendUrl: String): Map<String, String> {
        if (kind == null) return emptyMap()
        return buildMap {
            when (kind) {
                EmailTemplateKind.NEW_RECIPE -> {
                    put(MailPlaceholder.LINK, kind.link(frontendUrl, SAMPLE_RECIPE_ID))
                    put(MailPlaceholder.USERNAME, SAMPLE_USERNAME)
                    put(MailPlaceholder.TITLE, SAMPLE_TITLE)
                }
                else -> put(MailPlaceholder.LINK, kind.link(frontendUrl, SAMPLE_TOKEN))
            }
            // Exactly the mails that declare it, so what a preview fills in and what a send
            // fills in stay the same set
            if (!kind.isTransactional) {
                put(MailPlaceholder.UNSUBSCRIBE, EmailTemplateKind.unsubscribeLink(frontendUrl, SAMPLE_TOKEN))
            }
        }
    }

    private const val SAMPLE_TOKEN = "sample-token"
    private const val SAMPLE_RECIPE_ID = "0"
    private const val SAMPLE_USERNAME = "a-cook"
    private const val SAMPLE_TITLE = "Tarte aux pommes"
}
