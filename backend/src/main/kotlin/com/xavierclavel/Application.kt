package com.xavierclavel

import com.xavierclavel.config.appModules
import com.xavierclavel.controllers.AuthController
import com.xavierclavel.controllers.CookbookController
import com.xavierclavel.controllers.DashboardController
import com.xavierclavel.controllers.ExportController
import com.xavierclavel.controllers.FollowController
import com.xavierclavel.controllers.HealthController
import com.xavierclavel.controllers.IngredientController
import com.xavierclavel.controllers.RecipeController
import com.xavierclavel.controllers.ImageController
import com.xavierclavel.controllers.LikeController
import com.xavierclavel.controllers.NotificationController
import com.xavierclavel.controllers.RecipeNotesController
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
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, cause.message ?: "Unknown error")
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, cause.message ?: "Unknown error")
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, cause.message ?: "Unknown error")
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "Unknown error")
        }
        exception<Throwable> { call, cause ->
            logger.error { "Call to ${call.request.path()} failed with error ${cause.stackTraceToString()}" }
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
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
    authenticate("auth-session") {
        serve(ExportController)
        serve(LikeController)
        serve(DashboardController)
        serve(FollowController)
        serve(RecipeNotesController)
    }
    serve(IngredientController)
    serve(UserController)
    serve(HealthController)
    serve(ImageController)
    serve(RecipeController)
    serve(CookbookController)
    serve(AuthController)
    serve(NotificationController)
}
