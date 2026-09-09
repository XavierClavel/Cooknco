package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.DeviceService
import com.xavierclavel.services.FollowService
import com.xavierclavel.services.NotificationService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getLocale
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getPaging
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.java.KoinJavaComponent.inject
import shared.dto.DeviceRegistrationDTO
import shared.infodto.NotificationInfo
import shared.utils.URL.NOTIFICATION_URL

/**
 * What a signed-in user has waiting for them, and where to push it.
 *
 * The two live together because they are two halves of one thing: the list is what a
 * notification durably *is* (see [NotificationService]), and a device registration is only
 * the address a copy of it is also delivered to.
 */
object NotificationController: Controller(NOTIFICATION_URL) {
    val followService: FollowService by inject(FollowService::class.java)
    val notificationService: NotificationService by inject(NotificationService::class.java)
    val deviceService: DeviceService by inject(DeviceService::class.java)

    override fun Route.routes() {
        authenticate("auth-session", "bearer-auth") {
            getNotifications()
            listNotifications()
            markRead()
            markAllRead()
            route("/devices") {
                registerDevice()
                unregisterDevice()
            }
        }
    }

    /**
     * Everything the notification bell shows, in one call.
     *
     * One call rather than three because the bell polls it (`pollingStore` in the web app),
     * and a badge that took three requests to draw would triple that traffic for nothing.
     */
    private fun Route.getNotifications() = get {
        val userId = getSessionUserId()
        call.respond(
            NotificationInfo(
                followersPending = followService.getFollowersPending(userId),
                notifications = notificationService.list(userId, getPaging()),
                unreadCount = notificationService.unreadCount(userId),
            )
        )
    }

    /** The same list, paged, for a screen that shows more than a menu's worth. */
    private fun Route.listNotifications() = get("/list") {
        call.respond(notificationService.list(getSessionUserId(), getPaging()))
    }

    private fun Route.markRead() = post("/{id}/read") {
        if (!notificationService.markRead(getSessionUserId(), getPathId())) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.markAllRead() = post("/read") {
        call.respond(HttpStatusCode.OK, notificationService.markAllRead(getSessionUserId()))
    }

    /**
     * Records where this user can be pushed to.
     *
     * Clients call it on every launch and whenever FCM rotates the token, so it has to be
     * idempotent — it is, because the token identifies the row. The locale is the one the
     * *client* is running in, not the account's: a user reading the app in English and the
     * website in French should get each notification in the language it will be read in.
     */
    private fun Route.registerDevice() = post {
        deviceService.register(getSessionUserId(), call.receive<DeviceRegistrationDTO>(), getLocale())
        call.respond(HttpStatusCode.Created)
    }

    /**
     * Forgets a device, on sign-out.
     *
     * A POST carrying the token rather than a DELETE naming it in the path: a registration
     * token is a long opaque string, and putting one in a URL puts it in every access log
     * between here and the client.
     */
    private fun Route.unregisterDevice() = post("/unregister") {
        val token = call.receive<DeviceRegistrationDTO>().token
        if (!deviceService.unregister(getSessionUserId(), token)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(HttpStatusCode.OK)
        }
    }
}
