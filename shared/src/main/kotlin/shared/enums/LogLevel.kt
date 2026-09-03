package shared.enums

/**
 * Severity of a captured server log line, ordered from least to most severe so that
 * log queries can filter on "at least this level".
 */
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ;

    fun isAtLeast(other: LogLevel) = this.ordinal >= other.ordinal
}
