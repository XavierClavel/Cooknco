package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.models.AppVersion
import com.xavierclavel.models.Device
import com.xavierclavel.models.query.QAppVersion
import com.xavierclavel.models.query.QDevice
import com.xavierclavel.models.query.QUser
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import shared.dto.AppVersionDTO
import shared.enums.AppPlatform
import shared.enums.AppVersionStatus
import shared.infodto.AdminAppVersionCount
import shared.infodto.AdminAppVersionInfo
import shared.infodto.AdminAppVersionReach
import shared.infodto.AppVersionCheckInfo
import shared.utils.AppVersions
import java.time.LocalDateTime

/**
 * Which builds of the mobile apps are allowed to run.
 *
 * The backend only ever *answers*: it says a build is too old, and the client is what stops.
 * Nothing here rejects the API calls an out-of-date app makes, and deliberately so — a gate
 * that returned 426 on every endpoint would break the sign-in and the update prompt along
 * with everything else, and would still not stop a client that ignored it. What this buys is
 * the honest thing: one place an operator can raise the floor without shipping a build.
 *
 * Every uncertainty resolves towards letting the app run. No row means no gate, an
 * unreadable version means no verdict, and a version newer than the store's is left alone.
 * The failure this design refuses is the one that cannot be undone from a phone.
 */
class AppVersionService: KoinComponent {

    // ------------------------------------------------------------------- reading

    /**
     * What a client running [version] on [platform] should do.
     *
     * @param version as the build reports itself; blank or unreadable is a normal answer
     */
    fun check(platform: AppPlatform, version: String): AppVersionCheckInfo {
        val gate = find(platform)
            ?: return AppVersionCheckInfo(status = AppVersionStatus.OK, currentVersion = version)

        val status = when {
            // A version we cannot read is not a version we can call old. Rows are validated
            // on save, so this is only ever the client's string.
            AppVersions.parse(version) == null -> AppVersionStatus.OK
            AppVersions.isOlderThan(version, gate.minimumVersion) -> AppVersionStatus.UPDATE_REQUIRED
            AppVersions.isOlderThan(version, gate.latestVersion) -> AppVersionStatus.UPDATE_AVAILABLE
            else -> AppVersionStatus.OK
        }

        return AppVersionCheckInfo(
            status = status,
            currentVersion = version,
            minimumVersion = gate.minimumVersion,
            latestVersion = gate.latestVersion,
            storeUrl = gate.storeUrl,
        )
    }

    /** Every platform, gated or not, always in the same order. */
    fun describe(): List<AdminAppVersionInfo> {
        val gates = QAppVersion().findList().associateBy { it.platform }
        return AppPlatform.entries.map { platform ->
            gates[platform]?.toInfo() ?: AdminAppVersionInfo.ungated(platform)
        }
    }

    fun describe(platform: AppPlatform): AdminAppVersionInfo =
        find(platform)?.toInfo() ?: AdminAppVersionInfo.ungated(platform)

    private fun find(platform: AppPlatform): AppVersion? =
        QAppVersion().platform.eq(platform).findOne()

    // --------------------------------------------------------------------- reach

    /**
     * What raising the floor to [candidateMinimum] would cost, measured on the install
     * base rather than guessed at.
     *
     * The whole reason this exists is that the save it informs is one-way from the
     * operator's side: a floor is applied by apps that have already stopped asking anything
     * else, so the moment to find out it was too high is before, not after. It is therefore
     * answered against a *candidate* the tab is still editing, not against what is saved.
     *
     * An unreadable version is counted apart and blocked by nothing, the same way the gate
     * itself treats one. A blank or unreadable candidate blocks nothing either, which is
     * the right answer for a half-typed field.
     *
     * @param candidateMinimum the floor to measure; defaults to the one in force
     */
    fun reach(platform: AppPlatform, candidateMinimum: String? = null): AdminAppVersionReach {
        val minimum = (candidateMinimum ?: find(platform)?.minimumVersion).orEmpty().trim()
        val devices = recentDevices(platform)

        val blocked = devices.filter { AppVersions.isOlderThan(it.appVersion, minimum) }

        return AdminAppVersionReach(
            platform = platform,
            windowDays = REACH_WINDOW_DAYS,
            devices = devices.size,
            users = devices.userCount(),
            minimumVersion = minimum,
            blockedDevices = blocked.size,
            blockedUsers = blocked.userCount(),
            unknownDevices = devices.count { AppVersions.parse(it.appVersion) == null },
            distribution = devices.groupBy { it.appVersion }
                .map { (version, rows) ->
                    AdminAppVersionCount(
                        version = version,
                        devices = rows.size,
                        users = rows.userCount(),
                        blocked = AppVersions.isOlderThan(version, minimum),
                    )
                }
                .sortedWith(NEWEST_FIRST),
        )
    }

    /**
     * The devices of one platform that are actually in use.
     *
     * `lastSeenAt` is touched on every launch, so the window is a proxy for "still
     * installed and opened". Only the two columns the counting needs are selected; the
     * account is fetched by id alone, which Ebean reads off the foreign key rather than
     * joining for.
     */
    private fun recentDevices(platform: AppPlatform): List<Device> =
        QDevice()
            .select(QDevice.Alias.appVersion)
            .user.fetch(QUser.Alias.id)
            .platform.eq(platform.devicePlatform())
            .lastSeenAt.after(LocalDateTime.now().minusDays(REACH_WINDOW_DAYS.toLong()))
            .findList()

    /** Distinct accounts behind a set of devices; one person with two handsets counts once. */
    private fun List<Device>.userCount(): Int = mapNotNull { it.user?.id }.distinct().size

    // ------------------------------------------------------------------- writing

    fun save(platform: AppPlatform, dto: AppVersionDTO): AdminAppVersionInfo {
        val minimum = dto.minimumVersion.trim()
        val latest = dto.latestVersion.trim()
        val storeUrl = dto.storeUrl.trim()
        validate(minimum, latest, storeUrl)

        val gate = find(platform) ?: AppVersion(platform = platform)
        gate.minimumVersion = minimum
        gate.latestVersion = latest
        gate.storeUrl = storeUrl
        gate.save()

        logger.info { "$platform builds below $minimum are now blocked; latest is $latest" }
        return gate.toInfo()
    }

    /**
     * Drops a platform's gate, which lets every build of it run again.
     *
     * @return false when there was no gate to remove
     */
    fun clear(platform: AppPlatform): Boolean {
        val removed = find(platform)?.delete() ?: false
        if (removed) logger.info { "$platform version gate removed; every build runs again" }
        return removed
    }

    /**
     * Refuses a gate that would lock users out for no reason they could act on.
     *
     * The floor above the ceiling is the one worth spelling out: it blocks *everybody*,
     * including the users who did update, and the only way back is this same screen — which
     * an operator on a phone in the evening does not have. It is a typo away at all times
     * (`1.10` read as older than `1.9`) so it is refused rather than warned about.
     */
    private fun validate(minimum: String, latest: String, storeUrl: String) {
        if (!AppVersions.isValid(minimum) || !AppVersions.isValid(latest)) {
            throw BadRequestException(BadRequestCause.APP_VERSION_INVALID)
        }
        if (AppVersions.isOlderThan(latest, minimum)) {
            throw BadRequestException(BadRequestCause.APP_VERSION_MINIMUM_ABOVE_LATEST)
        }
        // A blocked build with nowhere to go is a dead app, so the link is part of the gate
        // rather than an optional extra.
        if (storeUrl.isBlank() || storeUrl.length > STORE_URL_MAX_LENGTH) {
            throw BadRequestException(BadRequestCause.APP_VERSION_STORE_URL_INVALID)
        }
        if (!storeUrl.startsWith("https://") && !storeUrl.startsWith("http://")) {
            throw BadRequestException(BadRequestCause.APP_VERSION_STORE_URL_INVALID)
        }
    }

    private companion object {
        /** Matches the column, which is what would truncate a longer one. */
        const val STORE_URL_MAX_LENGTH = 511

        /**
         * How far back a device still counts as in use.
         *
         * Long enough that a seasonal user is not written off, short enough that handsets
         * replaced a year ago stop inflating what a floor appears to cost. Nothing prunes
         * `devices` on a timer — only a token FCM rejects is removed — so without a window
         * these figures would only ever grow.
         */
        const val REACH_WINDOW_DAYS = 90

        /** Newest build first; anything unreadable sorts to the end, by name. */
        val NEWEST_FIRST = Comparator<AdminAppVersionCount> { a, b ->
            val left = AppVersions.parse(a.version)
            val right = AppVersions.parse(b.version)
            when {
                left == null && right == null -> a.version.compareTo(b.version)
                left == null -> 1
                right == null -> -1
                else -> AppVersions.compare(b.version, a.version) ?: 0
            }
        }
    }
}
