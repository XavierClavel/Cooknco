package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.services.AdminService
import com.xavierclavel.services.ModerationService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.enumValueOfIgnoreCase
import com.xavierclavel.utils.getEnumQueryParam
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getPaging
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.utils.json
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.inject
import shared.dto.ModerationReasonDTO
import shared.dto.SearchResult
import shared.dto.SuspensionDTO
import shared.enums.AccountStatus
import shared.enums.UserRole
import shared.infodto.AdminUserInfo

/** Account management: search, role changes, suspension, ban and deletion. */
object AdminUserController: Controller("users") {
    val adminService: AdminService by inject(AdminService::class.java)
    val moderationService: ModerationService by inject(ModerationService::class.java)
    val userService: UserService by inject(UserService::class.java)

    override fun Route.routes() {
        searchUsers()
        getUser()
        setRole()
        suspendUser()
        banUser()
        reinstateUser()
        verifyUser()
        deleteUser()
    }

    private fun Route.searchUsers() = get {
        val paging = getPaging()
        val (count, users) = adminService.searchUsers(
            query = getStringQueryParam("query"),
            role = getEnumQueryParam<UserRole>("role"),
            status = getEnumQueryParam<AccountStatus>("status"),
            paging = paging,
        )
        val result = SearchResult(count, paging.pageIndex(), paging.pageSize(), users)
        call.respond(json.encodeToString(SearchResult.serializer(AdminUserInfo.serializer()), result))
    }

    private fun Route.getUser() = get("/{id}") {
        call.respond(adminService.getUser(getPathId()))
    }

    private fun Route.setRole() = put("/{id}/role/{role}") {
        val role = enumValueOfIgnoreCase<UserRole>(call.parameters["role"] ?: "")
        val id = getPathId()
        // Losing the last admin would lock the backoffice for everyone
        if (role != UserRole.ADMIN && userService.countAdmins() <= 1 && userService.getEntityById(id).role == UserRole.ADMIN) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_DEMOTE_LAST_ADMIN)
        }
        userService.setRole(id, role)
        call.respond(adminService.getUser(id))
    }

    private fun Route.suspendUser() = post("/{id}/suspend") {
        val dto = call.receive<SuspensionDTO>()
        call.respond(moderationService.suspendUser(getPathId(), dto.days, dto.reason))
    }

    private fun Route.banUser() = post("/{id}/ban") {
        val dto = call.receive<ModerationReasonDTO>()
        call.respond(moderationService.banUser(getPathId(), dto.reason))
    }

    private fun Route.reinstateUser() = post("/{id}/reinstate") {
        call.respond(moderationService.reinstateUser(getPathId()))
    }

    private fun Route.verifyUser() = post("/{id}/verify") {
        call.respond(moderationService.verifyUser(getPathId()))
    }

    private fun Route.deleteUser() = delete("/{id}") {
        val id = getPathId()
        // Deleting yourself here would leave the caller holding a dead session
        if (id == getSessionUserId()) throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_MODERATE_ADMIN)
        moderationService.deleteUser(id)
        call.respond(HttpStatusCode.OK)
    }
}
