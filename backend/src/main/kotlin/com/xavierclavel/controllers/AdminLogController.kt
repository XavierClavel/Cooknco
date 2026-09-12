package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.logging.LogBuffer
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.utils.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.Json
import shared.enums.LogLevel
import shared.infodto.LogEntryInfo
import kotlin.time.Duration.Companion.seconds

/**
 * Serves the server logs captured in [LogBuffer].
 *
 * Two ways in: `GET /logs` returns a filtered snapshot (also usable for incremental
 * polling via `sinceSequence`), and `GET /logs/stream` is an SSE live tail.
 */
object AdminLogController: Controller("logs") {

    /** Compact encoder: log lines are streamed one per event, so pretty-printing would bloat them. */
    private val logJson = Json { encodeDefaults = true }

    override fun Route.routes() {
        getLogs()
        streamLogs()
        clearLogs()
    }

    private fun Route.getLogs() = get {
        call.respond(
            LogBuffer.query(
                minLevel = getMinLevel(),
                logger = getStringQueryParam("logger"),
                search = getStringQueryParam("search"),
                sinceSequence = call.request.queryParameters["sinceSequence"]?.toLongOrNull(),
                limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200,
            )
        )
    }

    /**
     * Live tail. Only events arriving after the connection opens are sent — a client is
     * expected to load a snapshot from `GET /logs` first, then attach here and append.
     */
    private fun Route.streamLogs() = sse("/stream") {
        val minLevel = call.request.queryParameters["level"]
            ?.let { level -> LogLevel.entries.find { it.name.equals(level, ignoreCase = true) } }
            ?: LogLevel.TRACE
        val loggerFilter = call.request.queryParameters["logger"]?.takeIf { it.isNotBlank() }
        val search = call.request.queryParameters["search"]?.takeIf { it.isNotBlank() }

        // Keeps the connection alive through idle periods, which proxies would otherwise cut
        heartbeat {
            period = 20.seconds
            event = ServerSentEvent(comments = "keep-alive")
        }

        LogBuffer.stream
            .filter { it.matches(minLevel, loggerFilter, search) }
            .collect { entry ->
                send(
                    data = logJson.encodeToString(LogEntryInfo.serializer(), entry),
                    event = "log",
                    id = entry.sequence.toString(),
                )
            }
    }

    /** Empties the buffer, so an operator can watch a reproduction from a clean slate. */
    private fun Route.clearLogs() = delete {
        val adminId = getSessionUserId()
        LogBuffer.clear()
        logger.info { "Log buffer cleared by admin $adminId" }
        call.respond(HttpStatusCode.OK)
    }

    private fun RoutingContext.getMinLevel(): LogLevel =
        getEnumQueryParam<LogLevel>("level") ?: LogLevel.TRACE

    private fun LogEntryInfo.matches(
        minLevel: LogLevel,
        loggerFilter: String?,
        search: String?,
    ): Boolean =
        level.isAtLeast(minLevel) &&
            (loggerFilter == null || logger.contains(loggerFilter, ignoreCase = true)) &&
            (
                search == null ||
                    message.contains(search, ignoreCase = true) ||
                    stackTrace?.contains(search, ignoreCase = true) == true
                )
}
