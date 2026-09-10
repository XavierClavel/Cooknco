package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.AppPlatform
import shared.enums.AppVersionStatus

/**
 * What the backend tells a client about the build it is running.
 *
 * No copy travels with it. The three versions and the URL are facts an operator owns; the
 * sentence a user reads is the client's own, in the client's own language — which is the
 * only place that knows it, since the app has no locale to render server-side copy in
 * (`ApiClient.LOCALE` is a constant) and a wrong-language block screen is worse than none.
 */
@Serializable
data class AppVersionCheckInfo(
    val status: AppVersionStatus,
    /** Echoed back so a client can say which build it was judged as. */
    val currentVersion: String,
    /** Null when this platform has no gate configured, in which case [status] is OK. */
    val minimumVersion: String? = null,
    val latestVersion: String? = null,
    val storeUrl: String? = null,
)

/**
 * One platform's gate, as the backoffice releases tab shows it.
 *
 * Every platform is listed whether or not it has a row, so the tab shows the set of
 * clients rather than the set of rows somebody happened to create. [configured] is what
 * says the difference.
 *
 * The three strings have no defaults on purpose. The tab binds an input to each of them,
 * including on an ungated row, so they have to be on the wire even when empty — and with
 * a default that would hold only for as long as the server keeps a `Json` that encodes
 * defaults (Ktor's does; a `json(Json { ... })` of our own would not). Without one it is
 * the payload's shape rather than the framework's setting. [ungated] builds the empty row.
 */
@Serializable
data class AdminAppVersionInfo(
    val platform: AppPlatform,
    /** False while no gate exists, in which case the three fields below are empty. */
    val configured: Boolean,
    val minimumVersion: String,
    val latestVersion: String,
    val storeUrl: String,
    /** Epoch seconds. Null while nothing has been saved. */
    val updatedAt: Long? = null,
) {
    companion object {
        /** A platform nobody has gated: listed, editable, and blocking nothing. */
        fun ungated(platform: AppPlatform) = AdminAppVersionInfo(
            platform = platform,
            configured = false,
            minimumVersion = "",
            latestVersion = "",
            storeUrl = "",
        )
    }
}

/**
 * What a version floor would cost, before it is put in force.
 *
 * Counted over the devices that registered recently rather than over every row ever
 * written: a device is touched on each launch, so a handset last seen two years ago says
 * nothing about who a floor would stop today, and counting it would make every gate look
 * more expensive than it is. [windowDays] is how recent "recently" is, so the tab can say
 * which population these numbers describe.
 *
 * Devices only exist here once they have registered for push, so this is a floor on the
 * real reach rather than a census — a user who refused notifications is invisible to it.
 */
@Serializable
data class AdminAppVersionReach(
    val platform: AppPlatform,
    val windowDays: Int,
    /** Devices of this platform seen inside the window. */
    val devices: Int,
    /** Distinct accounts behind them. */
    val users: Int,
    /** The candidate floor the counts below were measured against. */
    val minimumVersion: String,
    val blockedDevices: Int,
    /**
     * Accounts with *at least one* device below the floor.
     *
     * Not "accounts that would be locked out": someone whose phone is stale and whose
     * tablet is current is stopped on one of them and fine on the other. It is the number
     * of people who would see the screen, which is the number worth hesitating over.
     */
    val blockedUsers: Int,
    /**
     * Devices whose build cannot be read — a client too old to report one, or a string we
     * do not understand. Never blocked, so never part of [blockedDevices].
     */
    val unknownDevices: Int,
    /** Newest build first, unknown last. */
    val distribution: List<AdminAppVersionCount>,
)

/** One build, and how much of the install base is on it. */
@Serializable
data class AdminAppVersionCount(
    /** Empty for the devices that report no build at all. */
    val version: String,
    val devices: Int,
    val users: Int,
    /** True when the candidate floor would stop this build. */
    val blocked: Boolean,
)
