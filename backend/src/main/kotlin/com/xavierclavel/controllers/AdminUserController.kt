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
import com.xavierclavel.utils.logger
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
        val adminId = getSessionUserId()
        // Losing the last admin would lock the backoffice for everyone
        if (role != UserRole.ADMIN && userService.countAdmins() <= 1 && userService.getEntityById(id).role == UserRole.ADMIN) {
            throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_DEMOTE_LAST_ADMIN)
        }
        userService.setRole(id, role)
        logger.info { "User $id given role $role by admin $adminId" }
        call.respond(adminService.getUser(id))
    }

    private fun Route.suspendUser() = post("/{id}/suspend") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val dto = call.receive<SuspensionDTO>()
        val user = moderationService.suspendUser(id, dto.days, dto.reason)
        logger.info { "User $id suspended for ${dto.days} day(s) by admin $adminId (reason: ${dto.reason})" }
        call.respond(user)
    }

    private fun Route.banUser() = post("/{id}/ban") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val dto = call.receive<ModerationReasonDTO>()
        val user = moderationService.banUser(id, dto.reason)
        logger.info { "User $id banned by admin $adminId (reason: ${dto.reason})" }
        call.respond(user)
    }

    private fun Route.reinstateUser() = post("/{id}/reinstate") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val user = moderationService.reinstateUser(id)
        logger.info { "User $id reinstated by admin $adminId" }
        call.respond(user)
    }

    private fun Route.verifyUser() = post("/{id}/verify") {
        val id = getPathId()
        val adminId = getSessionUserId()
        val user = moderationService.verifyUser(id)
        logger.info { "User $id verified by admin $adminId" }
        call.respond(user)
    }

    private fun Route.deleteUser() = delete("/{id}") {
        val id = getPathId()
        // Deleting yourself here would leave the caller holding a dead session
        val adminId = getSessionUserId()
        if (id == adminId) throw ForbiddenException(ForbiddenCause.NOT_ALLOWED_TO_MODERATE_ADMIN)
        moderationService.deleteUser(id)
        logger.info { "User $id deleted by admin $adminId" }
        call.respond(HttpStatusCode.OK)
    }
}
