package shared.enums

/** Whether a catalogue ingredient's Open Prices link prices the recipes that use it. */
enum class PriceLinkStatus {
    /** Not linked to any category. */
    NONE,
    /** Linked, and every amount of it converts to what the category is priced per. */
    PRICED,
    /**
     * Linked to a category the bundled table does not list: a refresh dropped it. The link is
     * kept, and prices again if a later refresh brings the category back.
     */
    NOT_IN_TABLE,
    /** Linked to a category priced per litre or per piece, on an ingredient with no density or weight per piece. */
    NOT_CONVERTIBLE,
}
