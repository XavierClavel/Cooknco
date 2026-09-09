package main.com.xavierclavel.utils

import com.xavierclavel.services.PushMessage
import com.xavierclavel.services.PushResult
import com.xavierclavel.services.PushSender

/**
 * Stands in for Firebase, which no test has credentials for.
 *
 * Records rather than counts: what the tests need to assert is not that a push happened but
 * what was in it — the wording, the kind, and the notification id the app uses to mark the
 * right row read. Sends are appended, so a test can assert on a whole fan-out at once.
 *
 * Guarded by a lock rather than left to chance: the fan-out this records runs on
 * `NotificationService`'s own scope, so several coroutines can be pushing at once, and an
 * unsynchronised `ArrayList` under that loses sends rather than failing loudly.
 */
class FakePushSender : PushSender {

    private val lock = Any()

    private val messages = mutableListOf<PushMessage>()

    /** Every message pushed since the last [reset], in the order they were sent. */
    val sent: List<PushMessage> get() = synchronized(lock) { messages.toList() }

    /**
     * Tokens to answer as dead, so a test can exercise the pruning.
     *
     * Set before the send that should trip it; a token in here is reported stale and counted
     * as failed, exactly as FCM reporting `UNREGISTERED` would be.
     */
    @Volatile
    var staleTokens: Set<String> = emptySet()

    /** Set to make every push fail, as an unreachable FCM would. */
    @Volatile
    var failing: Boolean = false

    override suspend fun send(messages: List<PushMessage>): PushResult {
        synchronized(lock) { this.messages += messages }
        if (failing) return PushResult(failed = messages.size)
        val stale = messages.map { it.token }.filter { it in staleTokens }
        return PushResult(
            delivered = messages.size - stale.size,
            failed = stale.size,
            staleTokens = stale,
        )
    }

    fun reset() {
        synchronized(lock) { messages.clear() }
        staleTokens = emptySet()
        failing = false
    }

    /** The messages pushed for one kind, e.g. `new_recipe`. */
    fun sentOfKind(kind: String) = sent.filter { it.data["kind"] == kind }

    /**
     * Waits for a fan-out to arrive, and returns what did.
     *
     * The notifications the app emits itself are dispatched on a background scope, so the
     * request that triggered one has returned before the push is made. A test therefore has
     * to wait for it, and waiting on a condition is the only honest way — there is no handle
     * on the coroutine to join.
     *
     * @param count how many messages of that kind to wait for
     * @return the messages of that kind, however many arrived
     */
    fun awaitKind(kind: String, count: Int = 1, timeoutMillis: Long = 5_000): List<PushMessage> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val found = sentOfKind(kind)
            if (found.size >= count) return found
            Thread.sleep(25)
        }
        return sentOfKind(kind)
    }
}
