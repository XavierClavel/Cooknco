package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
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
import shared.dto.PremiumGrantDTO
import shared.dto.SearchResult
import shared.dto.SuspensionDTO
import shared.enums.AccountStatus
import shared.enums.PremiumStatus
import shared.enums.UserRole
import shared.infodto.AdminUserInfo
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Account management: search, role changes, premium grants, suspension, ban and deletion. */
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
        grantPremium()
        revokePremium()
        deleteUser()
    }

    private fun Route.searchUsers() = get {
        val paging = getPaging()
        val (count, users) = adminService.searchUsers(
            query = getStringQueryParam("query"),
            role = getEnumQueryParam<UserRole>("role"),
            status = getEnumQueryParam<AccountStatus>("status"),
            premium = getEnumQueryParam<PremiumStatus>("premium"),
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

    /**
     * Makes an account premium — for good, or until a date.
     *
     * A grant an operator makes by hand is the only way in today, which is the whole point
     * of the endpoint: the product has no billing yet, and everything downstream of the
     * subscription — the gates, the backoffice, what each client offers — can be built and
     * used against it. A payment provider's webhook, when there is one, calls the same
     * service rather than growing a second idea of what premium is.
     *
     * Answers with the account as the backoffice shows it, like every other action here,
     * so a row updates in place instead of needing a reload.
     */
    private fun Route.grantPremium() = post("/{id}/premium") {
        val dto = call.receive<PremiumGrantDTO>()
        val id = getPathId()
        // A form that failed to send its date must not read as "forever": see PremiumGrantDTO
        if (!dto.forever && dto.until == null) {
            throw BadRequestException(BadRequestCause.PREMIUM_GRANT_HAS_NO_TERM)
        }
        // Read back at UTC, which is what `AdminUserInfo.premiumUntil` is written at: a date
        // an operator saves and reloads has to come back the day they picked.
        val until = if (dto.forever) null else LocalDateTime.ofEpochSecond(dto.until!!, 0, ZoneOffset.UTC)
        userService.grantPremium(id, until)
        call.respond(adminService.getUser(id))
    }

    /** Ends a grant of either kind, now. Nothing is refunded and nothing else changes. */
    private fun Route.revokePremium() = delete("/{id}/premium") {
        val id = getPathId()
        userService.revokePremium(id)
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
