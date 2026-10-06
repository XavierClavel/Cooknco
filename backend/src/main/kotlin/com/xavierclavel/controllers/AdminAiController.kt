package com.xavierclavel.controllers

import com.xavierclavel.services.AiUsageService
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.logEdit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import org.koin.java.KoinJavaComponent.inject
import shared.infodto.AiSettingsDTO

/**
 * What the AI features spend and the limits on them, for the backoffice AI tab.
 *
 * One read for the whole tab, and one write for the settings. The provider itself is not
 * editable here: its key is a credential that lives in the cluster's secret, and an admin
 * session is not a reason to be able to read it or swap it. What an operator steers from here
 * is how much may be spent — per account per day, and in total per month.
 */
object AdminAiController : Controller("ai") {
    val usageService: AiUsageService by inject(AiUsageService::class.java)

    override fun Route.routes() {
        getOverview()
        saveSettings()
    }

    private fun Route.getOverview() = get {
        call.respond(usageService.overview())
    }

    private fun Route.saveSettings() = put("/settings") {
        val saved = usageService.saveSettings(call.receive<AiSettingsDTO>())
        logEdit {
            "AI limits set to ${saved.dailyLimitPerUser} requests/account/day, " +
                "budget ${saved.monthlyBudget?.let { "$it ${saved.currency}/month" } ?: "none"}, " +
                "prices ${saved.inputPricePerMillion}/${saved.outputPricePerMillion} ${saved.currency} per M tokens"
        }
        call.respond(saved)
    }
}
