package shared.utils

import shared.enums.EmailTemplateKind
import shared.enums.Locale
import shared.enums.MailPlaceholder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wordings packaged in the jar, held to the rule the backoffice holds a saved one to.
 *
 * `EmailTemplateService.validate` refuses an *edited* wording that drops a placeholder its
 * kind declares, and a notification mail with no unsubscribe link in it is exactly what that
 * is there to stop. Nothing checks the packaged copy the same way — it is a file, not a save
 * — so this does: the floor every install falls back to has to satisfy the rule too, or the
 * one mail an operator never touched would be the one that ships broken.
 */
class EmailTemplatesTest {

    @Test
    fun `every packaged wording fills in what its kind declares`() {
        EmailTemplateKind.entries.forEach { kind ->
            Locale.entries.forEach { locale ->
                val (subject, body) = EmailTemplates.packaged(kind, locale)
                assertTrue(subject.isNotBlank(), "${kind.key}/$locale has no subject")
                val used = EmailTemplates.placeholdersIn(body)
                kind.placeholders.forEach {
                    assertTrue(it in used, "${kind.key}/$locale never fills in {{$it}}")
                }
            }
        }
    }

    /**
     * The other half of the same rule: a mail that goes out whatever the setting says must
     * not offer a way to stop it, since nothing would honour the promise.
     */
    @Test
    fun `only the mails that can be stopped say how`() {
        EmailTemplateKind.entries.forEach { kind ->
            assertEquals(
                !kind.isTransactional,
                MailPlaceholder.UNSUBSCRIBE in kind.placeholders,
                "${kind.key} declares an unsubscribe link it should not, or drops one it should have",
            )
            Locale.entries.forEach { locale ->
                val body = EmailTemplates.packaged(kind, locale).second
                assertEquals(
                    !kind.isTransactional,
                    MailPlaceholder.UNSUBSCRIBE in EmailTemplates.placeholdersIn(body),
                    "${kind.key}/$locale",
                )
            }
        }
    }

    /** A preview fills in everything a wording is allowed to name, or it is not a preview. */
    @Test
    fun `the sample values cover every declared placeholder`() {
        EmailTemplateKind.entries.forEach { kind ->
            val values = EmailTemplates.sampleValues(kind, "https://cooknco.eu")
            assertEquals(kind.placeholders.toSet(), values.keys, kind.key)
        }
    }
}
