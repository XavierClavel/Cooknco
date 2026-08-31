package shared.enums

/**
 * Bucket size for the backoffice trend charts.
 *
 * The SQL fragments are held here rather than built from request input, so the
 * date_trunc unit and interval can never come from an untrusted string.
 */
enum class TimeGranularity(
    val sqlUnit: String,
    val sqlInterval: String,
    val defaultBuckets: Int,
) {
    DAY("day", "1 day", 30),
    WEEK("week", "1 week", 12),
    MONTH("month", "1 month", 12),
}
