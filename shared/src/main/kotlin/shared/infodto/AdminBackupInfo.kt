package shared.infodto

import kotlinx.serialization.Serializable
import shared.enums.BackupDatabase
import shared.enums.BackupHealth

/**
 * The database backup volume, seen from the backoffice.
 *
 * Everything here is read off the dumps themselves rather than from anything the backup
 * job reports. A job that exits 0 having written nothing is the failure worth catching,
 * and only the files can tell that apart from a job that worked.
 */
@Serializable
data class AdminBackupOverview(
    /** False when the volume is not mounted — the normal case outside the cluster. */
    val mounted: Boolean,
    val path: String,
    /** The worst health among [databases]: the one figure the page leads with. */
    val health: BackupHealth,
    val databases: List<AdminBackupDatabase>,
    /** Every finished dump on the volume, newest first. Retention keeps this small. */
    val dumps: List<AdminBackupFile>,
    /** `.dump.part` files: a run that started and did not finish. */
    val unfinished: List<AdminBackupFile>,
    /** Files on the volume that are not dumps of a known database. Counted, never touched. */
    val strays: List<AdminBackupFile>,
    val totalBytes: Long,
    /** Capacity of the filesystem holding the volume; 0 when it is not mounted. */
    val volumeTotalBytes: Long,
    val volumeFreeBytes: Long,
    /** The schedule the freshness verdicts are measured against, in seconds. */
    val intervalSeconds: Long,
    val lateAfterSeconds: Long,
    val staleAfterSeconds: Long,
    /** How many days of dumps the job keeps, so the page can say what a full history is. */
    val retentionDays: Int,
    val scanDurationMs: Long,
)

/** One database's dumps: whether it is being backed up, and how reliably. */
@Serializable
data class AdminBackupDatabase(
    val database: BackupDatabase,
    val health: BackupHealth,
    val dumps: Int,
    val bytes: Long,
    /** Newest dump, the one [health] is read from; null when there is none at all. */
    val latest: AdminBackupFile? = null,
    val oldest: AdminBackupFile? = null,
    /**
     * Nights that produced a dump, against the nights the volume's span covers.
     *
     * A gap measure, and only that: it runs from the oldest dump on the volume to the
     * newest, so a job deployed three days ago reads 3 of 3 rather than 3 of 14, and one
     * that stopped a week ago stays at 14 of 14 while [health] goes stale. Whether the
     * most recent night ran is [health]'s question, and this one does not restate it.
     */
    val nightsCovered: Int,
    val nightsExpected: Int,
)

/** A file on the backup volume. */
@Serializable
data class AdminBackupFile(
    val filename: String,
    /** Null for a file whose name is not a dump of a database we expect. */
    val database: BackupDatabase? = null,
    val bytes: Long,
    /**
     * When the run that wrote this dump started, parsed from the stamp in its name; null
     * on a file that is not one of ours.
     *
     * This, not the mtime, is what freshness is measured from: the mtime says when the
     * bytes last landed, which a restored volume or a copied-in file resets to now, and a
     * backup page that goes green because the dumps were copied is the one failure it
     * exists to prevent. Both are shown.
     */
    val takenAt: Long? = null,
    /** Epoch seconds, as everywhere else in the API. */
    val lastModified: Long,
    /** The same database's preceding dump, for the size comparison below; null for the first. */
    val previousBytes: Long? = null,
    /**
     * Set when this dump is sharply smaller than the one before it.
     *
     * A dump that succeeds against an empty or half-restored database is the silent
     * failure this catches: it is a valid file, of a plausible age, holding nothing.
     */
    val shrunk: Boolean = false,
)
