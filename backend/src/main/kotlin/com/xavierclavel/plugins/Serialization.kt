package com.xavierclavel.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * JSON for every routed call.
 *
 * Installed on the routing root rather than on the application so that a single route may
 * override it: Ktor refuses a route-scoped plugin whose key is already installed application-wide
 * ("Installing RouteScopedPlugin to application and route is not supported"), and `/mcp` needs its
 * own settings — JSON-RPC replies must carry no explicit nulls and no class discriminator. See
 * [com.xavierclavel.controllers.McpController]. Every response the API serializes is answered from
 * inside `routing`, so nothing loses its converter by the move.
 */
fun Application.configureSerialization() {
    routing {
        install(ContentNegotiation) {
            json()
        }
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
    }
}
