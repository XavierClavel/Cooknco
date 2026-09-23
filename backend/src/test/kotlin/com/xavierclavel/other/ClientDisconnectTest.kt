package main.com.xavierclavel.other

import com.xavierclavel.ApplicationTest
import com.xavierclavel.logging.LogBuffer
import com.xavierclavel.module
import com.xavierclavel.utils.logger
import io.ktor.client.request.get
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedWriteChannelException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import shared.enums.LogLevel
import shared.infodto.LogEntryInfo
import java.net.Socket
import java.util.Base64
import java.util.UUID
import kotlin.test.assertTrue

/**
 * A client hanging up in the middle of a response must not be reported as a server error.
 *
 * The endpoint it happens on constantly is the admin log tail: the backoffice reopens its
 * `EventSource` on every filter change, so each of those closures used to write an ERROR
 * into the ring buffer — which the tail that replaced it then replayed as though the server
 * had broken, and which the overview's "errors in logs" alert counted. Watching the logs
 * was what made them look bad.
 *
 * The assertions are on [LogBuffer] rather than on a status, because there is no status to
 * assert on: by the time the write fails, the response has left.
 */
class ClientDisconnectTest: ApplicationTest() {

    /** Where the buffer stands now, so a test only reads what its own case produced. */
    private fun mark(): Long = LogBuffer.query(limit = 1).entries.lastOrNull()?.sequence ?: 0L

    private fun errorsSince(mark: Long): List<LogEntryInfo> =
        LogBuffer.query(minLevel = LogLevel.ERROR, sinceSequence = mark, limit = 100).entries

    /**
     * The end-to-end case, and the reason this one test starts a real Netty rather than
     * going through `testApplication` like everything else: the test engine's transport
     * cancels the handler cleanly when a client goes away, so it never reaches the write
     * that fails. Only a real socket, closed from under a live response, reproduces it.
     */
    @Test
    fun `a tail whose client vanishes leaves no error behind`() {
        val server = embeddedServer(Netty, port = 0, host = "127.0.0.1", module = { module() })
        server.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        try {
            val cookie = loginAsAdmin(port)
            val mark = mark()

            openTail(port, cookie).close()

            // The handler only discovers the departure when it next writes, so keep the
            // lines coming after the socket is gone
            repeat(20) {
                logger.info { "disconnect-probe-${UUID.randomUUID()}" }
                Thread.sleep(50)
            }
            Thread.sleep(1000)

            val errors = errorsSince(mark)
            assertTrue(
                errors.none { it.message.contains("/logs/stream") },
                "a closed tail was reported as a server error: ${errors.map { it.message.lineSequence().first() }}",
            )
        } finally {
            server.stop(0, 0)
        }
    }

    /**
     * The two shapes the disconnect arrives in, asserted directly — the socket test above
     * can only provoke whichever one its timing happens to produce, and in production it is
     * the other one that shows up (a bare [ClosedWriteChannelException] out of the SSE
     * session's own write, rather than the pipeline's [ChannelWriteException] wrapper).
     */
    @Test
    fun `neither shape of a failed response write is a server error`() = testApplication {
        application {
            module()
            routing {
                get("/disconnect/wrapped") {
                    throw ChannelWriteException("Cannot write to channel", ClosedWriteChannelException())
                }
                get("/disconnect/bare") { throw ClosedWriteChannelException() }
            }
        }

        listOf("/disconnect/wrapped", "/disconnect/bare").forEach { path ->
            val mark = mark()
            runCatching { client.get(path) }
            assertTrue(
                errorsSince(mark).none { it.message.contains(path) },
                "$path was reported as a server error",
            )
        }
    }

    private fun loginAsAdmin(port: Int): String {
        val basic = Base64.getEncoder().encodeToString("admin@mail.com:$password".toByteArray())
        return Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write(
                ("POST /api/v1/auth/login HTTP/1.1\r\nHost: localhost\r\n" +
                    "Authorization: Basic $basic\r\nConnection: close\r\n\r\n").toByteArray()
            )
            socket.getOutputStream().flush()
            val response = socket.getInputStream().readBytes().decodeToString()
            Regex("Set-Cookie: (user_session=[^;]*)").find(response)?.groupValues?.get(1)
                ?: error("no session cookie in: ${response.lineSequence().first()}")
        }
    }

    /** Opens the tail and waits for the handler to attach, so the close lands on a live one. */
    private fun openTail(port: Int, cookie: String): Socket {
        val socket = Socket("127.0.0.1", port)
        socket.getOutputStream().write(
            ("GET /api/v1/admin/logs/stream?level=INFO HTTP/1.1\r\nHost: localhost\r\n" +
                "Accept: text/event-stream\r\nCookie: $cookie\r\n\r\n").toByteArray()
        )
        socket.getOutputStream().flush()
        Thread.sleep(1000)
        socket.getInputStream().read(ByteArray(4096))
        return socket
    }
}
