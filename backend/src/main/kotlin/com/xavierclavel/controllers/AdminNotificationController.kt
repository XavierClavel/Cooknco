package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.NotificationService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getEnumQueryParam
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject
import shared.dto.AnnouncementDTO
import shared.dto.NotificationTestDTO
import shared.enums.Locale

/**
 * Push notifications an operator sends by hand, for the backoffice notifications tab.
 *
 * Unlike the mails tab next to it, this owns no wording: an announcement is typed at send
 * time rather than saved as a template, because it is a one-off by definition — a template
 * for it would be a template with exactly one use.
 *
 * Everything here is behind `admin-session`, declared once by [AdminController].
 */
object AdminNotificationController: Controller("notifications") {
    val notificationService: NotificationService by inject(NotificationService::class.java)

    override fun Route.routes() {
        getAudience()
        sendAnnouncement()
        sendTest()
    }

    /**
     * How many users and devices a send would reach.
     *
     * Read before sending, so an operator about to broadcast knows the size of what they are
     * about to do. `users` empty means everybody, exactly as it does on the send itself.
     *
     * @param users comma-separated user ids; absent for a broadcast
     * @param locale narrows a broadcast to the users with a device in that language
     */
    private fun Route.getAudience() = get("/audience") {
        val userIds = call.request.queryParameters["users"]
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?: emptyList()
        call.respond(notificationService.describeAudience(userIds, getEnumQueryParam<Locale>("locale")))
    }

    /**
     * Sends an announcement, to the users named or to everybody.
     *
     * Accepted rather than OK, like the test mail next door and for the same reason: what
     * comes back says the notifications are stored and the pushes are on their way. Only a
     * device can say one arrived.
     *
     * A broadcast is deliberately not the default — [AnnouncementDTO.userIds] empty is what
     * asks for one, so the caller has to have sent a body that says so.
     */
    private fun Route.sendAnnouncement() = post("/announce") {
        call.respond(HttpStatusCode.Accepted, notificationService.announce(call.receive<AnnouncementDTO>()))
    }

    /**
     * Sends one notification to the operator's own devices.
     *
     * The recipient is the caller, not a field on the request: a test exists to prove the
     * path works, and one that could be aimed at somebody else would be an announcement with
     * no audience preview.
     */
    private fun Route.sendTest() = post("/test") {
        call.respond(notificationService.sendTest(getSessionUserId(), call.receive<NotificationTestDTO>()))
    }
}
