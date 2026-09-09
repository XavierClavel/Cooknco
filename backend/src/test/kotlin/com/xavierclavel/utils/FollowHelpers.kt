package main.com.xavierclavel.utils

import shared.infodto.FollowInfo
import shared.utils.URL.FOLLOW_URL
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals


suspend fun HttpClient.follow(id: Long): FollowInfo {
    this.post("$FOLLOW_URL/$id").apply {
        assertEquals(HttpStatusCode.Created, status)
        return Json.decodeFromString<FollowInfo>(bodyAsText())
    }
}

suspend fun HttpClient.unfollow(id: Long) {
    this.delete("$FOLLOW_URL/$id").apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}

suspend fun HttpClient.getFollowers(id: Long): Set<FollowInfo> {
    this.get("$FOLLOW_URL/$id/followers").apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString<Set<FollowInfo>>(bodyAsText())
    }
}

suspend fun HttpClient.getFollows(id: Long): Set<FollowInfo> {
    this.get("$FOLLOW_URL/$id/follows").apply {
        assertEquals(HttpStatusCode.OK, status)
        return Json.decodeFromString<Set<FollowInfo>>(bodyAsText())
    }
}

/**
 * Accepts a pending follow request, as the account that was asked.
 *
 * The path names the *follower* and the session names who is accepting — the opposite way
 * round from [follow], which is what the endpoint does (`FollowController.acceptFollow`).
 */
suspend fun HttpClient.acceptFollowRequest(followerId: Long) {
    this.post("$FOLLOW_URL/$followerId/request").apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}
