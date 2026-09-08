package com.xavierclavel

import com.xavierclavel.config.appModules
import com.xavierclavel.controllers.AdminController
import com.xavierclavel.controllers.AuthController
import com.xavierclavel.controllers.CookbookController
import com.xavierclavel.controllers.DashboardController
import com.xavierclavel.controllers.ExportController
import com.xavierclavel.controllers.FollowController
import com.xavierclavel.controllers.HealthController
import com.xavierclavel.controllers.IngredientController
import com.xavierclavel.controllers.InternalMailTemplateController
import com.xavierclavel.controllers.RecipeController
import com.xavierclavel.controllers.ImageController
import com.xavierclavel.controllers.LikeController
import com.xavierclavel.controllers.LinkPreviewController
import com.xavierclavel.controllers.NotificationController
import com.xavierclavel.controllers.RecipeNotesController
import com.xavierclavel.controllers.ReportController
import com.xavierclavel.controllers.UnitController
import com.xavierclavel.controllers.UserController
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.utils.serve
import com.xavierclavel.plugins.*
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.logger
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import org.koin.core.context.GlobalContext.startKoin
import org.koin.ktor.ext.inject

fun main() {
    startKoin {
        modules(
            appModules,
    )}

    DatabaseManager.init()

    logger.info { " Server started." }
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
    logger.info { " Server closed." }
}

fun Application.module() {
    configureSerialization()
    configureRouting()
    install(SSE)
    install(CORS) {
        anyHost()
        anyMethod()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true

    }
    install(StatusPages) {
        exception<UnauthorizedException> { call, error ->
            call.respond(HttpStatusCode.Unauthorized, error.message ?: "Unknown error")
        }
        exception<ForbiddenException> { call, error ->
            call.respond(HttpStatusCode.Forbidden, error.message ?: "Unknown error")
        }
        exception<NotFoundException> { call, error ->
            call.respond(HttpStatusCode.NotFound, error.message ?: "Unknown error")
        }
        exception<BadRequestException> { call, error ->
            call.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
        }
        exception<Throwable> { call, error ->
            logger.error { "Call to ${call.request.path()} failed with error ${error.stackTraceToString()}" }
            call.respond(HttpStatusCode.InternalServerError, error.message ?: "Unknown error")
        }

    }
    logger.info { "Status pages configured" }
    configureAuthentication()
    serveRoutes()
    scheduleJob()

    val userService: UserService by inject()
    userService.setupDefaultAdmin()

}

//Controllers declaration
fun Application.serveRoutes() = routing {
    authenticate("auth-session", "bearer-auth") {
        serve(ExportController)
        serve(LikeController)
        serve(DashboardController)
        serve(FollowController)
        serve(RecipeNotesController)
    }
    serve(IngredientController)
    serve(UnitController)
    serve(UserController)
    serve(HealthController)
    serve(ImageController)
    serve(RecipeController)
    serve(CookbookController)
    serve(AuthController)
    serve(NotificationController)
    serve(ReportController)
    // Not an API: the documents behind the public app routes people share. See the controller.
    serve(LinkPreviewController)
    serve(AdminController)
    // Outside the admin gate on purpose: its only caller is mail-service, and nginx never
    // proxies this prefix, so it is unreachable from outside the cluster. See the controller.
    serve(InternalMailTemplateController)
}
