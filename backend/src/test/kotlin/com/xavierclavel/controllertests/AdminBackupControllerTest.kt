package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.getBackupOverview
import main.com.xavierclavel.utils.getBackupOverviewRaw
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import shared.enums.BackupDatabase
import shared.enums.BackupHealth
import shared.infodto.AdminBackupDatabase
import shared.infodto.AdminBackupOverview
import shared.utils.Filepath.BACKUPS_ROOT
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.file.attribute.FileTime

/**
 * The backup volume is real state outside the database, so these tests write actual files
 * onto it. `COOKNCO_BACKUPS_ROOT` points it at build/test-backups (see the root build
 * script), which is what makes that safe.
 *
 * Ages are written into the filenames rather than simulated, because that is where the
 * service reads them from — see `nothing is fresh because its mtime says so` below.
 */
class AdminBackupControllerTest : ApplicationTest() {

    companion object {
        private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        private const val DAY = 86_400L
        private const val HOUR = 3_600L
    }

    /** The volume survives [cleanDb], which only truncates tables. */
    @BeforeEach
    fun emptyVolume() {
        val dir = Path(BACKUPS_ROOT)
        if (dir.exists()) dir.listDirectoryEntries().forEach { it.deleteIfExists() }
    }

    /**
     * Writes a dump as the nightly job would name it: `{database}-{stamp}.dump`, stamped
     * `hoursAgo` in the past.
     */
    private fun writeDump(
        database: BackupDatabase,
        hoursAgo: Long,
        size: Int = 1024,
        unfinished: Boolean = false,
    ): String {
        val stamp = STAMP.format(Instant.now().minusSeconds(hoursAgo * HOUR))
        val name = "${database.filePrefix}-$stamp.dump" + if (unfinished) ".part" else ""
        writeFile(name, size)
        return name
    }

    /**
     * Writes a dump stamped as the nightly run of `nightsAgo` nights ago — 02:15 UTC on
     * that date, which is the schedule.
     *
     * The history figures group by UTC date, so they are exercised with dumps stamped the
     * way the job stamps them rather than with plain offsets from now: two dumps "two
     * hours apart" straddle two dates if the suite happens to run at midnight.
     */
    private fun writeNightlyDump(
        database: BackupDatabase,
        nightsAgo: Long,
        minute: Long = 0,
        size: Int = 1024,
    ): String {
        val night = LocalDate.now(ZoneOffset.UTC).minusDays(nightsAgo)
        val at = night.atTime(2, 15).plusMinutes(minute).toInstant(ZoneOffset.UTC)
        val name = "${database.filePrefix}-${STAMP.format(at)}.dump"
        writeFile(name, size)
        return name
    }

    private fun writeFile(name: String, size: Int = 1024) {
        Path(BACKUPS_ROOT).createDirectories()
        Path("$BACKUPS_ROOT/$name").writeBytes(ByteArray(size) { it.toByte() })
    }

    private fun AdminBackupOverview.database(database: BackupDatabase): AdminBackupDatabase =
        databases.first { it.database == database }

    /** A night's dump for everything that is not the database under test, so it reads OK. */
    private fun writeOtherDatabases(except: BackupDatabase) =
        BackupDatabase.entries.filter { it != except }.forEach { writeDump(it, hoursAgo = 1) }

    // ----------------------------------------------------------- authorisation

    @Test
    fun `backups are closed to anonymous callers`() = runTest {
        client.getBackupOverviewRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `backups are closed to regular users`() = runTestAsUser {
        client.getBackupOverviewRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // ---------------------------------------------------------------- freshness

    @Test
    fun `a volume with no dump at all reports every database missing`() = runTestAsAdmin {
        val overview = client.getBackupOverview()
        assertEquals(BackupHealth.MISSING, overview.health)
        assertEquals(BackupDatabase.entries.size, overview.databases.size)
        assertTrue(overview.databases.all { it.health == BackupHealth.MISSING && it.dumps == 0 })
        assertTrue(overview.dumps.isEmpty())
        assertEquals(0L, overview.totalBytes)
    }

    @Test
    fun `last night's dump reads healthy`() = runTestAsAdmin {
        BackupDatabase.entries.forEach { writeDump(it, hoursAgo = 6) }

        val overview = client.getBackupOverview()
        assertEquals(BackupHealth.OK, overview.health)
        assertTrue(overview.databases.all { it.health == BackupHealth.OK })
        assertEquals(BackupDatabase.entries.size, overview.dumps.size)
    }

    @Test
    fun `a dump still inside its grace is not called late`() = runTestAsAdmin {
        // The schedule is nightly and the grace is two hours, so 25h has not missed a night
        BackupDatabase.entries.forEach { writeDump(it, hoursAgo = 25) }

        assertEquals(BackupHealth.OK, client.getBackupOverview().health)
    }

    @Test
    fun `one missed night is late, a second is stale`() = runTestAsAdmin {
        writeDump(BackupDatabase.COOKNCO, hoursAgo = 30)
        writeOtherDatabases(except = BackupDatabase.COOKNCO)

        client.getBackupOverview().let {
            assertEquals(BackupHealth.LATE, it.database(BackupDatabase.COOKNCO).health)
            // The page leads with the worst of them, not with an average
            assertEquals(BackupHealth.LATE, it.health)
        }

        emptyVolume()
        writeDump(BackupDatabase.COOKNCO, hoursAgo = 60)
        writeOtherDatabases(except = BackupDatabase.COOKNCO)

        client.getBackupOverview().let {
            assertEquals(BackupHealth.STALE, it.database(BackupDatabase.COOKNCO).health)
            assertEquals(BackupHealth.STALE, it.health)
        }
    }

    @Test
    fun `one database falling behind does not hide behind the other`() = runTestAsAdmin {
        writeDump(BackupDatabase.COOKNCO, hoursAgo = 2)
        // mail-service has never been dumped at all

        val overview = client.getBackupOverview()
        assertEquals(BackupHealth.OK, overview.database(BackupDatabase.COOKNCO).health)
        assertEquals(BackupHealth.MISSING, overview.database(BackupDatabase.MAIL_SERVICE).health)
        assertEquals(BackupHealth.MISSING, overview.health)
    }

    /**
     * The failure this whole tab exists to catch: dumps that were moved, copied or restored
     * onto the volume have a brand new mtime and an old stamp, and it is the stamp that
     * tells the truth about when a backup last ran.
     */
    @Test
    fun `nothing is fresh because its mtime says so`() = runTestAsAdmin {
        val name = writeDump(BackupDatabase.COOKNCO, hoursAgo = 20 * 24)
        Path("$BACKUPS_ROOT/$name").setLastModifiedTime(FileTime.fromMillis(System.currentTimeMillis()))
        writeOtherDatabases(except = BackupDatabase.COOKNCO)

        val overview = client.getBackupOverview()
        assertEquals(BackupHealth.STALE, overview.database(BackupDatabase.COOKNCO).health)

        val row = overview.dumps.first { it.filename == name }
        assertNotNull(row.takenAt)
        // Both are reported; they simply do not agree, and the page shows that
        assertTrue(row.lastModified > row.takenAt!!)
    }

    // ------------------------------------------------------------ what counts

    @Test
    fun `a database whose name contains a hyphen is still recognised`() = runTestAsAdmin {
        val name = writeDump(BackupDatabase.MAIL_SERVICE, hoursAgo = 3)
        assertTrue(name.startsWith("mail-service-"))

        val overview = client.getBackupOverview()
        assertEquals(1, overview.database(BackupDatabase.MAIL_SERVICE).dumps)
        assertEquals(BackupDatabase.MAIL_SERVICE, overview.dumps.single().database)
        assertTrue(overview.strays.isEmpty())
    }

    @Test
    fun `a file that is not one of our dumps is a stray, never a backup`() = runTestAsAdmin {
        writeFile("notes.txt", size = 10)
        writeFile("scratch-20260101-020000.dump", size = 20)          // a database we do not expect
        writeFile("cooknco-notadate-020000.dump", size = 30)          // shaped right, stamped wrong
        writeFile("cooknco-20260101-021500.dump.gz", size = 40)       // not the format the job writes

        val overview = client.getBackupOverview()
        assertEquals(BackupHealth.MISSING, overview.database(BackupDatabase.COOKNCO).health)
        assertEquals(0, overview.database(BackupDatabase.COOKNCO).dumps)
        assertEquals(4, overview.strays.size)
        assertTrue(overview.strays.all { it.database == null && it.takenAt == null })
        // They occupy the volume all the same, so they count towards what is on it
        assertEquals(100L, overview.totalBytes)
    }

    @Test
    fun `a part file is a run that died, not a backup`() = runTestAsAdmin {
        writeDump(BackupDatabase.COOKNCO, hoursAgo = 26, unfinished = true)
        writeOtherDatabases(except = BackupDatabase.COOKNCO)

        val overview = client.getBackupOverview()
        assertEquals(BackupHealth.MISSING, overview.database(BackupDatabase.COOKNCO).health)
        assertTrue(overview.dumps.none { it.database == BackupDatabase.COOKNCO })

        val unfinished = overview.unfinished.single()
        assertEquals(BackupDatabase.COOKNCO, unfinished.database)
        assertTrue(unfinished.filename.endsWith(".dump.part"))
    }

    // --------------------------------------------------------------- history

    @Test
    fun `a dump that collapses in size against the night before is flagged`() = runTestAsAdmin {
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 2, size = 4000)
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 1, size = 4200)
        val collapsed = writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0, size = 100)

        val overview = client.getBackupOverview()
        val rows = overview.dumps.associateBy { it.filename }
        assertTrue(rows.getValue(collapsed).shrunk)
        assertEquals(4200L, rows.getValue(collapsed).previousBytes)
        assertFalse(rows.values.first { it.bytes == 4200L }.shrunk)
        // The oldest has nothing to be compared against
        assertNull(rows.values.first { it.bytes == 4000L }.previousBytes)
    }

    @Test
    fun `ordinary growth is not mistaken for a collapse`() = runTestAsAdmin {
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 1, size = 4000)
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0, size = 3600)

        assertTrue(client.getBackupOverview().dumps.none { it.shrunk })
    }

    /**
     * mail-service's dumps are an order of magnitude smaller than the application
     * database's, so a chain built across both reads every mail-service dump that follows
     * a cooknco one as a collapse. The stamps below interleave the two deliberately: with
     * one chain the mail-service dump of two nights ago follows an 8 KB cooknco dump and
     * would be flagged, and with one chain per database nothing is.
     */
    @Test
    fun `sizes are only compared within a database`() = runTestAsAdmin {
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 2, size = 8000)
        writeNightlyDump(BackupDatabase.MAIL_SERVICE, nightsAgo = 1, size = 200)
        writeNightlyDump(BackupDatabase.MAIL_SERVICE, nightsAgo = 0, size = 210)
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0, minute = 1, size = 8200)

        val dumps = client.getBackupOverview().dumps
        assertEquals(4, dumps.size)
        assertTrue(dumps.none { it.shrunk })
        // Each dump was measured against its own database's predecessor, or against nothing
        assertEquals(8000L, dumps.first { it.bytes == 8200L }.previousBytes)
        assertEquals(200L, dumps.first { it.bytes == 210L }.previousBytes)
        assertNull(dumps.first { it.bytes == 200L }.previousBytes)
    }

    @Test
    fun `nights that produced no dump are counted against the ones that could have`() =
        runTestAsAdmin {
            writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 4)
            writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 3)
            // two nights missed here
            writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0)

            val database = client.getBackupOverview().database(BackupDatabase.COOKNCO)
            assertEquals(3, database.nightsCovered)
            assertEquals(5, database.nightsExpected)
        }

    @Test
    fun `a job deployed yesterday is not charged for the nights before it existed`() =
        runTestAsAdmin {
            writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 1)
            writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0)

            val database = client.getBackupOverview().database(BackupDatabase.COOKNCO)
            assertEquals(2, database.nightsCovered)
            // Not 14: nothing older than the oldest dump on the volume was ever expected
            assertEquals(2, database.nightsExpected)
        }

    @Test
    fun `two runs in one night count as one night`() = runTestAsAdmin {
        // An off-schedule run, as `kubectl create job --from=cronjob/database-backup` makes
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0)
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0, minute = 40)

        val database = client.getBackupOverview().database(BackupDatabase.COOKNCO)
        assertEquals(2, database.dumps)
        assertEquals(1, database.nightsCovered)
        assertEquals(1, database.nightsExpected)
    }

    @Test
    fun `the newest and oldest dump of each database are named`() = runTestAsAdmin {
        val oldest = writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 3, size = 10)
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 2, size = 20)
        val newest = writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0, size = 30)

        val database = client.getBackupOverview().database(BackupDatabase.COOKNCO)
        assertEquals(3, database.dumps)
        assertEquals(60L, database.bytes)
        assertEquals(newest, database.latest?.filename)
        assertEquals(oldest, database.oldest?.filename)
    }

    @Test
    fun `dumps are listed newest first`() = runTestAsAdmin {
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 2)
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 0)
        writeNightlyDump(BackupDatabase.COOKNCO, nightsAgo = 1)

        val taken = client.getBackupOverview().dumps.map { it.takenAt!! }
        assertEquals(taken.sortedDescending(), taken)
    }

    // --------------------------------------------------------------- reporting

    @Test
    fun `the thresholds the verdicts were reached with are reported alongside them`() =
        runTestAsAdmin {
            val overview = client.getBackupOverview()
            assertEquals(DAY, overview.intervalSeconds)
            assertEquals(DAY + 2 * HOUR, overview.lateAfterSeconds)
            assertEquals(2 * DAY + 2 * HOUR, overview.staleAfterSeconds)
            assertEquals(14, overview.retentionDays)
            assertTrue(overview.mounted)
            assertEquals(BACKUPS_ROOT, overview.path)
        }
}
