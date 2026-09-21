package com.xavierclavel.plugins

import com.xavierclavel.controllers.AuthController
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.UserSession
import com.xavierclavel.utils.logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.basic
import io.ktor.server.auth.oauth
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.client.engine.cio.*
import io.ktor.server.auth.bearer
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject
import shared.enums.Locale

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.configureAuthentication() {
    val userService : UserService by inject<UserService>()
    val redisService: RedisService by inject<RedisService>()
    val configuration: Configuration by inject<Configuration>()



    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.httpOnly = true
            cookie.path = "/"
            cookie.maxAgeInSeconds = RedisService.SESSION_TTL
            // Local dev runs on plain http, where a Secure cookie would never be sent back.
            cookie.secure = configuration.backend.url.startsWith("https")
            cookie.extensions["SameSite"] = "Lax"
        }
    }

    install(Authentication) {
        basic("auth-basic") {
            realm = "Access to the '/' path"
            validate { credentials ->
                val user = userService.findEntityByMail(credentials.name) ?: throw UnauthorizedException(UnauthorizedCause.INVALID_MAIL_OR_PASSWORD)
                if (user.passwordHash.isNullOrBlank()) throw BadRequestException(BadRequestCause.OAUTH_ONLY)
                if (!user.isVerified) throw UnauthorizedException(UnauthorizedCause.USER_NOT_VERIFIED)
                if (user.isBanned) throw UnauthorizedException(UnauthorizedCause.ACCOUNT_BANNED)
                if (user.isSuspended()) throw UnauthorizedException(UnauthorizedCause.ACCOUNT_SUSPENDED)
                if (userService.isPasswordValid(credentials.password, user.passwordHash!!)) {
                    UserIdPrincipal(credentials.name)
                } else {
                    throw UnauthorizedException(UnauthorizedCause.INVALID_MAIL_OR_PASSWORD)
                }
            }
        }

        bearer("bearer-auth") {
            authenticate { tokenCredential ->
                val session = redisService.getSession(tokenCredential.token)
                if (session != null) {
                    redisService.touchSession(tokenCredential.token)
                    UserIdPrincipal(session.userId.toString())
                } else {
                    null
                }
            }
        }
        oauth("auth-oauth-google") {
            // Configure oauth authentication
            urlProvider = { "${configuration.backend.url}/auth/callback-oauth-google" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://oauth2.googleapis.com/token",
                    requestMethod = HttpMethod.Post,
                    clientId = configuration.oauth.google.clientId,
                    clientSecret = configuration.oauth.google.clientSecret,
                    defaultScopes = listOf("openid", "profile", "email"),
                    extraAuthParameters = listOf("access_type" to "offline"),
                    onStateCreated = { call, state ->
                        //saves new state with redirect url value
                        call.request.queryParameters["redirect"]?.let {
                            AuthController.redirects[state] = it
                        }
                        // Google's callback carries nothing of ours, so the client's language
                        // has to be remembered here or not at all
                        call.request.queryParameters["locale"]
                            ?.let { reported -> Locale.entries.find { it.name.equals(reported, ignoreCase = true) } }
                            ?.let { AuthController.oauthLocales[state] = it }
                    }
                )
            }
            client = AuthController.applicationHttpClient
        }

        session<UserSession>("auth-session") {
            validate { session ->
                if (redisService.hasSession(session.sessionId)) {
                    // Rolling session: an active user keeps their session alive indefinitely.
                    if (redisService.touchSession(session.sessionId)) sessions.set(session)
                    session
                } else {
                    null
                }
            }
            challenge {
                throw UnauthorizedException(UnauthorizedCause.SESSION_NOT_FOUND)
            }
        }
        session<UserSession>("admin-session") {
            validate { session ->
                redisService.getAdminSession(session.sessionId)?.also {
                    if (redisService.touchSession(session.sessionId)) sessions.set(session)
                }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, UnauthorizedCause.SESSION_NOT_FOUND.key)
            }
        }
    }



}