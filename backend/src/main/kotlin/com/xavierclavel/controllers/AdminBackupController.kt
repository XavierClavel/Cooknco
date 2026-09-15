package com.xavierclavel.controllers

import com.xavierclavel.services.BackupService
import com.xavierclavel.utils.Controller
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.java.KoinJavaComponent.inject

/**
 * The database backup volume, for the backoffice backups tab.
 *
 * One read endpoint and nothing else: see [BackupService] for why there is no route here
 * that deletes a dump or serves one.
 */
object AdminBackupController: Controller("backups") {
    val backupService: BackupService by inject(BackupService::class.java)

    override fun Route.routes() {
        getOverview()
    }

    private fun Route.getOverview() = get {
        call.respond(backupService.buildOverview())
    }
}
