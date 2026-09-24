package com.xavierclavel.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ForbiddenCause
import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.exceptions.UnauthorizedCause
import com.xavierclavel.exceptions.UnauthorizedException
import com.xavierclavel.models.User
import com.xavierclavel.models.UserCounts
import com.xavierclavel.models.jointables.query.QCookbookUser
import com.xavierclavel.models.jointables.query.QFollow
import com.xavierclavel.models.jointables.query.QLike
import com.xavierclavel.models.query.QRecipe
import com.xavierclavel.models.query.QNotification
import com.xavierclavel.models.query.QReport
import com.xavierclavel.models.query.QUser
import com.xavierclavel.utils.DbTransaction.insertAndGet
import com.xavierclavel.utils.DbTransaction.updateAndGet
import com.xavierclavel.utils.countByParent
import com.xavierclavel.utils.logger
import shared.dto.UserDTO
import shared.dto.UserSettingsDTO
import shared.infodto.UserInfo
import shared.enums.Locale
import shared.enums.UserRole
import shared.overviewdto.UserOverview
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.EmailTemplateKind
import shared.events.EventProducer
import shared.events.UserCreatedEvent
import java.time.LocalDateTime
import java.util.UUID

class UserService: KoinComponent {
    val encryptionService: EncryptionService by inject()
    val deviceService: DeviceService by inject()
    val followService: FollowService by inject()
    val mailService: MailService by inject()
    val eventProducerService: EventProducer by inject()


    fun countAll() =
        QUser().findCount()

    //Defined as users who logged in in the last 30 days
    fun countActiveUsers() =
        QUser()
            .where().lastActivityDate.gt(LocalDateTime.now().minusMonths(1))
            .findCount()

    fun getEntityById(userId: Long) : User =
        findEntityById(userId) ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)

    fun findEntityById(userId: Long) : User? =
        QUser().id.eq(userId).findOne()

    /**
     * Every member profile a search engine may be pointed at.
     *
     * The same two flags [com.xavierclavel.services.LinkPreviewService] checks before it will
     * describe an account, and deliberately not a third: whatever is listed here has to be
     * exactly what the preview will render, or the sitemap advertises pages that come back as
     * the site's own default.
     */
    fun findPublicForSitemap(limit: Int): List<User> =
        QUser()
            .select(QUser.Alias.id)
            .isAccountPublic.isTrue
            .isBanned.isFalse
            .orderBy().id.asc()
            .setMaxRows(limit)
            .findList()

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
        mailService.send(EmailTemplateKind.PASSWORD_RESET, user, token)
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

    /**
     * @param locale the language the signing-up client is running in, when it said so.
     *   Worth taking at exactly this moment: the verification mail goes out below, before
     *   any device of theirs has had a chance to register, so this is the only thing that
     *   can put the very first mail an account receives in the right language.
     */
    fun createUser(userDTO: UserDTO, verified: Boolean, locale: Locale? = null): User =
        User.from(
            userDTO = userDTO,
            passwordHash = encryptionService.encryptPassword(userDTO.password),
            mailEncrypted = encryptionService.encrypt(userDTO.mail),
            mailHash = encryptionService.hash(userDTO.mail),
            token = UUID.randomUUID().toString(),
        )
        .apply {
            this.locale = locale
            if (verified) {
                this.verify()
            }
        }
        .insertAndGet()
        .apply {
            eventProducerService.produceEvent { UserCreatedEvent(this.id, this.username, this.mailEncrypted) }
            if (!verified) {
                mailService.send(EmailTemplateKind.ACCOUNT_VERIFICATION, this, token)
            }
        }

    fun editUser(id: Long, userDTO: UserDTO): UserInfo {
        val currentUser = getEntityById(id)
        userDTO.username = userDTO.username.trim()
        if (userDTO.username != currentUser.username && existsByUsername(userDTO.username)) {
            throw BadRequestException(BadRequestCause.USERNAME_ALREADY_USED)
        }
        return describe(currentUser.merge(userDTO).updateAndGet())
    }

    /**
     * Stamps the account's last connection. Called on every session creation — a password
     * sign-in, a Google one, and the session a Google signup opens straight away.
     *
     * A bulk update rather than a loaded entity, for correctness as much as for the query
     * it saves: this used to read the row and set the field on the returned object without
     * ever saving it, so the column kept the value it was inserted with and the backoffice
     * showed every account as last seen the day it joined.
     */
    fun registerUserActivity(id: Long) {
        QUser().id.eq(id)
            .asUpdate().set(QUser.Alias.lastActivityDate, LocalDateTime.now())
            .update()
    }

    /**
     * Just the name, for the label the session carries — a whole user read would be a row too
     * many on a path that runs once a day per session. See [com.xavierclavel.plugins.SessionData].
     */
    fun findUsername(id: Long): String? =
        QUser().id.eq(id).select(QUser.Alias.username).findOne()?.username

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
        return findByUsername(username)?.let { describe(it) }
    }

    fun getUser(id: Long) : UserInfo =
        describe(getEntityById(id))

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
        return describe(user.verify().updateAndGet())
    }

    fun isPasswordValid(id: Long, password: String): Boolean =
        isPasswordValid(password, getEntityById(id).passwordHash!!)

    fun isPasswordValid(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    fun updatePassword(id: Long, password: String): UserInfo {
        val user = getEntityById(id)
        if (user.passwordHash == null) throw BadRequestException(BadRequestCause.OAUTH_ONLY)
        return describe(getEntityById(id).updatePassword(encryptionService.encryptPassword(password)!!).updateAndGet())
    }


    fun setRole(id: Long, role: UserRole) =
        describe(getEntityById(id).setRole(role).updateAndGet())

    /**
     * The account behind a premium feature's request, or a refusal.
     *
     * The one place a premium gate is spelled out, so that adding a second paid feature is
     * a call to this and not a second opinion about what premium means. Read from the row
     * rather than from the session: a session is created at sign-in and cached in Redis for
     * thirty days, so a grant made this afternoon would otherwise reach the user whenever
     * they next logged out — and a revocation would take just as long to bite.
     *
     * @throws ForbiddenException when the account holds no running grant and does not
     *   moderate the product (see [User.hasPremiumAccess])
     */
    fun checkPremiumAccess(userId: Long): User =
        getEntityById(userId).also {
            if (!it.hasPremiumAccess()) throw ForbiddenException(ForbiddenCause.PREMIUM_REQUIRED)
        }

    /**
     * Makes an account premium, for good or until [until].
     *
     * @param until when a timed grant ends, or null for one with no end
     * @throws BadRequestException when [until] has already passed — a grant that is over
     *   before it is made is a mistyped year rather than a decision, and accepting it would
     *   report success while changing nothing an operator can see.
     */
    fun grantPremium(id: Long, until: LocalDateTime?): UserInfo {
        if (until != null && !until.isAfter(LocalDateTime.now())) {
            throw BadRequestException(BadRequestCause.PREMIUM_EXPIRY_IN_THE_PAST)
        }
        val user = getEntityById(id)
        if (until == null) user.grantPremiumForever() else user.grantPremiumUntil(until)
        return describe(user.updateAndGet())
    }

    /** Ends a grant of either kind, now. See [User.revokePremium]. */
    fun revokePremium(id: Long): UserInfo =
        describe(getEntityById(id).revokePremium().updateAndGet())

    fun countPremiumUsers() =
        QUser()
            .or()
                .isPremiumForever.eq(true)
                .premiumUntil.gt(LocalDateTime.now())
            .endOr()
            .findCount()

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
        if (!user.autoAcceptsFollowRequests()) return describe(user)
        // Nothing left to review: clear the backlog and reload to get up-to-date counts
        followService.acceptAllPendingFollowRequests(id)
        return describe(getEntityById(id))
    }

    fun getSettings(id: Long): UserSettingsDTO =
        getEntityById(id).getSettings()

    /**
     * Records a language a client reported, for an account that has none.
     *
     * Called from wherever a client says what it is running in — today, registering a
     * device. Silent about an account that already has one: see [User.adoptLocale].
     */
    fun adoptLocale(user: User, reported: Locale) {
        if (!user.adoptLocale(reported)) return
        user.update()
        logger.info { "Account ${user.id} adopted locale $reported from a client" }
    }

    /**
     * The language to write to an account in.
     *
     * The account's own when it has one — what the user chose, or what the first client to
     * say anything reported. A device is consulted only for an account that has none, and
     * [Locale.FR] is the last resort: the value the column held for everybody, so an account
     * nothing has ever reported for reads exactly as it did before.
     *
     * Here rather than in either caller because notifications and mail both resolve it, and
     * they have to agree — a push and the mail announcing the same recipe arriving in two
     * different languages would be nobody's idea of a preference.
     */
    fun readingLocaleOf(user: User): Locale =
        user.locale ?: deviceService.readingLocaleOf(user.id, Locale.FR)

    /**
     * [readingLocaleOf] for a whole audience, in one query rather than one per member.
     *
     * The query is for the accounts with no language of their own, and only those: once a
     * client has reported one, answering is a field read. An audience that has all reported
     * touches `devices` not at all.
     */
    fun readingLocalesOf(users: Collection<User>): Map<Long, Locale> {
        val undeclared = users.filter { it.locale == null }.map { it.id }
        val byDevice = deviceService.readingLocalesOf(undeclared)
        return users.associate { it.id to (it.locale ?: byDevice[it.id] ?: Locale.FR) }
    }

    fun search(searchString: String?, paging: Paging): Pair<Int, List<UserInfo>> {
        val query = QUser()
            .apply {
                if (!searchString.isNullOrBlank()) {
                    this.username.ilike("%$searchString%")
                }
            }

        return Pair(query.findCount(), describeAll(query.setPaging(paging).findList()))
    }

    /**
     * One account's [UserInfo]. Five queries for the counts rather than the five lazy loads it
     * would otherwise cost — the same five, but the shape is the one a listing can use, so there is
     * a single way to build the DTO and a page cannot drift from a single read.
     */
    fun describe(user: User): UserInfo = describeAll(listOf(user)).single()

    /** [describe] for a whole page, at a fixed cost whatever the page holds. */
    fun describeAll(users: List<User>): List<UserInfo> {
        val counts = countsOf(users.map { it.id })
        return users.map { it.toInfo(counts[it.id] ?: UserCounts.NONE) }
    }

    /**
     * The five figures a profile states, for a whole page of accounts, in five queries.
     *
     * One grouped `count(*)` per collection, from the child side: reading `user.recipes.size` and
     * the four like it costs a round trip each, per account. See
     * [com.xavierclavel.utils.countByParent].
     *
     * Soft-deleted recipes drop out on their own — Ebean applies the flag to its own queries — which
     * is what keeps this agreeing with the collection it replaces.
     *
     * @return counts per account id; an account with nothing to its name is absent from the map
     */
    fun countsOf(userIds: Collection<Long>): Map<Long, UserCounts> {
        if (userIds.isEmpty()) return emptyMap()
        val recipes = QRecipe()
            .select("${QRecipe.Alias.owner.id}, count(*)")
            .owner.id.`in`(userIds)
            .query()
            .countByParent()
        val likes = QLike()
            .select("${QLike.Alias.user.id}, count(*)")
            .user.id.`in`(userIds)
            .query()
            .countByParent()
        val cookbooks = QCookbookUser()
            .select("${QCookbookUser.Alias.user.id}, count(*)")
            .user.id.`in`(userIds)
            .query()
            .countByParent()
        // A pending request is not a follower, on either side
        val followers = QFollow()
            .select("${QFollow.Alias.user.id}, count(*)")
            .user.id.`in`(userIds)
            .pending.eq(false)
            .query()
            .countByParent()
        val follows = QFollow()
            .select("${QFollow.Alias.follower.id}, count(*)")
            .follower.id.`in`(userIds)
            .pending.eq(false)
            .query()
            .countByParent()

        return userIds.associateWith { id ->
            UserCounts(
                recipes = recipes[id] ?: 0,
                likes = likes[id] ?: 0,
                cookbooks = cookbooks[id] ?: 0,
                followers = followers[id] ?: 0,
                follows = follows[id] ?: 0,
            )
        }
    }




}