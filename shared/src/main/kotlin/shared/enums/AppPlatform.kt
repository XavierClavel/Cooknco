package shared.enums

/**
 * A client whose build an operator can gate.
 *
 * Only the two store-distributed apps. The web app is not one of them and deliberately has
 * no row here: a browser fetches the current bundle on every load, so there is no stale
 * build to lock out and no store to send anyone to. A user running yesterday's SPA in an
 * open tab is a reload away from the newest one, which is not what a force update is for.
 *
 * Distinct from [DevicePlatform], which says where a *push* can be delivered — it carries a
 * WEB entry for exactly the client this one leaves out, and gaining an entry there (a new
 * push transport) must not silently create a version gate nobody configured.
 */
enum class AppPlatform {
    ANDROID,
    IOS,
    ;

    /**
     * The push platform a gated client registers its devices under.
     *
     * Written out rather than matched on `name` so that the two enums drifting apart is a
     * compile error here — which is the point of their being separate. `devices` is the
     * only record of what versions are installed, so this mapping is what lets a floor be
     * costed before it is raised (`AppVersionService.reach`).
     */
    fun devicePlatform(): DevicePlatform = when (this) {
        ANDROID -> DevicePlatform.ANDROID
        IOS -> DevicePlatform.IOS
    }
}
