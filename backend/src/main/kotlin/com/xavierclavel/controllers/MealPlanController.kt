package com.xavierclavel.controllers

import com.xavierclavel.controllers.AuthController.getSessionUserId
import com.xavierclavel.services.MealPlanService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.getPathId
import com.xavierclavel.utils.getStringQueryParam
import com.xavierclavel.utils.logEdit
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.inject
import shared.dto.MealPlanEntryDTO
import shared.dto.MealPlanEntryEditDTO
import shared.utils.URL.MEAL_PLAN_URL

/**
 * The caller's own meal plan. See `MealPlanService`.
 *
 * Premium, every route of it, through [UserService.checkPremiumAccess] — the one place premium is
 * spelled out — and checked before anything is read. Reading included: a plan made while the
 * grant ran stays where it is when it ends, and comes back with the next one.
 */
object MealPlanController : Controller(MEAL_PLAN_URL) {
    private val mealPlanService: MealPlanService by inject(MealPlanService::class.java)
    private val userService: UserService by inject(UserService::class.java)

    override fun Route.routes() {
        listEntries()
        suggestTitles()
        createEntry()
        updateEntry()
        deleteEntry()
    }

    /** `?from=2026-10-12&to=2026-10-18`, both days included. */
    private fun Route.listEntries() = get {
        val user = userService.checkPremiumAccess(getSessionUserId())
        val entries = mealPlanService.list(user.id, getStringQueryParam("from"), getStringQueryParam("to"))
        call.respond(entries)
    }

    private fun Route.suggestTitles() = get("/suggestions") {
        val user = userService.checkPremiumAccess(getSessionUserId())
        call.respond(mealPlanService.suggestions(user.id, getStringQueryParam("query")))
    }

    private fun Route.createEntry() = post {
        val user = userService.checkPremiumAccess(getSessionUserId())
        val entry = mealPlanService.create(user, call.receive<MealPlanEntryDTO>())
        logEdit { "Meal plan entry ${entry.id} added for ${entry.date} (${entry.slot})" }
        call.respond(HttpStatusCode.Created, entry)
    }

    private fun Route.updateEntry() = put("/{id}") {
        val user = userService.checkPremiumAccess(getSessionUserId())
        val entry = mealPlanService.update(user.id, getPathId(), call.receive<MealPlanEntryEditDTO>())
        logEdit { "Meal plan entry ${entry.id} edited, now ${entry.date} (${entry.slot})" }
        call.respond(entry)
    }

    private fun Route.deleteEntry() = delete("/{id}") {
        val user = userService.checkPremiumAccess(getSessionUserId())
        val id = getPathId()
        mealPlanService.delete(user.id, id)
        logEdit { "Meal plan entry $id removed" }
        call.respond(HttpStatusCode.OK)
    }
}
