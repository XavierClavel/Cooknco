package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.services.FollowService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getIdPathVariable
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.logger
import shared.utils.URL.FOLLOW_URL
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.koin.java.KoinJavaComponent.inject

object FollowController: Controller(FOLLOW_URL) {
    val userService : UserService by inject(UserService::class.java)
    val followService: FollowService by inject(FollowService::class.java)

    override fun Route.routes() {
        follow()
        unfollow()
        getFollowers()
        getFollows()
        isFollowingUser()
        isFollowedByUser()

        acceptFollow()
        declineFollow()
    }

    private fun Route.follow() = post("/{id}") {
        val (userId,followerId) = handleFollowRequest()
        if (followService.isFollowing(userId, followerId)) throw BadRequestException(BadRequestCause.USER_ALREADY_FOLLOWED)
        val follow = followService.createFollow(userId, followerId)
        logger.info { "User $followerId now follows user $userId" }
        call.respond(HttpStatusCode.Created, follow)
    }

    private fun Route.acceptFollow() = post("/{id}/request") {
        val (followerId,userId) = handleFollowRequest()
        followService.acceptFollowRequest(userId, followerId)
        logger.info { "User $userId accepted the follow request from user $followerId" }
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.declineFollow() = delete("/{id}/request") {
        val (followerId,userId) = handleFollowRequest()
        followService.deleteFollow(userId, followerId)
        logger.info { "User $userId declined the follow request from user $followerId" }
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.unfollow() = delete("/{id}") {
        val (userId,followerId) = handleFollowRequest()
        if (!followService.isFollowing(userId, followerId)) throw BadRequestException(BadRequestCause.USER_NOT_FOLLOWED)
        val result = followService.deleteFollow(userId, followerId)
        if (!result) return@delete call.respond(HttpStatusCode.InternalServerError)
        logger.info { "User $followerId no longer follows user $userId" }
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.getFollowers() = get("/{id}/followers") {
        val userId = getPathId()
        userService.getUser(userId)
        val paging = getPaging()
        call.respond(followService.getFollowers(userId, paging))
    }

    private fun Route.getFollows() = get("/{id}/follows") {
        val userId = getPathId()
        userService.getUser(userId)
        val paging = getPaging()
        call.respond(followService.getFollows(userId, paging))
    }

    private fun Route.isFollowedByUser() = get("/{id}/followedBy/{targetId}") {
        val userId = getPathId()
        val targetId = getIdPathVariable("targetId") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        call.respond(followService.isFollowing(userId, targetId))
    }

    private fun Route.isFollowingUser() = get("/{id}/follows/{targetId}") {
        val userId = getPathId()
        val targetId = getIdPathVariable("targetId") ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        call.respond(followService.isFollowing(targetId, userId))
    }



    private suspend fun RoutingContext.handleFollowRequest(): Pair<Long, Long> {
        val userId = getPathId()
        userService.getUser(userId)
        val followerId = getSessionUserId()
        if(userId == followerId) throw BadRequestException(BadRequestCause.NOT_APPLICABLE_ON_SELF)
        return Pair(userId, followerId)
    }


}