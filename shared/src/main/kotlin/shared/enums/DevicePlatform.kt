package shared.enums

/**
 * The kind of client a push token was issued for.
 *
 * Recorded per device rather than inferred from the token, because a token is an opaque
 * string that says nothing about where it came from, and the payload FCM needs differs
 * per platform (`android` vs `apns` overrides in the message).
 */
enum class DevicePlatform {
    ANDROID,
    IOS,
    WEB,
}
