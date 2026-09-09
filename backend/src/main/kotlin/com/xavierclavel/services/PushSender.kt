package com.xavierclavel.services

import com.xavierclavel.utils.logger

/**
 * One notification, addressed to one device.
 *
 * Already rendered: the sender does no wording, no locale and no templating, because by the
 * time a message reaches it the text has been settled by [NotificationService] and written
 * to a `notifications` row. What goes out and what the recipient later reads in the app are
 * then the same string by construction.
 */
data class PushMessage(
    val token: String,
    val title: String,
    val body: String,

    /**
     * Key/value pairs handed to the client alongside the visible text — the kind, the
     * notification's id, the path to open. FCM only carries strings here, so callers that
     * have a number convert it.
     */
    val data: Map<String, String> = emptyMap(),
)

/**
 * What a batch of pushes did.
 *
 * [staleTokens] are the ones the transport reported as belonging to an app that is no
 * longer installed. They are the only tokens safe to delete — a push that failed for any
 * other reason (a timeout, a 503, a quota) says nothing about whether the device exists,
 * and deleting on those would quietly unsubscribe users during an outage.
 */
data class PushResult(
    val delivered: Int = 0,
    val failed: Int = 0,
    val staleTokens: List<String> = emptyList(),
)

/**
 * Delivers notifications to devices.
 *
 * An interface for the reason [PdfRenderer] is one: the thing that does the work is not in
 * this image, and both the tests and an install with no push credentials need a version
 * that does nothing without the callers knowing the difference.
 */
interface PushSender {
    /**
     * Pushes each message, and reports what happened.
     *
     * Never throws: a notification is stored before it is pushed, so a transport that is
     * down costs the recipient a buzz rather than the notification itself. Failures are
     * counted and logged instead.
     */
    suspend fun send(messages: List<PushMessage>): PushResult
}

/**
 * The sender used where there are no push credentials: local development, the tests, and
 * any environment whose `application.yaml` leaves the `push` section off.
 *
 * It logs rather than silently swallowing, so that "the notification never arrived" is
 * answerable from the backend's own logs without first working out whether it was ever
 * configured to send one.
 */
class NoopPushSender : PushSender {
    override suspend fun send(messages: List<PushMessage>): PushResult {
        if (messages.isNotEmpty()) {
            logger.info { "Push is not configured: ${messages.size} notification(s) stored but not delivered" }
        }
        return PushResult(failed = messages.size)
    }
}
