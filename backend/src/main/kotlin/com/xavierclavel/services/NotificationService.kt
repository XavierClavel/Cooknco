package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Device
import com.xavierclavel.models.Notification
import com.xavierclavel.models.Recipe
import com.xavierclavel.models.User
import com.xavierclavel.models.jointables.query.QFollow
import com.xavierclavel.models.query.QNotification
import com.xavierclavel.models.query.QUser
import com.xavierclavel.utils.logger
import io.ebean.Paging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.dto.AnnouncementDTO
import shared.dto.NotificationTestDTO
import shared.enums.Locale
import shared.enums.NotificationKind
import shared.enums.NotificationPlaceholder
import shared.infodto.AdminNotificationSendInfo
import shared.infodto.AdminNotificationTestInfo
import shared.infodto.AdminPushAudienceInfo
import shared.infodto.UserNotificationInfo
import shared.utils.NotificationWordings

/**
 * The notifications the application sends, and the record of the ones it has sent.
 *
 * Every notification goes the same way round: a `notifications` row per recipient first,
 * then a push per device of those recipients. That order is the design rather than an
 * implementation detail — the row is what the app's list is built from, so a recipient with
 * no device, a dead token, or notifications switched off at the OS still finds everything
 * addressed to them, and a push is only ever the buzz that announces a row.
 *
 * Wording comes from [NotificationWordings] for the kinds the app emits and from the
 * operator for an announcement. Either way it is rendered before it is stored, so what a
 * device showed and what the list shows cannot drift apart.
 */
class NotificationService : KoinComponent {
    private val deviceService: DeviceService by inject()
    private val pushSender: PushSender by inject()

    /**
     * Where fan-out runs.
     *
     * Its own scope, not the call's: publishing a recipe must not wait on a push to every
     * follower, nor fail because one of them could not be reached. A [SupervisorJob] so one
     * failed fan-out does not cancel the next.
     */
    private val dispatches = SupervisorJob()

    private val scope = CoroutineScope(dispatches + Dispatchers.IO + CoroutineName("notifications"))

    /**
     * Waits until no fan-out is still running.
     *
     * Exists for the tests. A dispatch deliberately outlives the request that caused it, so
     * a test that wiped the database straight after one would race the inserts it is about
     * to assert on — or worse, leave them landing in the next test. Nothing in production
     * calls this: there, outliving the request is the whole point.
     */
    suspend fun awaitDispatches() {
        // A running dispatch can launch nothing further, but one may start between the two
        // lines below, so this drains until it finds the scope genuinely idle
        while (true) {
            val running = dispatches.children.toList()
            if (running.isEmpty()) return
            running.joinAll()
        }
    }

    companion object {
        /** How many notifications the bell asks for. Enough to fill a menu, not a history. */
        const val DEFAULT_PAGE_SIZE = 20
    }

    // ------------------------------------------------------------------- reading

    fun list(userId: Long, paging: Paging): List<UserNotificationInfo> =
        QNotification()
            .user.id.eq(userId)
            .actor.fetch()
            .orderBy().createdAt.desc()
            .setPaging(paging)
            .findList()
            .map { it.toInfo() }

    fun unreadCount(userId: Long): Int =
        QNotification().user.id.eq(userId).readAt.isNull.findCount()

    /** @return false when the notification is not this user's, or does not exist */
    fun markRead(userId: Long, notificationId: Long): Boolean {
        val notification = QNotification().id.eq(notificationId).user.id.eq(userId).findOne() ?: return false
        notification.markRead().update()
        return true
    }

    fun markAllRead(userId: Long): Int =
        QNotification().user.id.eq(userId).readAt.isNull.findList()
            .onEach { it.markRead().update() }
            .size

    // ------------------------------------------------------------------ clearing

    /**
     * Takes one notification out of its recipient's list, for good.
     *
     * A delete rather than a flag, because the row *is* the notification: keeping a cleared
     * one would make the table and the list disagree about what a user has, and put a filter
     * on every read from then on to hold them apart. It is also what keeps [unreadCount]
     * honest with nothing said to it — clearing an unread notification stops it being
     * counted because it stops existing.
     *
     * The push already on the recipient's phone is not recalled, and cannot be: clearing is
     * about the list, and a notification that has already buzzed is not news this can take
     * back.
     *
     * @return false when the notification is not this user's, or is already gone
     */
    fun clear(userId: Long, notificationId: Long): Boolean =
        QNotification().id.eq(notificationId).user.id.eq(userId).delete() > 0

    /**
     * Clears everything addressed to this user, not just the page they were shown.
     *
     * The bell asks for [DEFAULT_PAGE_SIZE] at a time, so a clear bounded by what came back
     * would empty a list that fills itself again on the next poll — and the user would have
     * no way of telling how many presses "clear all" takes.
     *
     * Follow requests survive it, and that is the point of their being a list of their own:
     * they are a queue to answer rather than news, so clearing the notification that
     * announced one leaves the request itself waiting to be answered.
     *
     * @return how many were cleared
     */
    fun clearAll(userId: Long): Int =
        QNotification().user.id.eq(userId).delete()

    // -------------------------------------------------------- what the app emits

    /**
     * Tells a recipe author's followers about it.
     *
     * Accepted followers only: a pending request is somebody the author has not let in yet,
     * and pushing them the author's new recipes would answer the request on their behalf.
     */
    fun onRecipeCreated(recipe: Recipe) {
        val author = recipe.owner ?: return
        // Neither is possible on a recipe a moment old, but this is also the path a
        // re-publish or a backfill would take, and a hidden recipe must not ring anyone
        if (recipe.isHidden || author.isBanned) return

        val followers = QFollow()
            .user.id.eq(author.id)
            .pending.eq(false)
            .follower.fetch()
            .findList()
            .mapNotNull { it.follower }

        dispatch(
            recipients = followers,
            kind = NotificationKind.NEW_RECIPE,
            actor = author,
            values = mapOf(
                NotificationPlaceholder.USERNAME to author.username,
                NotificationPlaceholder.TITLE to recipe.title,
            ),
            link = "/recipe/view?id=${recipe.id}",
        )
    }

    /**
     * Tells someone they have been followed, or asked to be.
     *
     * The two are one method because they are one event with two outcomes, and which one
     * happened is exactly what the recipient needs told: a request is something to act on,
     * a follow is not.
     */
    fun onFollowed(followed: User, follower: User, pending: Boolean) = dispatch(
        recipients = listOf(followed),
        kind = if (pending) NotificationKind.FOLLOW_REQUEST else NotificationKind.NEW_FOLLOWER,
        actor = follower,
        values = mapOf(NotificationPlaceholder.USERNAME to follower.username),
        link = "/user/view?user=${follower.id}",
    )

    /** Tells someone the request they had sent was accepted. */
    fun onFollowRequestAccepted(followed: User, follower: User) = dispatch(
        recipients = listOf(follower),
        kind = NotificationKind.FOLLOW_ACCEPTED,
        actor = followed,
        values = mapOf(NotificationPlaceholder.USERNAME to followed.username),
        link = "/user/view?user=${followed.id}",
    )

    // ----------------------------------------------------- what an operator sends

    /**
     * Sends an announcement to everyone, or to the users named.
     *
     * The rows are written before this returns, so the count reported is real and the
     * notifications are already in the app. The pushes are not waited on: a broadcast is one
     * request per device, and an operator should not hold a connection open for the length
     * of one.
     */
    fun announce(dto: AnnouncementDTO): AdminNotificationSendInfo {
        val title = dto.title.trim()
        val body = dto.body.trim()
        validate(title, body)

        val recipients = recipientsOf(dto)
        if (recipients.isEmpty()) throw BadRequestException(BadRequestCause.NOTIFICATION_HAS_NO_AUDIENCE)

        val stored = store(recipients, NotificationKind.ANNOUNCEMENT, title, body, dto.link.trim(), actor = null)
        // Every device of the chosen recipients: the locale, if there was one, already
        // narrowed *who* the recipients are, so narrowing again here would drop the second
        // handset of a user who was deliberately included
        val devices = deviceService.findForUsers(recipients.map { it.id })

        logger.info { "Announcement stored for ${recipients.size} user(s), pushing to ${devices.size} device(s)" }
        scope.launch { pushAndPrune(devices, stored) }

        return AdminNotificationSendInfo(recipients = recipients.size, devices = devices.size)
    }

    /**
     * Sends one notification to the caller's own devices, and reports what happened.
     *
     * Waited on, unlike an announcement: it goes to one operator's devices, and a test whose
     * answer was "queued" would not have tested the thing anybody runs it to test.
     */
    suspend fun sendTest(userId: Long, dto: NotificationTestDTO): AdminNotificationTestInfo {
        val title = dto.title.trim().ifBlank { DEFAULT_TEST_TITLE }
        val body = dto.body.trim().ifBlank { DEFAULT_TEST_BODY }
        validate(title, body)

        val devices = deviceService.findForUser(userId)
        if (devices.isEmpty()) throw BadRequestException(BadRequestCause.NOTIFICATION_HAS_NO_DEVICE)

        // Stored like any other, so a test also exercises the in-app list, not only the push
        val stored = store(
            recipients = listOf(QUser().id.eq(userId).findOne() ?: throw NotFoundException(NotFoundCause.USER_NOT_FOUND)),
            kind = NotificationKind.ANNOUNCEMENT,
            title = title,
            body = body,
            link = dto.link.trim(),
            actor = null,
        )

        val result = pushAndPrune(devices, stored)
        return AdminNotificationTestInfo(
            devices = devices.size,
            pushed = result.delivered,
            failed = result.failed,
        )
    }

    private fun validate(title: String, body: String) {
        if (title.isBlank() || body.isBlank()) throw BadRequestException(BadRequestCause.NOTIFICATION_EMPTY)
        if (title.length > NotificationWordings.MAX_TITLE_LENGTH ||
            body.length > NotificationWordings.MAX_BODY_LENGTH
        ) {
            throw BadRequestException(BadRequestCause.NOTIFICATION_TOO_LONG)
        }
    }

    /**
     * The language to write one user's notifications in.
     *
     * The account's own when it has one, which is the point of it being managed here: it is
     * what the user chose or what the first client to say anything reported, and it is the
     * same language their mails go out in. A device is consulted only for an account that
     * has none, and [Locale.FR] is the last resort — the value the column used to hold for
     * everybody, so an account nothing has ever reported for reads exactly as it did before.
     */
    private fun readingLocaleOf(user: User): Locale =
        user.locale ?: deviceService.readingLocaleOf(user.id, Locale.FR)

    /**
     * Who an announcement is for.
     *
     * Naming users takes precedence over everything else, [AnnouncementDTO.locale] included:
     * an operator who picked three accounts meant those three. A named user who does not
     * exist is an error rather than a silent omission — a mistyped id should be reported,
     * not leave the operator believing the notification went out.
     *
     * A broadcast is every account that is not banned. With a locale it is narrowed to the
     * accounts that will *read* it in that language — which has to be resolved the same way
     * [readingLocaleOf] does, or an operator would select an audience by one rule and have
     * it worded by another: an account whose language is EN and whose last handset reported
     * FR would land in the French send and receive an English notification.
     *
     * So: the account's own language when it has one, and failing that a device registered
     * in it. An account with neither is in no language audience at all, the same way it
     * never was.
     */
    private fun recipientsOf(dto: AnnouncementDTO): List<User> {
        if (dto.userIds.isNotEmpty()) {
            val wanted = dto.userIds.distinct()
            val users = QUser().id.`in`(wanted).findList()
            if (users.size != wanted.size) throw NotFoundException(NotFoundCause.USER_NOT_FOUND)
            return users
        }
        val dtoLocale = dto.locale
            ?: return QUser().isBanned.eq(false).findList()

        // Accounts with a language of their own are answered by it alone; the device lookup
        // is only for the ones that have none, so a stale handset cannot pull an account
        // into an audience its own language excludes it from
        val byDevice = deviceService.findUserIdsReadingIn(dtoLocale)
        return QUser()
            .isBanned.eq(false)
            .or()
                .locale.eq(dtoLocale)
                .and()
                    .locale.isNull()
                    .id.`in`(byDevice.ifEmpty { setOf(-1L) })
                .endAnd()
            .endOr()
            .findList()
    }

    /**
     * What a send would reach, for the backoffice to show before one goes out.
     *
     * Resolves the audience through [recipientsOf], the same way [announce] does, so the
     * preview and the send cannot disagree about who is included.
     */
    fun describeAudience(userIds: List<Long>, locale: Locale?): AdminPushAudienceInfo {
        val recipients = recipientsOf(AnnouncementDTO(title = "", body = "", userIds = userIds, locale = locale))
        return deviceService.describeAudience(recipients.map { it.id })
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Stores a notification per recipient and pushes it, off the caller's thread.
     *
     * Both halves are in the background here — unlike [announce], where the storing is
     * waited on. The difference is who is asking: an operator is owed a count, whereas the
     * user publishing a recipe is owed nothing but a fast response, and their request must
     * not fail because a follower's notification could not be written.
     *
     * Only ids cross into the coroutine. The entities the caller holds belong to its
     * transaction, which will have been committed and closed by the time this runs.
     */
    private fun dispatch(
        recipients: List<User>,
        kind: NotificationKind,
        actor: User?,
        values: Map<String, String>,
        link: String,
    ) {
        // Nobody is notified of their own doing, and a banned account is not notified at all
        val targets = recipients.filter { it.id != actor?.id && !it.isBanned }.distinctBy { it.id }
        if (targets.isEmpty()) return

        val ids = targets.map { it.id }
        val actorId = actor?.id

        scope.launch {
            try {
                val users = QUser().id.`in`(ids).findList()
                val actorEntity = actorId?.let { QUser().id.eq(it).findOne() }
                val stored = users.map { user ->
                    val reading = readingLocaleOf(user)
                    val (title, body) = NotificationWordings.render(kind, reading, values)
                    Notification(
                        user = user,
                        kind = kind,
                        title = title.take(NotificationWordings.MAX_TITLE_LENGTH),
                        body = body.take(NotificationWordings.MAX_BODY_LENGTH),
                        link = link,
                        actor = actorEntity,
                    ).also { it.insert() }
                }
                pushAndPrune(deviceService.findForUsers(ids), stored)
            } catch (e: Exception) {
                // Whatever caused this has already been committed and answered
                logger.error(e) { "Could not deliver ${kind.key} to ${ids.size} recipient(s)" }
            }
        }
    }

    private fun store(
        recipients: List<User>,
        kind: NotificationKind,
        title: String,
        body: String,
        link: String,
        actor: User?,
    ): List<Notification> = recipients.map { recipient ->
        Notification(
            user = recipient,
            kind = kind,
            title = title,
            body = body,
            link = link,
            actor = actor,
        ).also { it.insert() }
    }

    /**
     * Pushes one notification to each device, and prunes whatever FCM reports as dead.
     *
     * Stored rows are matched to devices by user so that each push carries the id of the row
     * it announces: tapping the notification can then mark exactly that one read.
     */
    private suspend fun pushAndPrune(devices: List<Device>, stored: List<Notification>): PushResult {
        if (devices.isEmpty()) return PushResult()
        val byUser = stored.associateBy { it.user?.id }

        val messages = devices.mapNotNull { device ->
            val notification = byUser[device.user?.id] ?: return@mapNotNull null
            PushMessage(
                token = device.token,
                title = notification.title,
                body = notification.body,
                data = buildMap {
                    put("kind", notification.kind.key)
                    put("notificationId", notification.id.toString())
                    if (notification.link.isNotBlank()) put("link", notification.link)
                },
            )
        }

        val result = pushSender.send(messages)
        deviceService.removeStale(result.staleTokens)
        return result
    }
}

private const val DEFAULT_TEST_TITLE = "Cook&Co"
private const val DEFAULT_TEST_BODY = "Test notification from the backoffice."
