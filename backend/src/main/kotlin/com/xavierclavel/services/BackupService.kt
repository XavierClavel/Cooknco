package com.xavierclavel.services

import com.xavierclavel.utils.Configuration
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.BackupDatabase
import shared.enums.BackupHealth
import shared.infodto.AdminBackupDatabase
import shared.infodto.AdminBackupFile
import shared.infodto.AdminBackupOverview
import shared.utils.Filepath.BACKUPS_ROOT
import shared.utils.logger
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.io.path.Path

/**
 * The database backup volume, seen from the backoffice.
 *
 * Nothing watched the nightly `pg_dump` before this. It writes onto a volume of its own
 * (`k8s/base/backup.yaml`) and the only way to know it had stopped was to go looking on
 * the day a restore was needed, which is the one day it is too late.
 *
 * **What it reads, and what it deliberately does not.** This watches the *dumps*, not the
 * CronJob. A job that exits 0 having produced nothing looks healthy from Kubernetes and is
 * indistinguishable from a working one until somebody counts the files, so counting the
 * files is the check. It also means no credential has to be handed to the backup pod for
 * it to report in, and no table has to be written to on a schedule: the volume already
 * holds the evidence, and evidence is better than a self-report either way.
 *
 * **Read-only, on purpose.** There is nothing here that deletes a dump — retention already
 * does that, on the volume, where a mistake is survivable — and nothing that serves one:
 * a dump is every user's data in one file, and a download button behind an admin session
 * turns one stolen cookie into the whole database. Copying one out stays a `kubectl cp`
 * by somebody with cluster access (`k8s/README.md`).
 *
 * Scans on demand and caches nothing, like [StorageService]: a stale backup figure is
 * worse than a slow one, since the whole point is to notice the night it changes.
 */
class BackupService: KoinComponent {
    private val configuration: Configuration by inject()

    companion object {
        /**
         * `{database}-{yyyyMMdd-HHmmss}.dump`, optionally still `.part`.
         *
         * The prefix is greedy over hyphens because the databases have them in their names
         * — `mail-service-20260914-021500.dump` — so the stamp is what anchors the split.
         */
        private val FILENAME = Regex("""^(.+)-(\d{8}-\d{6})\.dump(\.part)?$""")

        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }

    // ------------------------------------------------------------------ scan

    /** A file on the volume, before it is known whether it belongs to a database we expect. */
    private data class BackupFile(
        val filename: String,
        val database: BackupDatabase?,
        val takenAt: Long?,
        val bytes: Long,
        val lastModified: Long,
        val unfinished: Boolean,
    )

    /**
     * Lists the volume's regular files.
     *
     * A volume that is not mounted is not a failure to report as one: outside the cluster
     * there is no backup job at all, and the tab says so rather than painting a developer's
     * laptop as a fortnight of missed backups.
     */
    private fun scan(): List<BackupFile> {
        val dir = Path(BACKUPS_ROOT)
        if (!Files.isDirectory(dir)) return emptyList()
        return try {
            Files.newDirectoryStream(dir).use { stream -> stream.mapNotNull { readFile(it) } }
        } catch (e: IOException) {
            logger.error(e) { "Could not read the backup volume $BACKUPS_ROOT" }
            emptyList()
        }
    }

    private fun readFile(path: Path): BackupFile? {
        val attributes = try {
            Files.readAttributes(path, BasicFileAttributes::class.java)
        } catch (e: IOException) {
            // Vanished between the listing and the stat — a dump being rotated under us
            return null
        }
        if (!attributes.isRegularFile) return null

        val filename = path.fileName.toString()
        val match = FILENAME.matchEntire(filename)
        // Both halves have to hold for the file to count as one of ours: a name shaped like
        // a dump but naming something else, or carrying a stamp that is not a date, is a
        // stray. Neither may be counted as a night's backup — a file somebody copied onto
        // the volume reporting as the backup that did not happen is the worst answer here.
        val takenAt = match?.let { parseStamp(it.groupValues[2]) }
        val database = match?.takeIf { takenAt != null }?.let { BackupDatabase.fromFilePrefix(it.groupValues[1]) }
        return BackupFile(
            filename = filename,
            database = database,
            takenAt = takenAt.takeIf { database != null },
            bytes = attributes.size(),
            lastModified = attributes.lastModifiedTime().toMillis() / 1000,
            unfinished = match?.groupValues?.get(3)?.isNotEmpty() == true,
        )
    }

    /** The job stamps its filenames with `date -u`, so the stamp is read back as UTC. */
    private fun parseStamp(stamp: String): Long? = try {
        LocalDateTime.parse(stamp, STAMP).toEpochSecond(ZoneOffset.UTC)
    } catch (e: DateTimeParseException) {
        null
    }

    // -------------------------------------------------------------- freshness

    private val intervalSeconds: Long get() = configuration.backups.intervalHours * 3600
    private val graceSeconds: Long get() = configuration.backups.graceHours * 3600

    /** One scheduled run plus the slack a displaced run is allowed. */
    private val lateAfterSeconds: Long get() = intervalSeconds + graceSeconds

    /** A second run missed on top of the first: the job is not running, rather than late. */
    private val staleAfterSeconds: Long get() = 2 * intervalSeconds + graceSeconds

    /**
     * Freshness, measured from the run that wrote the dump rather than from the file's mtime.
     *
     * The mtime is when the bytes last landed, which a restored volume or a file copied in
     * by hand resets to now — and a backups page that goes green because somebody moved
     * files around is the exact failure it exists to prevent. See [AdminBackupFile.takenAt].
     */
    private fun healthOf(latest: BackupFile?, now: Long): BackupHealth {
        val takenAt = latest?.takenAt ?: return BackupHealth.MISSING
        val age = now - takenAt
        return when {
            age > staleAfterSeconds -> BackupHealth.STALE
            age > lateAfterSeconds -> BackupHealth.LATE
            else -> BackupHealth.OK
        }
    }

    // --------------------------------------------------------------- overview

    fun buildOverview(): AdminBackupOverview {
        val startedAt = System.currentTimeMillis()
        val now = startedAt / 1000
        val files = scan()
        val root = File(BACKUPS_ROOT)
        val mounted = root.isDirectory

        val (ours, strays) = files.partition { it.database != null && it.takenAt != null }
        val (unfinished, dumps) = ours.partition { it.unfinished }
        val byDatabase = dumps.groupBy { it.database!! }

        // Newest first everywhere: a backups page is read from the most recent night down.
        val ordered = dumps.sortedByDescending { it.takenAt }
        val previousBytes = previousBytesByFilename(byDatabase)

        val databases = BackupDatabase.entries.map { summarise(it, byDatabase[it].orEmpty(), now, previousBytes) }

        return AdminBackupOverview(
            mounted = mounted,
            path = BACKUPS_ROOT,
            health = databases.map { it.health }.reduce(BackupHealth::worstOf),
            databases = databases,
            dumps = ordered.map { it.toInfo(previousBytes[it.filename]) },
            unfinished = unfinished.sortedByDescending { it.lastModified }.map { it.toInfo(null) },
            strays = strays.sortedByDescending { it.lastModified }.map { it.toInfo(null) },
            totalBytes = files.sumOf { it.bytes },
            volumeTotalBytes = if (mounted) root.totalSpace else 0,
            volumeFreeBytes = if (mounted) root.usableSpace else 0,
            intervalSeconds = intervalSeconds,
            lateAfterSeconds = lateAfterSeconds,
            staleAfterSeconds = staleAfterSeconds,
            retentionDays = configuration.backups.retentionDays,
            scanDurationMs = System.currentTimeMillis() - startedAt,
        )
    }

    /**
     * Each dump paired with the size of the one before it, per database.
     *
     * Sizes are only comparable within a database — the application database and the mail
     * one differ by an order of magnitude — so the chain is built per database and then
     * flattened, rather than comparing a dump against whatever happens to precede it on disk.
     */
    private fun previousBytesByFilename(byDatabase: Map<BackupDatabase, List<BackupFile>>): Map<String, Long> =
        byDatabase.values.flatMap { files ->
            files.sortedBy { it.takenAt }
                .zipWithNext { previous, current -> current.filename to previous.bytes }
        }.toMap()

    private fun summarise(
        database: BackupDatabase,
        files: List<BackupFile>,
        now: Long,
        previousBytes: Map<String, Long>,
    ): AdminBackupDatabase {
        val ordered = files.sortedByDescending { it.takenAt }
        val latest = ordered.firstOrNull()
        val oldest = ordered.lastOrNull()
        val nights = nights(files)

        return AdminBackupDatabase(
            database = database,
            health = healthOf(latest, now),
            dumps = files.size,
            bytes = files.sumOf { it.bytes },
            latest = latest?.toInfo(previousBytes[latest.filename]),
            oldest = oldest?.toInfo(previousBytes[oldest.filename]),
            nightsCovered = nights.size,
            nightsExpected = nightsExpected(nights),
        )
    }

    /**
     * The distinct UTC dates that produced a dump.
     *
     * Dates rather than elapsed hours, because the job runs at a fixed time of night: two
     * runs on one date are one night's backup, however far apart they were, and the
     * off-schedule run an operator kicks off by hand must not read as an extra night.
     */
    private fun nights(files: List<BackupFile>): Set<LocalDate> =
        files.mapNotNullTo(HashSet()) { it.takenAt?.let { at -> LocalDate.ofEpochDay(at / 86400) } }

    /**
     * How many nights the volume's span covers, gaps included.
     *
     * Measured from the oldest dump to the newest rather than up to tonight, which makes
     * this purely a question about the history and leaves the question of whether the most
     * recent backup ran to [healthOf]. Two figures that each answer one thing: a job
     * deployed three days ago reads 3 of 3 rather than 3 of 14, and a job that stopped a
     * week ago stays at 14 of 14 here while going [BackupHealth.STALE] there — which is
     * the truth, since every night it was running, it worked.
     */
    private fun nightsExpected(nights: Set<LocalDate>): Int {
        if (nights.isEmpty()) return 0
        return (ChronoUnit.DAYS.between(nights.min(), nights.max()) + 1).toInt()
    }

    private fun BackupFile.toInfo(previousBytes: Long?) = AdminBackupFile(
        filename = filename,
        database = database,
        takenAt = takenAt,
        bytes = bytes,
        lastModified = lastModified,
        previousBytes = previousBytes,
        shrunk = previousBytes != null && bytes < previousBytes * configuration.backups.shrinkRatio,
    )
}
