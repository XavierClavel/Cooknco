import legalEn from '@/locales/legal/en'
import legalFr from '@/locales/legal/fr'
import { getLocale } from '@/scripts/localization'
import type { LegalDocument, LegalDocuments } from '@/locales/legal/shared'

export { legalCopy }
export type { LegalDocument }

/**
 * The privacy policy and the deletion notice, in the language the reader is in.
 *
 * Deliberately not `$t` keys: these two are documents rather than interface copy, and
 * keeping them whole is what lets the two languages be compared section by section. The
 * pages call this inside a `computed`, so switching language rewrites the page the way it
 * rewrites every other one.
 *
 * Named for the copy rather than for `LegalDocument.vue`, which renders it, because the two
 * cannot share a name: a `<legal-document>` tag is camelised to `legalDocument` and matched
 * against the page's own bindings *before* the auto-imported components, so a function
 * called that would be rendered as the component and draw nothing at all — silently, and
 * only at runtime.
 *
 * Anything but French falls back to English, which is also `i18n`'s own fallback.
 */
function legalCopy(kind: keyof LegalDocuments): LegalDocument {
  const documents = getLocale() === 'fr' ? legalFr : legalEn
  return documents[kind]
}
