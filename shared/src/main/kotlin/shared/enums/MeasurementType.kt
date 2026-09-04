package shared.enums

/**
 * The physical quantity a unit measures. An ingredient declares which of these it can be expressed
 * in, and each [AmountUnit] belongs to exactly one.
 */
enum class MeasurementType {
    /** No amount at all ("salt, to taste"). */
    NONE,
    /** Countable pieces (2 eggs). */
    AMOUNT,
    WEIGHT,
    VOLUME,
}
