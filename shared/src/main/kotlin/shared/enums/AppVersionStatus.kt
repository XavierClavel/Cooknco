package shared.enums

/**
 * What the backend thinks of the build asking.
 *
 * The verdict, not the remedy: the client decides what a status looks like on screen, and
 * only the client can actually enforce [UPDATE_REQUIRED] — see `AppVersionService`.
 */
enum class AppVersionStatus {
    /** Nothing to do. Either up to date, ahead of the store, or no gate is configured. */
    OK,

    /** Runnable, but a newer build is out. A nudge the user may dismiss. */
    UPDATE_AVAILABLE,

    /** Older than the oldest build allowed to run. The client blocks itself. */
    UPDATE_REQUIRED,
}
