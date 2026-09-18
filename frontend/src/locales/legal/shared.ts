/**
 * The shape the two legal documents are written in, and the address they publish.
 *
 * They are data rather than markup because each one exists twice — once per language — and
 * the two have to keep the same sections in the same order. A section missing from one of
 * them is then a diff anybody can see, instead of a paragraph that quietly exists in French
 * only. `LegalDocument.vue` is the single renderer, so neither copy carries layout.
 */

/** Where a privacy or deletion request is answered. Written into every document from here. */
export const CONTACT_EMAIL = 'contact@cooknco.eu'

/** A list item. `term` is set in bold ahead of the text, for the "what — why" lists. */
export interface LegalBullet {
  term?: string
  text: string
}

export interface LegalSection {
  heading: string
  /** Paragraphs, in order. */
  body?: string[]
  bullets?: LegalBullet[]
  /** Paragraphs printed after the bullets, for the "and this is why" line. */
  after?: string[]
}

export interface LegalDocument {
  title: string
  /** Rendered as-is, so it is written the way that language writes a date. */
  updated: string
  intro: string[]
  sections: LegalSection[]
}

/** The two documents, per language. Both languages implement this, so neither can omit one. */
export interface LegalDocuments {
  privacy: LegalDocument
  deletion: LegalDocument
}
