package com.xavierclavel.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import shared.enums.LogLevel

/**
 * Logback appender that mirrors log events into [LogBuffer] so the admin backoffice can
 * serve them. Wired up in logback.xml; `capacity` is settable from that configuration.
 */
class RingBufferAppender : AppenderBase<ILoggingEvent>() {

    var capacity: Int = LogBuffer.DEFAULT_CAPACITY

    override fun start() {
        LogBuffer.setCapacity(capacity)
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        LogBuffer.append(
            timestamp = event.timeStamp,
            level = event.level.toLogLevel(),
            logger = event.loggerName,
            thread = event.threadName ?: "",
            message = event.formattedMessage ?: "",
            stackTrace = event.throwableProxy?.let { ThrowableProxyUtil.asString(it) },
        )
    }

    private fun Level.toLogLevel(): LogLevel = when (levelInt) {
        Level.TRACE_INT -> LogLevel.TRACE
        Level.DEBUG_INT -> LogLevel.DEBUG
        Level.WARN_INT -> LogLevel.WARN
        Level.ERROR_INT -> LogLevel.ERROR
        else -> LogLevel.INFO
    }
}
