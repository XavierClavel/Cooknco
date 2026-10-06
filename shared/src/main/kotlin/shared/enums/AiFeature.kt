package shared.enums

/**
 * What a model was asked to do, on every row of the AI usage history.
 *
 * Every AI feature spends from the same monthly budget and the same per-account daily
 * allowance — one bill, one ceiling — and this is what lets the backoffice say which feature
 * the money went to. A new feature is a case here and a `feature =` at its call to
 * `AiUsageService.record`.
 *
 * Stored by ordinal, like every enum here: append, never reorder.
 */
enum class AiFeature {
    /** A photographed recipe page, read into the editor (`PhotoImportService`). */
    PHOTO_IMPORT,
}
