package main.com.xavierclavel.other

import com.xavierclavel.logging.LogBuffer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import shared.enums.LogLevel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ring buffer backs the backoffice log viewer. It is a global fed by every logging
 * thread, so what matters is that it stays bounded, keeps the newest lines, and filters
 * the same way the SSE tail does.
 */
class LogBufferTest {

    @BeforeEach
    fun reset() {
        LogBuffer.clear()
        LogBuffer.setCapacity(LogBuffer.DEFAULT_CAPACITY)
    }

    private fun append(
        message: String,
        level: LogLevel = LogLevel.INFO,
        logger: String = "com.xavierclavel.Test",
        stackTrace: String? = null,
    ) = LogBuffer.append(
        timestamp = 1_700_000_000_000,
        level = level,
        logger = logger,
        thread = "test",
        message = message,
        stackTrace = stackTrace,
    )

    @Test
    fun `entries come back in chronological order`() {
        append("first")
        append("second")
        append("third")
        assertEquals(listOf("first", "second", "third"), LogBuffer.query().entries.map { it.message })
    }

    @Test
    fun `sequence numbers increase monotonically`() {
        repeat(3) { append("line $it") }
        val sequences = LogBuffer.query().entries.map { it.sequence }
        assertEquals(sequences.sorted(), sequences)
        assertEquals(sequences.distinct().size, sequences.size)
    }

    @Test
    fun `the oldest entries are evicted once capacity is reached`() {
        LogBuffer.setCapacity(3)
        repeat(5) { append("line $it") }

        val page = LogBuffer.query()
        assertEquals(listOf("line 2", "line 3", "line 4"), page.entries.map { it.message })
        assertEquals(3, page.bufferSize)
        assertEquals(3, page.bufferCapacity)
        assertEquals(2, page.droppedCount)
    }

    @Test
    fun `shrinking the capacity trims what is already buffered`() {
        repeat(10) { append("line $it") }
        LogBuffer.setCapacity(4)
        assertEquals(4, LogBuffer.query().bufferSize)
        assertEquals(listOf("line 6", "line 7", "line 8", "line 9"), LogBuffer.query().entries.map { it.message })
    }

    @Test
    fun `the level filter keeps only equal or higher severities`() {
        append("trace me", LogLevel.TRACE)
        append("debug me", LogLevel.DEBUG)
        append("info me", LogLevel.INFO)
        append("warn me", LogLevel.WARN)
        append("error me", LogLevel.ERROR)

        assertEquals(5, LogBuffer.query(minLevel = LogLevel.TRACE).entries.size)
        assertEquals(
            listOf("warn me", "error me"),
            LogBuffer.query(minLevel = LogLevel.WARN).entries.map { it.message },
        )
        assertEquals(1, LogBuffer.countAtLeast(LogLevel.ERROR))
        assertEquals(2, LogBuffer.countAtLeast(LogLevel.WARN))
    }

    @Test
    fun `the logger filter matches on a substring, case-insensitively`() {
        append("a", logger = "com.xavierclavel.services.RecipeService")
        append("b", logger = "io.ktor.server.Application")

        assertEquals(listOf("a"), LogBuffer.query(logger = "recipeservice").entries.map { it.message })
        assertEquals(listOf("b"), LogBuffer.query(logger = "io.ktor").entries.map { it.message })
        assertTrue(LogBuffer.query(logger = "nothing").entries.isEmpty())
    }

    @Test
    fun `the search filter also looks inside stack traces`() {
        append("plain message")
        append("failed", stackTrace = "java.lang.IllegalStateException: boom")

        assertEquals(listOf("plain message"), LogBuffer.query(search = "PLAIN").entries.map { it.message })
        assertEquals(listOf("failed"), LogBuffer.query(search = "IllegalState").entries.map { it.message })
    }

    @Test
    fun `sinceSequence returns only what the caller has not seen`() {
        append("old")
        val lastSeen = LogBuffer.query().entries.last().sequence
        append("new")

        assertEquals(listOf("new"), LogBuffer.query(sinceSequence = lastSeen).entries.map { it.message })
        assertTrue(LogBuffer.query(sinceSequence = lastSeen + 10).entries.isEmpty())
    }

    @Test
    fun `a limit returns the newest matching entries`() {
        repeat(10) { append("line $it") }
        assertEquals(
            listOf("line 7", "line 8", "line 9"),
            LogBuffer.query(limit = 3).entries.map { it.message },
        )
    }

    @Test
    fun `the limit is capped so one request cannot dump an unbounded page`() {
        repeat(LogBuffer.MAX_PAGE_SIZE + 50) { append("line $it") }
        assertEquals(LogBuffer.MAX_PAGE_SIZE, LogBuffer.query(limit = Int.MAX_VALUE).entries.size)
        // The cap never pads a short buffer out
        LogBuffer.clear()
        append("only one")
        assertEquals(1, LogBuffer.query(limit = Int.MAX_VALUE).entries.size)
    }

    @Test
    fun `a limit below one is raised to one rather than returning nothing`() {
        append("a")
        append("b")
        assertEquals(listOf("b"), LogBuffer.query(limit = 0).entries.map { it.message })
    }

    @Test
    fun `known loggers are reported for the filter dropdown`() {
        append("a", logger = "b.Second")
        append("b", logger = "a.First")
        append("c", logger = "a.First")
        assertEquals(listOf("a.First", "b.Second"), LogBuffer.query().loggers)
    }

    @Test
    fun `clearing empties the buffer and resets the dropped counter`() {
        LogBuffer.setCapacity(2)
        repeat(5) { append("line $it") }
        assertTrue(LogBuffer.query().droppedCount > 0)

        LogBuffer.clear()
        LogBuffer.query().apply {
            assertTrue(entries.isEmpty())
            assertEquals(0, bufferSize)
            assertEquals(0L, droppedCount)
        }
    }
}
