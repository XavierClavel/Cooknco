package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.DeviceService
import com.xavierclavel.services.FollowService
import com.xavierclavel.services.NotificationService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getLocale
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.logger
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
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
            clearNotification()
            clearAll()
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
        val userId = getSessionUserId()
        val notificationId = getPathId()
        if (!notificationService.markRead(userId, notificationId)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info { "Notification $notificationId marked read by user $userId" }
            call.respond(HttpStatusCode.OK)
        }
    }

    private fun Route.markAllRead() = post("/read") {
        val userId = getSessionUserId()
        val marked = notificationService.markAllRead(userId)
        logger.info { "All notifications marked read by user $userId ($marked)" }
        call.respond(HttpStatusCode.OK, marked)
    }

    /**
     * Clears one notification, read or not.
     *
     * A 404 for a notification that is somebody else's says exactly what a 404 for one that
     * is already gone says, which is deliberate: telling the two apart would let a caller
     * learn which ids exist, and to the caller they mean the same thing — it is not in
     * their list.
     */
    private fun Route.clearNotification() = delete("/{id}") {
        val userId = getSessionUserId()
        val notificationId = getPathId()
        if (!notificationService.clear(userId, notificationId)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info { "Notification $notificationId cleared by user $userId" }
            call.respond(HttpStatusCode.OK)
        }
    }

    /**
     * Clears the lot, and answers how many that was.
     *
     * Unread ones included: "clear" is the user saying they are done with the list, not that
     * they have read it — [markAllRead] is the one that says that, and it is the other
     * action offered next to this one.
     */
    private fun Route.clearAll() = delete {
        val userId = getSessionUserId()
        val cleared = notificationService.clearAll(userId)
        logger.info { "All notifications cleared by user $userId ($cleared)" }
        call.respond(HttpStatusCode.OK, cleared)
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
        val userId = getSessionUserId()
        val registration = call.receive<DeviceRegistrationDTO>()
        deviceService.register(userId, registration, getLocale())
        logger.info {
            "Device registered for user $userId " +
                "(${registration.platform}, app ${registration.appVersion.ifBlank { "unknown" }})"
        }
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
        val userId = getSessionUserId()
        val token = call.receive<DeviceRegistrationDTO>().token
        if (!deviceService.unregister(userId, token)) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            logger.info { "Device unregistered for user $userId" }
            call.respond(HttpStatusCode.OK)
        }
    }
}
