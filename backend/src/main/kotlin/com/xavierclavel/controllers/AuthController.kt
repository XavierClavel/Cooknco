package com.xavierclavel.controllers

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.models.User
import com.xavierclavel.plugins.RedisService
import com.xavierclavel.plugins.SessionData
import com.xavierclavel.services.EncryptionService
import com.xavierclavel.services.UserService
import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.Controller
import com.xavierclavel.utils.UserSession
import com.xavierclavel.utils.logger
import shared.dto.GoogleOauthDto
import shared.dto.UserDTO
import shared.enums.Locale
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
import io.ktor.server.application.ApplicationCall
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
import org.hibernate.Session
import org.koin.java.KoinJavaComponent.inject
import shared.dto.SessionDto
import java.util.UUID
import kotlin.text.trim

object AuthController: Controller(AUTH_URL) {
    val userService: UserService by inject(UserService::class.java)
    val redisService: RedisService by inject(RedisService::class.java)
    val configuration: Configuration by inject(Configuration::class.java)
    val redirects = mutableMapOf<String, String>()

    /**
     * The language each in-flight Google sign-in was started from, keyed by OAuth state.
     *
     * Google's callback is a redirect it builds itself, so the only moment the client's
     * language is visible in that flow is the moment it leaves — captured next to
     * `?redirect=` in `configureAuthentication`, and consumed once when the state comes back.
     */
    val oauthLocales = mutableMapOf<String, Locale>()
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

    /**
     * Signing in also reports the client's language, through `?locale=`.
     *
     * This is the web's equivalent of what registering a device does for the app: it is the
     * one request every client makes that says something about the person rather than about
     * the page they happened to open. Only an account that has no language yet takes it —
     * [UserService.adoptLocale] — so signing in from a borrowed English laptop cannot
     * rewrite a choice made in the settings.
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private fun Route.login() = post("/login") {
        val mail = call.principal<UserIdPrincipal>()?.name.toString()
        val entity = userService.findByMail(mail)
        reportedLocale()?.let { userService.adoptLocale(entity, it) }
        val sessionId = createSession(entity.toInfo())
        call.sessions.set(UserSession(sessionId))
        logger.info { "User ${entity.id} (${entity.username}) signed in" }
        call.respond(SessionDto(sessionId))
    }

    /**
     * The language the calling client says it is running in, or null.
     *
     * Lenient on purpose, unlike [com.xavierclavel.utils.getLocale]: this rides along with
     * requests whose real job is signing in or signing up, and neither should fail over the
     * spelling of a language. Unreadable means unknown, and unknown has a well-defined
     * meaning here — see `User.locale`.
     */
    private fun RoutingContext.reportedLocale(): Locale? =
        call.request.queryParameters["locale"]
            ?.takeIf { it.isNotBlank() }
            ?.let { reported -> Locale.entries.find { it.name.equals(reported, ignoreCase = true) } }

    private fun Route.loginGoogleOauth() = get("/login-oauth-google") {
    }

    private fun Route.callbackGoogleOauth() = get("/callback-oauth-google") {
        val currentPrincipal = call.principal<OAuthAccessTokenResponse.OAuth2>()

        currentPrincipal?.let { principal ->
            val state = principal.state
            val accessToken = principal.accessToken

            if (state != null) {
                // Consumed rather than read: one sign-in, one state, and nothing later can
                // match it again
                val sessionId = googleOauthLogin(accessToken, oauthLocales.remove(state))

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
        oauthToken: String,
        reported: Locale?,
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
            if (user.isBanned) throw UnauthorizedException(UnauthorizedCause.ACCOUNT_BANNED)
            if (user.isSuspended()) throw UnauthorizedException(UnauthorizedCause.ACCOUNT_SUSPENDED)
            // Same reporting as a password sign-in: a returning account with no language
            // takes the one its client came in with
            reported?.let { userService.adoptLocale(user, it) }
            // Bound to a val: `user` is reassigned further down, so it does not smart-cast
            // inside the logging lambda.
            val account = user
            val sessionId = createSession(account.toInfo())
            call.sessions.set(UserSession(sessionId))
            logger.info { "User ${account.id} (${account.username}) signed in through Google Oauth" }
            return sessionId
        }
        if (userService.findEntityByMail(response.email) != null) {
            //todo: merge accounts
            throw UnauthorizedException(UnauthorizedCause.OAUTH_NOT_SETUP)
        }

        user = createGoogleOauthUser(response, reported)
        val sessionId = createSession(user.toInfo())
        call.sessions.set(UserSession(sessionId))
        return sessionId
    }

    private fun RoutingContext.createGoogleOauthUser(oauthDto: GoogleOauthDto, reported: Locale?): User {
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
        val userCreated = userService.createUser(userDTO, true, reported)
        logger.info {"Account created through Google Oauth by ${userCreated.username}"}
        return userCreated
    }

    private fun Route.logout() = post("/logout") {
        val userId = getOptionalSessionId()
        val bearerToken = call.getBearerToken()
        if (bearerToken != null) {
            redisService.deleteSession(bearerToken)
        }
        val session = call.sessions.get<UserSession>()
        if (session != null) {
            redisService.deleteSession(session.sessionId)
            call.sessions.clear<UserSession>()
        }
        logger.info { "User ${userId ?: "unknown"} signed out" }
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.verifyUser() = post("/verify") {
        val token = call.queryParameters["token"] ?: throw BadRequestException(BadRequestCause.TOKEN_MISSING)
        val user = userService.verifyUser(token)
        logger.info { "Account verified : ${user.username}" }
        call.respond(HttpStatusCode.OK)
    }

    private fun Route.whoami() = get("/me") {
        val userInfo = userService.getUser(getSessionUserId()) ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)
        call.respond(userInfo)
    }

    /**
     * Creates an account, in the language its client says it is running in.
     *
     * `?locale=` is read leniently — absent or unreadable simply leaves the account without
     * one, and the fallbacks in `User.locale` apply. Refusing a signup over the spelling of
     * a language would trade an account for a preference, and the verification mail that
     * goes out next is the only thing riding on it.
     */
    private fun Route.signup() = post("/signup") {
        val userDTO = call.receive(UserDTO::class)
        userDTO.username = userDTO.username.trim()
        if (userService.existsByUsername(userDTO.username)) throw BadRequestException(BadRequestCause.USERNAME_ALREADY_USED)
        if (userService.existsByMail(userDTO.mail)) throw BadRequestException(BadRequestCause.MAIL_ALREADY_USED)

        val userCreated = userService.createUser(userDTO, false, reportedLocale())
        logger.info {"Account created through basic auth by ${userCreated.username}"}
        call.respond(HttpStatusCode.Created, userCreated.toInfo())
    }

    //TODO: send mail to user with verification code to send through another endpoint to chose a new password
    private fun Route.requestPasswordReset() = delete("/password/reset/{mail}") {
        val mail = call.parameters["mail"] ?: throw BadRequestException(BadRequestCause.MAIL_MISSING)
        try {
            val token = userService.requestPasswordReset(mail)
            logger.info { "A password reset was requested for $mail" }
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
        logger.info { "A password was reset through a reset token" }
        call.respond(HttpStatusCode.OK)
    }

    fun ApplicationCall.getBearerToken(): String? {
        val header = request.headers["Authorization"] ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.removePrefix("Bearer ").trim()
    }

    suspend fun ApplicationCall.getBearerTokenUserId(): Long? {
        val token = getBearerToken() ?: return null
        return redisService.getSessionUserId(token)
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun RoutingContext.getOptionalSessionId(): Long? {
        val tokenUserId = call.getBearerTokenUserId()
        if (tokenUserId != null) {
            return tokenUserId
        }
        val session = call.sessions.get<UserSession>() ?: return null
        val userId = redisService.getSessionUserId(session.sessionId)
        if (userId == null) {
            call.sessions.clear<UserSession>()
        }
        return userId
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun RoutingContext.getSessionUserId(): Long {
        val tokenUserId = call.getBearerTokenUserId()
        if (tokenUserId != null) {
            return tokenUserId
        }
        val sessionId = getSessionId()
        val userId = redisService.getSessionUserId(sessionId)
        if (userId == null) {
            call.sessions.clear<UserSession>()
            throw UnauthorizedException(UnauthorizedCause.SESSION_NOT_FOUND)
        }
        return userId
    }


    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun RoutingContext.getSessionId(): String {
        val session =
            call.sessions.get<UserSession>() ?: throw UnauthorizedException(UnauthorizedCause.SESSION_NOT_FOUND)
        return session.sessionId
    }


    private suspend fun RoutingContext.createSession(user: UserInfo): String {
        userService.registerUserActivity(user.id)
        val sessionId = UUID.randomUUID().toString()
        redisService.createSession(sessionId, user)
        return sessionId
    }

}