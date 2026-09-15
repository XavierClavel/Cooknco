package shared.enums

/**
 * How fresh a database's most recent dump is.
 *
 * The distinction that matters is [LATE] against [STALE]: one missed night is a job to go
 * and look at, several is a backup that has stopped happening and needs acting on now.
 * Both look identical in a plain file listing, which is why they are separated here.
 */
enum class BackupHealth {
    /** A dump from the last scheduled run is on the volume. */
    OK,

    /** The newest dump is past its window — one night was missed, or a run is overdue. */
    LATE,

    /** Nothing has been dumped for more than one further night: backups are not running. */
    STALE,

    /** No dump at all for this database — it is not being backed up, or never was. */
    MISSING,
    ;

    /** Worst-case merge, for the single figure the page leads with. */
    fun worstOf(other: BackupHealth) = if (other.ordinal > ordinal) other else this
}
