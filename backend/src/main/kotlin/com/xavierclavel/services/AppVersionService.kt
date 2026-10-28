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
 * the honest thing: one place an operator can raise the floor without shipping a build, or
 * take one bad build out of service without raising the floor past the good ones below it.
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
            stops(version, gate.minimumVersion, gate.blockedVersions) -> AppVersionStatus.UPDATE_REQUIRED
            AppVersions.isOlderThan(version, gate.latestVersion) -> AppVersionStatus.UPDATE_AVAILABLE
            else -> AppVersionStatus.OK
        }

        return AppVersionCheckInfo(
            status = status,
            currentVersion = version,
            minimumVersion = gate.minimumVersion,
            latestVersion = gate.latestVersion,
            storeUrl = gate.storeUrl,
            blocked = lists(gate.blockedVersions, version),
        )
    }

    /**
     * Whether a gate stops [version]: below its floor, or named on its list.
     *
     * The one definition [check] and [reach] share, so what the tab says a save would block
     * and what phones are then told cannot come to different answers.
     */
    private fun stops(version: String, minimum: String, blocked: List<String>): Boolean =
        AppVersions.isOlderThan(version, minimum) || lists(blocked, version)

    private fun lists(blocked: List<String>, version: String): Boolean =
        blocked.any { AppVersions.isSameBuild(version, it) }

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
     * What raising the floor to [candidateMinimum] and blocking [candidateBlocked] would
     * cost, measured on the install base rather than guessed at.
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
     * @param candidateBlocked the blocked builds to measure; defaults to the ones in force,
     *   and an empty list is a candidate of its own — the tab with every block removed
     */
    fun reach(
        platform: AppPlatform,
        candidateMinimum: String? = null,
        candidateBlocked: List<String>? = null,
    ): AdminAppVersionReach {
        val saved by lazy { find(platform) }
        val minimum = (candidateMinimum ?: saved?.minimumVersion).orEmpty().trim()
        val blockedVersions = (candidateBlocked ?: saved?.blockedVersions.orEmpty())
            .map { it.trim() }
            .filter { AppVersions.isValid(it) }
        val devices = recentDevices(platform)

        val blocked = devices.filter { stops(it.appVersion, minimum, blockedVersions) }

        return AdminAppVersionReach(
            platform = platform,
            windowDays = REACH_WINDOW_DAYS,
            devices = devices.size,
            users = devices.userCount(),
            minimumVersion = minimum,
            blockedVersions = blockedVersions,
            blockedDevices = blocked.size,
            blockedUsers = blocked.userCount(),
            unknownDevices = devices.count { AppVersions.parse(it.appVersion) == null },
            distribution = devices.groupBy { it.appVersion }
                .map { (version, rows) ->
                    AdminAppVersionCount(
                        version = version,
                        devices = rows.size,
                        users = rows.userCount(),
                        blocked = stops(version, minimum, blockedVersions),
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
        val gate = find(platform) ?: AppVersion(platform = platform)
        val minimum = dto.minimumVersion.trim()
        val latest = dto.latestVersion.trim()
        val storeUrl = dto.storeUrl.trim()
        // Null keeps what is saved, so a client that predates the list cannot wipe it
        val blocked = dto.blockedVersions?.let(::normalise) ?: gate.blockedVersions.toList()
        validate(minimum, latest, storeUrl, blocked)

        gate.minimumVersion = minimum
        gate.latestVersion = latest
        gate.storeUrl = storeUrl
        gate.blockedVersions = blocked.toMutableList()
        gate.save()

        logger.info {
            "$platform builds below $minimum are now blocked" +
                (if (blocked.isEmpty()) "" else ", and ${blocked.joinToString()}") +
                "; latest is $latest"
        }
        return gate.toInfo()
    }

    /**
     * One entry per build, newest first, whatever order and spelling they arrived in.
     *
     * `1.301` and `1.301.0` are the same build to the gate, so they are one entry here —
     * the first spelling wins. Blank entries are dropped rather than refused: an empty chip
     * is nothing, not a malformed version.
     */
    private fun normalise(versions: List<String>): List<String> {
        val named = versions.map { it.trim() }.filter { it.isNotEmpty() }
        // Refused before they are collapsed, which reads versions leniently and would fold
        // `1.301-rc1` into `1.301` without a word instead of turning it down
        if (named.any { !AppVersions.isValid(it) }) {
            throw BadRequestException(BadRequestCause.APP_VERSION_INVALID)
        }
        return named
            .fold(mutableListOf<String>()) { kept, version ->
                if (kept.none { AppVersions.isSameBuild(it, version) }) kept += version
                kept
            }
            .sortedWith { a, b -> AppVersions.compare(b, a) ?: 0 }
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
    private fun validate(minimum: String, latest: String, storeUrl: String, blocked: List<String>) {
        if (!AppVersions.isValid(minimum) || !AppVersions.isValid(latest)) {
            throw BadRequestException(BadRequestCause.APP_VERSION_INVALID)
        }
        if (AppVersions.isOlderThan(latest, minimum)) {
            throw BadRequestException(BadRequestCause.APP_VERSION_MINIMUM_ABOVE_LATEST)
        }
        if (blocked.size > MAX_BLOCKED_VERSIONS) {
            throw BadRequestException(BadRequestCause.APP_VERSION_TOO_MANY_BLOCKED)
        }
        // The same trap as the floor, in a different place: the store sends a blocked user
        // to the latest build, so blocking it — or a tester's build past it — is a loop
        // with no way out. The fix has to be published, and named latest, first.
        if (blocked.any { !AppVersions.isOlderThan(it, latest) }) {
            throw BadRequestException(BadRequestCause.APP_VERSION_BLOCKED_NOT_BELOW_LATEST)
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
         * A blocked list is for the odd build with a severe bug, not a second way of
         * writing a floor. Fifty is far past any honest use, and bounds the array a save
         * can put in the row and every launch then compares against.
         */
        const val MAX_BLOCKED_VERSIONS = 50

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
