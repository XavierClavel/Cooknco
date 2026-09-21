package shared.enums

/**
 * What kind of premium an account holds, as the backoffice shows it and filters on it.
 *
 * Derived from the two columns behind it rather than stored: an account is premium for
 * good, premium until a date, or not premium — and a grant that has run out reads as
 * [NONE] again, because the date it lapsed on is a fact about its history and not a state
 * anything should treat as premium.
 */
enum class PremiumStatus {
    /** No grant, or one whose date has passed. */
    NONE,

    /** Premium until a date, which has not come yet. */
    UNTIL,

    /** Premium with no end. */
    FOREVER,
}
