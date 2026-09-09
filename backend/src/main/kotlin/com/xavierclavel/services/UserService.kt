package com.xavierclavel.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.models.User
import com.xavierclavel.models.query.QNotification
import com.xavierclavel.models.query.QReport
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
        findEntityById(userId) ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)

    fun findEntityById(userId: Long) : User? =
        QUser().id.eq(userId).findOne()

    /**
     * Decrypts an account's mail address, for the admin backoffice. Returns a placeholder
     * rather than throwing when the ciphertext cannot be read with the current AES key, so
     * one unreadable row does not break a whole listing.
     */
    fun readMail(user: User): String =
        try {
            if (user.mailEncrypted.isBlank()) "" else encryptionService.decrypt(user.mailEncrypted)
        } catch (e: Exception) {
            logger.warn { "Could not decrypt mail of user ${user.id}: ${e.message}" }
            "<unreadable>"
        }

    /** Mail addresses are only searchable by exact match, since they are stored encrypted. */
    fun hashMail(mail: String): String = encryptionService.hash(mail)

    fun countAdmins() =
        QUser().role.eq(UserRole.ADMIN).findCount()

    fun countUnverifiedUsers() =
        QUser().isVerified.eq(false).findCount()

    fun countBannedUsers() =
        QUser().isBanned.eq(true).findCount()

    fun countSuspendedUsers() =
        QUser()
            .isBanned.eq(false)
            .suspendedUntil.gt(LocalDateTime.now())
            .findCount()

    fun countUsersJoinedSince(since: LocalDateTime) =
        QUser().joinDate.gt(since).findCount()

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

    fun deleteUserById(userId: Long): Int {
        detachReports(userId)
        detachNotifications(userId)
        return QUser().id.eq(userId).delete()
    }

    /**
     * Anonymises the reports an account filed or resolved instead of deleting them, so the
     * moderation history survives the account. Also required for the delete to go through
     * at all: reports.reporter_id / resolved_by_id are ON DELETE RESTRICT.
     */
    private fun detachReports(userId: Long) {
        QReport().reporter.id.eq(userId).findList().forEach { it.reporter = null; it.update() }
        QReport().resolvedBy.id.eq(userId).findList().forEach { it.resolvedBy = null; it.update() }
    }

    /**
     * Drops the deleted account from the notifications it caused, keeping the notifications
     * themselves. Required for the delete to go through at all, for the reason
     * [detachReports] is: notifications.actor_id is ON DELETE RESTRICT. The rows *addressed*
     * to the account need no such care — they cascade with it.
     */
    private fun detachNotifications(userId: Long) {
        QNotification().actor.id.eq(userId).findList().forEach { it.actor = null; it.update() }
    }

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