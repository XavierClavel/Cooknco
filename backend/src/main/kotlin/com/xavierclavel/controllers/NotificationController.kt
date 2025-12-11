package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.FollowService
import com.xavierclavel.utils.Controller
import shared.infodto.NotificationInfo
import shared.utils.URL.NOTIFICATION_URL
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

object NotificationController: Controller(NOTIFICATION_URL) {
    val followService: FollowService by inject(FollowService::class.java)

    override fun Route.routes() {
        authenticate("auth-session", "bearer-auth") {
            getNotifications()
        }

    }

    private fun Route.getNotifications() = get {
        val userId = getSessionUserId()
        val notification = NotificationInfo(
            followersPending = followService.getFollowersPending(userId),
        )
        call.respond(notification)
    }

}