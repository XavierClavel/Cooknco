package com.xavierclavel.logging

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import shared.dto.LogPage
import shared.enums.LogLevel
import shared.infodto.LogEntryInfo

/**
 * Bounded in-memory store of the most recent server log events, so that the admin
 * backoffice can read and tail logs without a mounted log volume.
 *
 * Fed by [RingBufferAppender] from arbitrary logging threads, so every mutation is
 * guarded. Nothing here may log: doing so would re-enter the appender.
 */
object LogBuffer {
    const val DEFAULT_CAPACITY = 5_000

    /** Upper bound on how many entries a single query may return. */
    const val MAX_PAGE_SIZE = 1_000

    private val lock = Any()
    private val entries = ArrayDeque<LogEntryInfo>()

    private var capacity = DEFAULT_CAPACITY
    private var sequence = 0L
    private var dropped = 0L

    /**
     * Live feed for the SSE tail. A slow consumer loses the oldest buffered events rather
     * than back-pressuring the logging thread — the ring buffer is the source of truth.
     */
    private val _stream = MutableSharedFlow<LogEntryInfo>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val stream: SharedFlow<LogEntryInfo> = _stream

    fun setCapacity(newCapacity: Int) = synchronized(lock) {
        capacity = newCapacity.coerceAtLeast(1)
        trimToCapacity()
    }

    fun append(
        timestamp: Long,
        level: LogLevel,
        logger: String,
        thread: String,
        message: String,
        stackTrace: String?,
    ) {
        val entry = synchronized(lock) {
            val entry = LogEntryInfo(
                sequence = ++sequence,
                timestamp = timestamp,
                level = level,
                logger = logger,
                thread = thread,
                message = message,
                stackTrace = stackTrace,
            )
            entries.addLast(entry)
            trimToCapacity()
            entry
        }
        _stream.tryEmit(entry)
    }

    /**
     * @param sinceSequence when set, only entries strictly newer than this sequence, so a
     *   client can poll for what it has not seen yet
     * @return matching entries in chronological order, truncated to the [limit] newest
     */
    fun query(
        minLevel: LogLevel = LogLevel.TRACE,
        logger: String? = null,
        search: String? = null,
        sinceSequence: Long? = null,
        limit: Int = 200,
    ): LogPage {
        val cappedLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        synchronized(lock) {
            val matching = entries.filter { it.matches(minLevel, logger, search, sinceSequence) }
            return LogPage(
                entries = if (matching.size <= cappedLimit) matching else matching.subList(matching.size - cappedLimit, matching.size),
                bufferCapacity = capacity,
                bufferSize = entries.size,
                droppedCount = dropped,
                loggers = entries.map { it.logger }.distinct().sorted(),
                minLevel = minLevel,
            )
        }
    }

    fun countAtLeast(level: LogLevel): Int = synchronized(lock) {
        entries.count { it.level.isAtLeast(level) }
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        dropped = 0
    }

    private fun trimToCapacity() {
        while (entries.size > capacity) {
            entries.removeFirst()
            dropped++
        }
    }

    private fun LogEntryInfo.matches(
        minLevel: LogLevel,
        logger: String?,
        search: String?,
        sinceSequence: Long?,
    ): Boolean {
        if (!level.isAtLeast(minLevel)) return false
        if (sinceSequence != null && sequence <= sinceSequence) return false
        if (!logger.isNullOrBlank() && !this.logger.contains(logger, ignoreCase = true)) return false
        if (!search.isNullOrBlank() &&
            !message.contains(search, ignoreCase = true) &&
            stackTrace?.contains(search, ignoreCase = true) != true
        ) return false
        return true
    }
}
