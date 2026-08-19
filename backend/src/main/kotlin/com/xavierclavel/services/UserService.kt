package com.xavierclavel.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.models.User
import com.xavierclavel.models.query.QUser
import com.xavierclavel.utils.DbTransaction.insertAndGet
import com.xavierclavel.utils.DbTransaction.updateAndGet
import com.xavierclavel.utils.logger
import shared.dto.UserDTO
import shared.dto.UserSettingsDTO
import shared.infodto.UserInfo
import shared.enums.UserRole
import shared.overviewdto.UserOverview
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.events.AccountVerificationRequestedEvent
import shared.events.EventProducer
import shared.events.PasswordResetRequestedEvent
import shared.events.UserCreatedEvent
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class UserService: KoinComponent {
    val encryptionService: EncryptionService by inject()
    val followService: FollowService by inject()
    val eventProducerService: EventProducer by inject()


    fun countAll() =
        QUser().findCount()

    //Defined as users who logged in in the last 30 days
    fun countActiveUsers() =
        QUser()
            .where().lastActivityDate.gt(LocalDateTime.now(ZoneOffset.UTC).minusMonths(1))
            .findCount()

    fun getEntityById(userId: Long) : User =
        QUser().id.eq(userId).findOne() ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)

    fun findByUsername(username: String) : User? =
        QUser().username.eq(username).findOne()

    fun findEntityByMail(mail: String): User? =
        QUser().mailHash.eq(encryptionService.hash(mail)).findOne()

    fun findEntityByGoogleId(googleId: String): User? =
        QUser().googleId.eq(googleId).findOne()

    fun findByMail(mail: String) : User {
        return findEntityByMail(mail) ?: throw NotFoundException(NotFoundCause.MAIL_NOT_FOUND)
    }


    fun listAllTokens() {
        QUser().findList().forEach {logger.info {"'${it.token}'"}}
    }

    fun requestPasswordReset(mail: String): String {
        val user = findByMail(mail)
        if (user.passwordHash == null) throw BadRequestException(BadRequestCause.OAUTH_ONLY)
        val token = generateToken()
        user.updateToken(token)
        eventProducerService.produceEvent { PasswordResetRequestedEvent(user.id, token) }
        return token
    }

    fun generateToken(): String {
        var token = UUID.randomUUID().toString()
        while (isTokenUsed(token)) {
            token = UUID.randomUUID().toString()
        }
        return token
    }

    fun isTokenUsed(token: String): Boolean =
        QUser().token.eq(token).exists()

    fun findByToken(token: String) : User =
        QUser().token.eq(token).findOne() ?: throw UnauthorizedException(UnauthorizedCause.INVALID_TOKEN)


    fun existsById(id: Long) = QUser().id.eq(id).exists()
    fun existsByMail(mail: String) = QUser().mailHash.eq(encryptionService.hash(mail)).exists()
    fun existsByUsername(username: String) = QUser().username.eq(username).exists()

    fun createUser(userDTO: UserDTO, verified: Boolean): User =
        User.from(
            userDTO = userDTO,
            passwordHash = encryptionService.encryptPassword(userDTO.password),
            mailEncrypted = encryptionService.encrypt(userDTO.mail),
            mailHash = encryptionService.hash(userDTO.mail),
            token = UUID.randomUUID().toString(),
        )
        .apply {
            if (verified) {
                this.verify()
            }
        }
        .insertAndGet()
        .apply {
            eventProducerService.produceEvent { UserCreatedEvent(this.id, this.username, this.mailEncrypted) }
            if (!verified) {
                eventProducerService.produceEvent { AccountVerificationRequestedEvent(this.id, token) }
            }
        }

    fun editUser(id: Long, userDTO: UserDTO): UserInfo {
        val currentUser = getEntityById(id)
        userDTO.username = userDTO.username.trim()
        if (userDTO.username != currentUser.username && existsByUsername(userDTO.username)) {
            throw BadRequestException(BadRequestCause.USERNAME_ALREADY_USED)
        }
        return currentUser.merge(userDTO).updateAndGet().toInfo()
    }

    fun registerUserActivity(id: Long) = getEntityById(id).registerNewActivity()

    fun deleteUserById(userId: Long) =
        QUser().id.eq(userId).delete()

    fun deleteUserByUsername(username: String) =
        QUser().username.eq(username).delete()

    fun getUserByUsername(username: String) : UserInfo? {
        return findByUsername(username)?.toInfo()
    }

    fun getUser(id: Long) : UserInfo =
        getEntityById(id).toInfo()

    fun listUsers(paging: Paging): List<UserOverview> =
        QUser().setPaging(paging).findList().map { it.toOverview() }

    //TODO :parameterize password
    fun setupDefaultAdmin() {
        shared.utils.logger.info {"setting up default admin"}
        if (findByUsername("admin") != null) return
        val user = createUser(
            UserDTO(
            username = "admin",
            password = "Passw0rd",
            mail = "admin@mail.com",
        ), verified = true)
            .setRole(UserRole.ADMIN)
            .updateAndGet()
        logger.info { "user ${user.id} created" }
    }

    fun verifyUser(token:String): UserInfo {
        val user = findByToken(token)
        if (!user.isTokenValid()) throw UnauthorizedException(UnauthorizedCause.INVALID_TOKEN)
        return user.verify().updateAndGet().toInfo()
    }

    fun isPasswordValid(id: Long, password: String): Boolean =
        isPasswordValid(password, getEntityById(id).passwordHash!!)

    fun isPasswordValid(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    fun updatePassword(id: Long, password: String): UserInfo {
        val user = getEntityById(id)
        if (user.passwordHash == null) throw BadRequestException(BadRequestCause.OAUTH_ONLY)
        return getEntityById(id).updatePassword(encryptionService.encryptPassword(password)!!).updateAndGet().toInfo()
    }


    fun setRole(id: Long, role: UserRole) =
        getEntityById(id).setRole(role).updateAndGet().toInfo()

    fun resetPassword(token: String, password: String) {
        val user = findByToken(token)
        if (!user.isTokenValid()) throw UnauthorizedException(UnauthorizedCause.INVALID_TOKEN)
        if (user.passwordHash == null) throw BadRequestException(BadRequestCause.OAUTH_ONLY)
        user.useToken()
        user.updatePassword(encryptionService.encryptPassword(password)!!)
        user.update()
    }

    fun validateUser(id: Long) =
        getEntityById(id).verify().updateAndGet()

    fun updateSettings(id: Long, userSettingsDTO: UserSettingsDTO): UserInfo {
        val user = getEntityById(id).updateSettings(userSettingsDTO).updateAndGet()
        if (!user.autoAcceptsFollowRequests()) return user.toInfo()
        // Nothing left to review: clear the backlog and reload to get up-to-date counts
        followService.acceptAllPendingFollowRequests(id)
        return getEntityById(id).toInfo()
    }

    fun getSettings(id: Long): UserSettingsDTO =
        getEntityById(id).getSettings()

    fun search(searchString: String?, paging: Paging): Pair<Int, List<UserInfo>> {
        val query = QUser()
            .apply {
                if (!searchString.isNullOrBlank()) {
                    this.username.ilike("%$searchString%")
                }
            }

        return Pair(query.findCount(), query.setPaging(paging).findList().map{ it.toInfo() })
    }




}