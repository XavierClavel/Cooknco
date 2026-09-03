package shared.enums

/** Ordering of the backoffice image table. Size first, since the table exists to reclaim space. */
enum class ImageSort {
    SIZE_DESCENDING,
    SIZE_ASCENDING,
    DATE_DESCENDING,
    DATE_ASCENDING,
    NAME_ASCENDING,
}
