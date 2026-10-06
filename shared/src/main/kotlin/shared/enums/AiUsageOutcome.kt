package shared.enums

/**
 * How one AI request that reached the model ended. Every one of them was billed, which is
 * why each is recorded whatever it came to.
 *
 * Stored by ordinal, like every enum here: append, never reorder.
 */
enum class AiUsageOutcome {
    /** The model's answer was used — for a photo import, a recipe handed to the editor. */
    READ,

    /** The model answered, and had nothing to give — no recipe on the photographed page. */
    NOTHING_READ,

    /** The model answered with something unusable. Refunded to the user's day, not the month. */
    FAILED,
}
