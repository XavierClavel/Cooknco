package com.xavierclavel

import com.xavierclavel.config.appModules
import com.xavierclavel.controllers.AdminController
import com.xavierclavel.controllers.AppVersionController
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
import com.xavierclavel.controllers.McpController
import com.xavierclavel.controllers.OAuthController
import com.xavierclavel.controllers.OAuthMetadataController
import com.xavierclavel.controllers.NotificationController
import com.xavierclavel.controllers.RecipeNotesController
import com.xavierclavel.controllers.ReportController
import com.xavierclavel.controllers.UnitController
import com.xavierclavel.controllers.UserController
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.ServiceUnavailableException
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
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedWriteChannelException
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
        // The name a download saves itself under. Only a response header the server opts into
        // is readable cross-origin, and in development the app runs off a different origin
        // than the API.
        exposeHeader(HttpHeaders.ContentDisposition)
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
        exception<ServiceUnavailableException> { call, error ->
            call.respond(HttpStatusCode.ServiceUnavailable, error.message ?: "Unknown error")
        }
        exception<BadRequestException> { call, error ->
            call.respond(HttpStatusCode.BadRequest, error.message ?: "Unknown error")
        }
        // A client that hangs up mid-response, which is neither a fault nor something it can
        // be told about: the status line left before it did, so there is no status left to
        // set. Both shapes the disconnect arrives in are named, and nothing wider: a plain
        // IOException from a route is a real failure and belongs in the handler below.
        //
        // Caught rather than merely not answered because the log tail is where this happens
        // constantly — the backoffice reopens its EventSource on every filter change — and
        // an ERROR there is worse than noise. It goes into the ring buffer, the tail that
        // replaces the closed one replays it as though the server had broken, and the
        // overview's "errors in logs" alert counts it. Watching the logs must not be what
        // makes them look bad.
        exception<ChannelWriteException> { call, error -> logClientGone(call, error) }
        exception<ClosedWriteChannelException> { call, error -> logClientGone(call, error) }
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

/**
 * A departed client, logged at debug so it stays out of the ring buffer the backoffice
 * reads — and so a stream that really is failing can still be looked into by raising the
 * level.
 */
private fun logClientGone(call: ApplicationCall, error: Throwable) {
    logger.debug { "Call to ${call.request.path()} ended when the client disconnected: ${error.message}" }
}

//Controllers declaration
fun Application.serveRoutes() = routing {
    authenticate("auth-session", "bearer-auth") {
        serve(LikeController)
        serve(DashboardController)
        serve(FollowController)
        serve(RecipeNotesController)
    }
    serve(IngredientController)
    serve(UnitController)
    serve(UserController)
    serve(HealthController)
    // Unauthenticated on purpose: asked at launch, before there is a session. See the controller.
    serve(AppVersionController)
    serve(ImageController)
    serve(RecipeController)
    serve(CookbookController)
    serve(AuthController)
    serve(NotificationController)
    serve(ReportController)
    // Not an API: the documents behind the public app routes people share. See the controller.
    serve(LinkPreviewController)
    // Resolves its own bearer token so the 401 can point at the metadata below, and refuses
    // the session cookie the rest of the API accepts. See the controller.
    serve(McpController)
    // The OAuth 2.1 authorization server the MCP endpoint sends its clients to, and the two
    // discovery documents that lead them there. Unauthenticated by necessity: a client reads
    // them precisely because it has no credentials yet.
    serve(OAuthMetadataController)
    serve(OAuthController)
    serve(AdminController)
    // Declares its own admin gate, like AdminController. See the controller.
    serve(ExportController)
    // Outside the admin gate on purpose: its only caller is mail-service, and nginx never
    // proxies this prefix, so it is unreachable from outside the cluster. See the controller.
    serve(InternalMailTemplateController)
}
