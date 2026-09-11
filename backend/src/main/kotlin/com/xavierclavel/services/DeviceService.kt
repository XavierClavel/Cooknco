package com.xavierclavel.services

import com.xavierclavel.models.Device
import com.xavierclavel.models.query.QDevice
import com.xavierclavel.utils.DbTransaction.updateAndGet
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.dto.DeviceRegistrationDTO
import shared.enums.DevicePlatform
import shared.enums.Locale
import shared.infodto.AdminPushAudienceInfo

/**
 * The devices notifications can be pushed to.
 *
 * A device is a token, not a handset: the app has no stable id to offer and does not need
 * one, since FCM already guarantees a token identifies exactly one install. Everything here
 * is therefore keyed on the token, which is what makes registration idempotent — the client
 * re-registers on every launch and on every token rotation, and nothing accumulates.
 */
class DeviceService: KoinComponent {
    private val userService: UserService by inject()

    /**
     * Records where a user can be pushed to, or moves an existing token to them.
     *
     * Re-pointing rather than refusing a token that is already registered is the case that
     * matters: a shared or resold handset would otherwise keep pushing the previous
     * account's notifications to whoever holds it now.
     */
    fun register(userId: Long, dto: DeviceRegistrationDTO, locale: Locale): Device {
        val user = userService.getEntityById(userId)
        // A client saying what language it runs in is the app's only way of telling the
        // backend, and an account with none takes it. Never a correction: see `User.locale`
        userService.adoptLocale(user, locale)
        val existing = QDevice().token.eq(dto.token).findOne()

        if (existing != null) {
            val moved = existing.user?.id != userId
            existing.user = user
            existing.platform = dto.platform
            // Overwritten on every launch, so an install that updated is counted as the
            // build it is now rather than as the one it first registered under
            existing.appVersion = dto.appVersion.trim()
            existing.locale = locale
            existing.updateAndGet()
            if (moved) logger.info { "Device ${existing.id} re-registered to user $userId" }
            return existing
        }

        return Device(
            user = user,
            token = dto.token,
            platform = dto.platform,
            appVersion = dto.appVersion.trim(),
            locale = locale,
        ).apply { insert() }
            .also { logger.info { "Device ${it.id} registered for user $userId on ${dto.platform}" } }
    }

    /**
     * Forgets one device, on a sign-out.
     *
     * Scoped to the caller so that knowing a token is not enough to unsubscribe someone
     * else's device. A token that is not theirs simply matches nothing.
     */
    fun unregister(userId: Long, token: String): Boolean =
        QDevice().token.eq(token).user.id.eq(userId).delete() > 0

    /** Every device of every user named. Empty in, empty out — never "all devices". */
    fun findForUsers(userIds: Collection<Long>): List<Device> =
        if (userIds.isEmpty()) emptyList()
        else QDevice().user.id.`in`(userIds).user.fetch().findList()

    fun findForUser(userId: Long): List<Device> = findForUsers(listOf(userId))

    /** Every registered device. */
    fun findAll(): List<Device> = QDevice().user.fetch().findList()

    /**
     * The users who have at least one device registered in a given language.
     *
     * The handsets, not the people: this describes clients, and [NotificationService] is
     * what widens it to the accounts whose own language says the same thing.
     */
    fun findUserIdsReadingIn(reading: Locale): Set<Long> =
        QDevice().locale.eq(reading).select(QDevice.Alias.user.id).findList()
            .mapNotNull { it.user?.id }
            .toSet()

    /**
     * The language to write the notifications of an account that has none of its own in.
     *
     * The most recently registered device wins, since that is the client they last used.
     * Barely reachable now that registering a device gives the account a language — it
     * catches the rows that predate that, and the window between a device being pointed at
     * a different account and that account's next launch.
     *
     * A user with no device has nothing to go on, so the caller supplies the fallback.
     */
    fun readingLocaleOf(userId: Long, fallback: Locale): Locale =
        QDevice().user.id.eq(userId)
            .orderBy().lastSeenAt.desc()
            .setMaxRows(1)
            .findOne()
            ?.locale
            ?: fallback

    /**
     * [readingLocaleOf] for many users at once, in one query.
     *
     * Its reason for existing is fan-out: mailing a popular author's followers one at a
     * time would ask this question once per recipient, which is a query per mail before a
     * single one is sent. Users with no device are simply absent from the result — the
     * caller holds the fallback, which is per-user.
     */
    fun readingLocalesOf(userIds: Collection<Long>): Map<Long, Locale> {
        if (userIds.isEmpty()) return emptyMap()
        return QDevice().user.id.`in`(userIds)
            .orderBy().lastSeenAt.desc()
            .findList()
            .groupBy { it.user?.id }
            .mapNotNull { (userId, devices) -> userId?.let { it to devices.first().locale } }
            .toMap()
    }

    /**
     * Drops tokens the transport reported as dead.
     *
     * The only thing that prunes the table, and deliberately the only thing: see
     * [PushResult.staleTokens] for why a merely failed push is not evidence of anything.
     */
    fun removeStale(tokens: Collection<String>): Int {
        if (tokens.isEmpty()) return 0
        val removed = QDevice().token.`in`(tokens).delete()
        if (removed > 0) logger.info { "Removed $removed device(s) FCM reported as unregistered" }
        return removed
    }

    /** What a set of recipients would be pushed on, for the backoffice to show. */
    fun describeAudience(recipientIds: Collection<Long>): AdminPushAudienceInfo {
        val devices = findForUsers(recipientIds)
        return AdminPushAudienceInfo(
            users = recipientIds.size,
            devices = devices.size,
            byPlatform = DevicePlatform.entries
                .associateWith { platform -> devices.count { it.platform == platform } }
                .filterValues { it > 0 },
        )
    }
}
