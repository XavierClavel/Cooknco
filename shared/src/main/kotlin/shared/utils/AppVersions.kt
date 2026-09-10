package shared.utils

/**
 * How two app builds are ordered.
 *
 * Dotted numbers, compared component by component — `1.10.0` is newer than `1.9.3`, which
 * a string comparison would get backwards. Both stores hand us a string of this shape
 * (Android's `versionName`, iOS's `CFBundleShortVersionString`) and so does
 * `build.gradle.kts`, so there is one rule for all of them.
 */
object AppVersions {

    /** Long enough for `2026.10.31.4`, short enough that a paste of prose is refused. */
    const val MAX_LENGTH = 23

    /** What an operator may save: numbers and dots, nothing else. */
    private val STRICT = Regex("""\d{1,6}(\.\d{1,6}){0,3}""")

    /**
     * What a *client* may report, which is not the same thing.
     *
     * A build can call itself `1.4.0-rc2` or `1.4.0+8` and still be a real install we have
     * to answer for, so the numeric head is read and the rest ignored. That makes a
     * pre-release compare equal to its release, which errs towards letting it run — the
     * direction to err in, since the alternative is locking a tester out of a build that is
     * newer than anything shipped.
     */
    private val LENIENT = Regex("""(\d{1,6}(?:\.\d{1,6}){0,3}).*""")

    /** True for a version an operator may set as a floor or a ceiling. */
    fun isValid(version: String): Boolean =
        version.length <= MAX_LENGTH && STRICT.matches(version)

    /**
     * The numeric components of a version, or null when there are none to read.
     *
     * Null is the answer for anything a client made up, and every caller treats it as
     * "cannot tell" rather than "old". See [compare].
     */
    fun parse(version: String): List<Int>? {
        val head = LENIENT.matchEntire(version.trim())?.groupValues?.get(1) ?: return null
        return head.split('.').map { it.toInt() }
    }

    /**
     * Orders two versions the way [Comparator.compare] does, or returns null when either
     * one cannot be read.
     *
     * Null rather than an arbitrary order on purpose: the one caller is the version gate,
     * and a gate that guessed would lock people out of an app over a version string it did
     * not understand.
     */
    fun compare(a: String, b: String): Int? {
        val left = parse(a) ?: return null
        val right = parse(b) ?: return null
        // Missing trailing components are zeroes, so 1.4 and 1.4.0 are the same build
        repeat(maxOf(left.size, right.size)) { i ->
            val diff = (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    /** True when [version] is strictly older than [other]. False when either is unreadable. */
    fun isOlderThan(version: String, other: String): Boolean =
        (compare(version, other) ?: 0) < 0
}
