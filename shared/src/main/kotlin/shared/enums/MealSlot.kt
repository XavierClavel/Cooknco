package shared.enums

/**
 * Which meal of the day a planned dish is for.
 *
 * Declared in the order a day is eaten in, which is the order the planner lists them: the
 * backend sorts on [ordinal], so a new slot goes where it falls in the day rather than at the
 * end. Stored by name, so adding or reordering one never rewrites a row.
 */
enum class MealSlot {
    BREAKFAST,
    LUNCH,
    SNACK,
    DINNER,
}
