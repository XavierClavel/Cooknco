package com.xavierclavel.utils

import com.xavierclavel.controllers.AuthController.getOptionalSession
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.json.Json
import mu.KotlinLogging

val logger = KotlinLogging.logger {}

/**
 * Every write a user or an operator performs, one line each.
 *
 * A logger of its own rather than [logger], because a trail mixed in with everything else
 * is not one: the ring buffer behind the backoffice logs tab holds 5 000 lines of whatever
 * the server was doing at the time, and a busy minute buries the edits. The tab already
 * filters by logger name — its dropdown is `LogPage.loggers`, and it shows the last dotted
 * segment — so this name is what turns "EditActions" into a view of nothing but the writes.
 *
 * Not a second mechanism, though: it inherits logback's root config like every other logger,
 * so these lines reach the console, the 60-day rolling file on the log volume, and the ring
 * buffer alike. Nothing in [com.xavierclavel.logging.LogBuffer] had to learn about it.
 */
private val editLogger = KotlinLogging.logger("com.xavierclavel.EditActions")

/**
 * Records one write, as `<what happened> by user <id> (<name>)`.
 *
 * The caller is read off the request rather than passed in, because "the actor is whoever
 * made this request" is the one thing an audit line must not get wrong, and the lines this
 * replaced got it wrong exactly that way — they named the *owner* of what was written, so a
 * moderator hiding somebody's recipe was filed under its author. A parameter can be handed
 * the wrong id; this cannot.
 *
 * [getOptionalSession] rather than anything that throws: this is called after the write has
 * happened, so a missing session must produce a worse log line and never a failed request
 * out of one that succeeded.
 *
 * The id is the identifier and the name beside it is a label. It comes from the session, so
 * it is what the account was called when it signed in and can be out of date — see
 * [com.xavierclavel.plugins.SessionData.username], which explains why nothing refreshes it.
 * Costs one Redis read per write, which is the read that resolves the caller anyway.
 */
suspend fun RoutingContext.logEdit(action: () -> String) =
    getOptionalSession().let { logEdit(it?.userId, it?.username, action) }

/**
 * The same line for a write with no session behind it to read: the four MCP tools, which are
 * not routes at all and are handed the account the token belongs to, and the image upload a
 * ticket authenticates, whose actor is recorded in the ticket. Passing an actor here is the
 * exception, and every caller of it is one of those.
 *
 * @param actorId the account responsible, or null when nothing identifies one — which is a
 *   line worth keeping rather than dropping, since the write happened either way.
 * @param actorName their username where the caller holds it already; the id stands alone
 *   when it does not.
 * @param action what was written and what became of it, named the way an operator searching
 *   for it later would: the entity, its id, and its title where it has one. Past tense,
 *   because it is called after the write — a line logging the intention would outlive the
 *   failure of the thing it claims.
 */
fun logEdit(actorId: Long?, actorName: String? = null, action: () -> String) =
    editLogger.info {
        val actor = actorId?.let { id -> "user $id" + actorName?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty() }
        "${action()} by ${actor ?: "an unidentified caller"}"
    }

val json = Json {
    prettyPrint = true
    encodeDefaults = true
}
