package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.services.ImageService
import com.xavierclavel.services.OAuthService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.UserSession
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getQuery
import com.xavierclavel.utils.json
import shared.dto.PasswordDTO
import shared.dto.SearchResult
import shared.dto.UserDTO
import shared.dto.UserSettingsDTO
import shared.enums.UserRole
import shared.infodto.UserInfo
import shared.utils.Filepath.USERS_IMG_PATH
import shared.utils.URL.USER_URL
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import org.koin.java.KoinJavaComponent.inject

object UserController: Controller(USER_URL) {
    val userService : UserService by inject(UserService::class.java)
    val imageService: ImageService by inject(ImageService::class.java)
    val oauthService: OAuthService by inject(OAuthService::class.java)

    override fun Route.routes() {
        getUser()
        searchUsers()
        countUsers()

        authenticate("auth-session", "bearer-auth") {
            editUser()
            deleteUser()

            updatePassword()

            getSettings()
            updateSettings()

            listMcpClients()
            revokeMcpClient()
        }

        authenticate("admin-session") {
            setRole()
        }
    }

    private fun Route.getUser() = get("/{id}") {
        val id = getPathId()
        val user = userService.getUser(id)
        call.respond(user)
    }

    private fun Route.searchUsers() = get {
        val searchString = getQuery()
        val paging = getPaging()
        val users = userService.search(searchString, paging)
        val result = SearchResult(users.first, paging.pageIndex(), paging.pageSize(), users.second)
        call.respond(json.encodeToString(SearchResult.serializer(UserInfo.serializer()), result))
    }

    private fun Route.countUsers() = get("/count") {
        call.respond(userService.countAll())
    }

    private fun Route.editUser() = put {
        val id = getSessionUserId()
        val userDTO = call.receive<UserDTO>()
        val response = userService.editUser(id, userDTO)
        call.respond(response)
    }

    private fun Route.deleteUser() = delete {
        val id = getSessionUserId()
        val user = userService.getUser(id)
        userService.deleteUserById(user.id)
        imageService.deleteImage(USERS_IMG_PATH, user.id, user.version)
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.updatePassword() = put("/password") {
        val id = getSessionUserId()
        val passwordDTO = call.receive<PasswordDTO>()
        if (!userService.isPasswordValid(id, passwordDTO.old)) {
            throw UnauthorizedException(UnauthorizedCause.INVALID_PASSWORD)
        }
        userService.updatePassword(id, passwordDTO.new)
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.getSettings() = get("/settings") {
        call.respond(userService.getSettings(getSessionUserId()))
    }

    private fun Route.updateSettings() = put("/settings") {
        val settingsDTO = call.receive<UserSettingsDTO>()
        userService.updateSettings(getSessionUserId(), settingsDTO)
        call.respond(HttpStatusCode.OK)
    }

    /**
     * The MCP clients this account has approved — the settings screen's "MCP access".
     *
     * Under `/user` rather than `/oauth` on purpose: `/oauth` is the spec's surface, spoken
     * by clients with their own tokens, and this is the account speaking about them with a
     * session of its own.
     */
    private fun Route.listMcpClients() = get("/mcp-clients") {
        call.respond(oauthService.grantsOf(getSessionUserId()))
    }

    /**
     * Withdraws one. Answers 404 rather than 200 when there was no such grant, so a client
     * the user is looking at in a stale list does not report as revoked twice.
     */
    private fun Route.revokeMcpClient() = delete("/mcp-clients/{clientId}") {
        val clientId = call.parameters["clientId"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
        if (oauthService.revokeGrant(getSessionUserId(), clientId)) {
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private fun Route.setRole() = put("/{id}/role/{role}") {
        val role = UserRole.valueOf(call.parameters["role"]!!)
        val id = getPathId()
        call.respond(userService.setRole(id, role))
    }


}