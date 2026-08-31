package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.LogLevel

/**
 * One captured log event. [sequence] is monotonically increasing per server process and
 * lets a client resume a tail without re-reading what it already has.
 */
@Serializable
data class LogEntryInfo(
    val sequence: Long,
    val timestamp: Long,
    val level: LogLevel,
    val logger: String,
    val thread: String,
    val message: String,
    val stackTrace: String? = null,
)
