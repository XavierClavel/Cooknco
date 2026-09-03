package shared.dto

import kotlinx.serialization.Serializable
import shared.enums.LogLevel
import shared.infodto.LogEntryInfo

/**
 * A page of captured server logs.
 *
 * [droppedCount] is how many events the ring buffer has evicted since startup, so the
 * backoffice can tell the operator that older lines are gone rather than absent.
 */
@Serializable
data class LogPage(
    val entries: List<LogEntryInfo>,
    val bufferCapacity: Int,
    val bufferSize: Int,
    val droppedCount: Long,
    val loggers: List<String>,
    val minLevel: LogLevel,
)
