package com.xavierclavel.controllers

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.models.User
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.services.EncryptionService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.UserSession
import com.xavierclavel.utils.getLocale
import com.xavierclavel.utils.logger
import shared.dto.GoogleOauthDto
import shared.dto.UserDTO
import shared.infodto.UserInfo
import shared.utils.URL.AUTH_URL
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import shared.dto.SessionDto
import java.util.UUID
import kotlin.text.trim

object AuthController: Controller(AUTH_URL) {
    val userService: UserService by inject(UserService::class.java)
    val redisService: RedisService by inject(RedisService::class.java)
    val encryptionService: EncryptionService by inject(EncryptionService::class.java)
    val configuration: Configuration by inject(Configuration::class.java)
    val redirects = mutableMapOf<String, String>()
    val applicationHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    override fun Route.routes() {
        authenticate("auth-basic") {
            login()
        }
        authenticate("auth-oauth-google") {
            loginGoogleOauth()
            callbackGoogleOauth()
        }
        authenticate("auth-session", "bearer-auth") {
            whoami()
        }
        logout()
        verifyUser()
        signup()
        requestPasswordReset()
        resetPassword()
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private fun Route.login() = post("/login") {
        val mail = call.principal<UserIdPrincipal>()?.name.toString()
        val user = userService.findByMail(mail).toInfo()
        val sessionId = createSession(user)
        call.sessions.set(UserSession(sessionId))
        call.respond(SessionDto(sessionId))
    }

    private fun Route.loginGoogleOauth() = get("/login-oauth-google") {
    }

    private fun Route.callbackGoogleOauth() = get("/callback-oauth-google") {
        val currentPrincipal = call.principal<OAuthAccessTokenResponse.OAuth2>()

        currentPrincipal?.let { principal ->
            val state = principal.state
            val accessToken = principal.accessToken

            if (state != null) {
                val sessionId = googleOauthLogin(accessToken)

                val redirect = redirects[state]

                if (redirect == "app") {
                    call.respondRedirect("cooknco://login/callback?token=$sessionId")
                    return@get
                }

                if (redirect != null) {
                    call.respondRedirect(redirect)
                    return@get
                }
            }
        }

        call.respondRedirect("${configuration.frontend.url}/home")
    }


    private suspend fun RoutingContext.googleOauthLogin(
        oauthToken: String
    ): String {
        val data = applicationHttpClient.get("https://openidconnect.googleapis.com/v1/userinfo") {
            bearerAuth(oauthToken)
        }.bodyAsText()
        val response = try {
            Json.decodeFromString<GoogleOauthDto>(data)
        } catch (e: SerializationException) {
            logger.info {"Failed to parse the following data: $data"}
            throw UnauthorizedException(UnauthorizedCause.OAUTH_FAILED)
        }
        var user = userService.findEntityByGoogleId(response.sub)
        if (user != null) {
            val sessionId = createSession(user.toInfo())
            call.sessions.set(UserSession(sessionId))
            return sessionId
        }
        if (userService.findEntityByMail(response.email) != null) {
            //todo: merge accounts
            throw UnauthorizedException(UnauthorizedCause.OAUTH_NOT_SETUP)
        }

        user = createGoogleOauthUser(response)
        val sessionId = createSession(user.toInfo())
        call.sessions.set(UserSession(sessionId))
        return sessionId
    }

    private fun RoutingContext.createGoogleOauthUser(oauthDto: GoogleOauthDto): User {
        val baseName = oauthDto.name?.trim() ?: UUID.randomUUID().toString()
        var name = baseName
        var index = 1
        while (userService.existsByUsername(name)) {
            name = "$baseName-$index"
            index++
        }
        val userDTO = UserDTO(
            username = name,
            mail = oauthDto.email,
            googleId = oauthDto.sub
            )
        val userCreated = userService.createUser(userDTO, true)
        logger.info {"Account created through Google Oauth by ${userCreated.username}"}
        return userCreated
    }

    private fun Route.logout() = post("/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.verifyUser() = post("/verify") {
        val token = call.queryParameters["token"] ?: throw BadRequestException(BadRequestCause.TOKEN_MISSING)
        logger.info {"'$token'"}
        val user = userService.verifyUser(token)
        logger.info {"Account verified : ${user.username}"}
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.whoami() = get("/me") {
        val userInfo = userService.getUser(getSessionUserId()) ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)
        call.respond(userInfo)
    }

    private fun Route.signup() = post("/signup") {
        val userDTO = call.receive(UserDTO::class)
        userDTO.username = userDTO.username.trim()
        logger.info {userDTO}
        if (userService.existsByUsername(userDTO.username)) throw BadRequestException(BadRequestCause.USERNAME_ALREADY_USED)
        if (userService.existsByMail(userDTO.mail)) throw BadRequestException(BadRequestCause.MAIL_ALREADY_USED)

        val userCreated = userService.createUser(userDTO, false)
        logger.info {"Account created through basic auth by ${userCreated.username}"}
        call.respond(HttpStatusCode.Created, userCreated.toInfo())
    }

    //TODO: send mail to user with verification code to send through another endpoint to chose a new password
    private fun Route.requestPasswordReset() = delete("/password/reset/{mail}") {
        val mail = call.parameters["mail"] ?: throw BadRequestException(BadRequestCause.MAIL_MISSING)
        try {
            val token = userService.requestPasswordReset(mail)
            call.respond(HttpStatusCode.OK)
        } catch (e: NotFoundException) {
            call.respond(HttpStatusCode.OK)
        } catch (e: BadRequestException) {
            if (e.message == BadRequestCause.OAUTH_ONLY.key) {
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    private fun Route.resetPassword() = put("/password/reset/{token}") {
        val token = call.parameters["token"] ?: throw UnauthorizedException(UnauthorizedCause.INVALID_TOKEN)
        val password = call.queryParameters["password"] ?: throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        userService.resetPassword(token, password)
        call.respond(HttpStatusCode.OK)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun RoutingContext.getOptionalSessionId(): Long? {
        val session = call.sessions.get<UserSession>() ?: return null
        val userId = redisService.getSessionUserId(session.sessionId)
        if (userId == null) {
            call.sessions.clear<UserSession>()
            throw UnauthorizedException(UnauthorizedCause.SESSION_NOT_FOUND)
        }
        return userId
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun RoutingContext.getSessionUserId(): Long {
        val session = call.sessions.get<UserSession>() ?: throw UnauthorizedException(UnauthorizedCause.SESSION_NOT_FOUND)
        val userId = redisService.getSessionUserId(session.sessionId)
        if (userId == null) {
            call.sessions.clear<UserSession>()
            throw UnauthorizedException(UnauthorizedCause.SESSION_NOT_FOUND)
        }
        return userId
    }

    private suspend fun RoutingContext.createSession(user: UserInfo): String {
        userService.registerUserActivity(user.id)
        val sessionId = UUID.randomUUID().toString()
        redisService.createSession(sessionId, user)
        return sessionId
    }

}