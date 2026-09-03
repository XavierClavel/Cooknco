package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ebean.DB
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.cleanupStorage
import main.com.xavierclavel.utils.cleanupStorageRaw
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.deleteImageRaw
import main.com.xavierclavel.utils.getStorageOverview
import main.com.xavierclavel.utils.getStorageOverviewRaw
import main.com.xavierclavel.utils.listImages
import main.com.xavierclavel.utils.listImagesRaw
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import shared.enums.ImageBucket
import shared.enums.ImageSort
import shared.enums.ImageStatus
import shared.infodto.AdminStorageBucket
import shared.infodto.AdminStorageOverview
import shared.infodto.RecipeInfo
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The image volume is real state outside the database, so these tests write actual files
 * into the buckets. `COOKNCO_IMG_ROOT` points the tree at build/test-img (see the root
 * build script), which is what makes that safe.
 */
class AdminStorageControllerTest : ApplicationTest() {

    /**
     * The volume survives [cleanDb], which only truncates tables — a file left by one test
     * would be an orphan in the next one and would count towards its assertions.
     */
    @BeforeEach
    fun emptyVolume() {
        ImageBucket.entries.forEach { bucket ->
            val dir = Path(bucket.path)
            if (dir.exists()) dir.listDirectoryEntries().forEach { it.deleteIfExists() }
        }
    }

    private fun write(bucket: ImageBucket, filename: String, size: Int = 64) {
        Path("${bucket.path}/$filename")
            .createParentDirectories()
            .writeBytes(ByteArray(size) { it.toByte() })
    }

    private fun exists(bucket: ImageBucket, filename: String) = Files.exists(Path("${bucket.path}/$filename"))

    /** Images are only ever uploaded through multipart, so tests set the version directly. */
    private fun setImageVersion(table: String, id: Long, version: Long) {
        DB.sqlUpdate("update $table set image_version = :v where id = :id")
            .setParameter("v", version)
            .setParameter("id", id)
            .execute()
    }

    private fun AdminStorageOverview.bucket(bucket: ImageBucket): AdminStorageBucket =
        buckets.first { it.bucket == bucket }

    private fun AdminStorageBucket.count(status: ImageStatus) = countByStatus[status] ?: 0

    // ----------------------------------------------------------- authorisation

    @Test
    fun `storage is closed to anonymous callers`() = runTest {
        client.getStorageOverviewRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.listImagesRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.cleanupStorageRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `storage is closed to regular users`() = runTestAsUser {
        client.getStorageOverviewRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.deleteImageRaw(ImageBucket.RECIPE, "1-v1.webp")
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // ---------------------------------------------------------------- overview

    @Test
    fun `an empty volume reports no files`() = runTestAsAdmin {
        val overview = client.getStorageOverview()
        assertEquals(0, overview.totalFiles)
        assertEquals(0L, overview.totalBytes)
        assertEquals(0, overview.reclaimableFiles)
        assertEquals(ImageBucket.entries.size, overview.buckets.size)
    }

    @Test
    fun `files are classified against the version their owner points at`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        setImageVersion("recipes", recipe!!.id, 2)

        write(ImageBucket.RECIPE, "${recipe!!.id}-v2.webp", size = 100)   // in use
        write(ImageBucket.RECIPE, "${recipe!!.id}-v1.webp", size = 200)   // superseded
        write(ImageBucket.RECIPE, "999999-v1.webp", size = 300)         // owner is gone
        write(ImageBucket.RECIPE, "notes.txt", size = 400)              // not ours

        runAsAdmin {
            val bucket = client.getStorageOverview().bucket(ImageBucket.RECIPE)
            assertEquals(4, bucket.files)
            assertEquals(1000L, bucket.bytes)
            assertEquals(1, bucket.count(ImageStatus.CURRENT))
            assertEquals(1, bucket.count(ImageStatus.STALE))
            assertEquals(1, bucket.count(ImageStatus.ORPHAN))
            assertEquals(1, bucket.count(ImageStatus.UNKNOWN))
            assertEquals(200L, bucket.bytesByStatus[ImageStatus.STALE])
            assertEquals(400L, bucket.largestFileBytes)
        }
    }

    @Test
    fun `reclaimable totals cover every bucket`() = runTest {
        write(ImageBucket.RECIPE, "999997-v1.webp", size = 10)
        write(ImageBucket.USER, "999998-v1.webp", size = 20)
        write(ImageBucket.COOKBOOK, "999999-v1.webp", size = 30)

        runAsAdmin {
            val overview = client.getStorageOverview()
            // No entity carries those ids, so every file is an orphan
            assertEquals(3, overview.reclaimableFiles)
            assertEquals(60L, overview.reclaimableBytes)
        }
    }

    // ----------------------------------------------------------------- listing

    @Test
    fun `the listing names the owner behind each file`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        setImageVersion("recipes", recipe!!.id, 3)
        write(ImageBucket.RECIPE, "${recipe!!.id}-v3.webp")

        runAsAdmin {
            val row = client.listImages(bucket = ImageBucket.RECIPE).items.single()
            assertEquals(ImageStatus.CURRENT, row.status)
            assertEquals(recipe!!.id, row.ownerId)
            assertEquals(3L, row.version)
            assertEquals(3L, row.ownerVersion)
            assertEquals(recipe!!.title, row.ownerLabel)
        }
    }

    @Test
    fun `an owner pointing at a file that is not on disk is listed as missing`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        setImageVersion("recipes", recipe!!.id, 4)

        runAsAdmin {
            val overview = client.getStorageOverview()
            // Both image buckets read the same recipe version, so both are missing a file
            assertEquals(1, overview.bucket(ImageBucket.RECIPE).count(ImageStatus.MISSING))
            assertEquals(2, overview.missingImages)

            val row = client.listImages(bucket = ImageBucket.RECIPE, status = ImageStatus.MISSING).items.single()
            assertEquals("${recipe!!.id}-v4.webp", row.filename)
            assertEquals(0L, row.bytes)
            assertNull(row.lastModified)
            assertEquals(recipe!!.title, row.ownerLabel)
        }
    }

    @Test
    fun `an owner with no image at all is not reported as missing`() = runTest {
        runAsUser1 { client.createRecipe() }
        runAsAdmin {
            assertEquals(0, client.getStorageOverview().missingImages)
        }
    }

    @Test
    fun `the listing filters by status and searches filename and owner`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        setImageVersion("recipes", recipe!!.id, 2)
        write(ImageBucket.RECIPE, "${recipe!!.id}-v2.webp")
        write(ImageBucket.RECIPE, "${recipe!!.id}-v1.webp")
        write(ImageBucket.RECIPE, "999999-v1.webp")

        runAsAdmin {
            assertEquals(1, client.listImages(status = ImageStatus.STALE).count)
            assertEquals(1, client.listImages(status = ImageStatus.ORPHAN).count)
            assertEquals(1, client.listImages(query = "999999").count)
            // The orphan has no owner row, so a search on the recipe title excludes it
            assertEquals(2, client.listImages(bucket = ImageBucket.RECIPE, query = recipe!!.title).count)
        }
    }

    @Test
    fun `the listing sorts by size`() = runTest {
        write(ImageBucket.RECIPE, "1-v1.webp", size = 10)
        write(ImageBucket.RECIPE, "2-v1.webp", size = 30)
        write(ImageBucket.RECIPE, "3-v1.webp", size = 20)

        runAsAdmin {
            assertEquals(
                listOf(30L, 20L, 10L),
                client.listImages(sort = ImageSort.SIZE_DESCENDING).items.map { it.bytes },
            )
            assertEquals(
                listOf(10L, 20L, 30L),
                client.listImages(sort = ImageSort.SIZE_ASCENDING).items.map { it.bytes },
            )
        }
    }

    @Test
    fun `buckets are listed separately`() = runTest {
        write(ImageBucket.RECIPE, "1-v1.webp")
        write(ImageBucket.RECIPE_THUMBNAIL, "1-v1.webp")
        write(ImageBucket.USER, "1-v1.webp")

        runAsAdmin {
            assertEquals(3, client.listImages().count)
            assertEquals(1, client.listImages(bucket = ImageBucket.RECIPE_THUMBNAIL).count)
        }
    }

    // ---------------------------------------------------------------- deletion

    @Test
    fun `a single file can be deleted`() = runTestAsAdmin {
        write(ImageBucket.RECIPE, "999999-v1.webp")
        client.deleteImageRaw(ImageBucket.RECIPE, "999999-v1.webp").apply { assertEquals(HttpStatusCode.OK, status) }
        assertFalse(exists(ImageBucket.RECIPE, "999999-v1.webp"))
    }

    @Test
    fun `deleting a file that is already gone is a not found`() = runTestAsAdmin {
        client.deleteImageRaw(ImageBucket.RECIPE, "999999-v1.webp")
            .apply { assertEquals(HttpStatusCode.NotFound, status) }
    }

    @Test
    fun `delete refuses to leave the bucket`() = runTestAsAdmin {
        client.deleteImageRaw(ImageBucket.RECIPE, "../users/1-v1.webp")
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
        client.deleteImageRaw(ImageBucket.RECIPE, null)
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
        client.deleteImageRaw(null, "1-v1.webp")
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
    }

    @Test
    fun `the shipped fallback image is never touched`() = runTest {
        write(ImageBucket.RECIPE, "default.webp")
        runAsAdmin {
            val row = client.listImages(bucket = ImageBucket.RECIPE).items.single()
            assertEquals(ImageStatus.CURRENT, row.status)
            assertNull(row.ownerId)

            client.deleteImageRaw(ImageBucket.RECIPE, "default.webp")
                .apply { assertEquals(HttpStatusCode.BadRequest, status) }

            client.cleanupStorage(statuses = ImageStatus.entries.filter { it.isReclaimable() })
            assertTrue(exists(ImageBucket.RECIPE, "default.webp"))
        }
    }

    // ----------------------------------------------------------------- cleanup

    @Test
    fun `cleanup removes superseded and orphaned files and leaves the rest`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        setImageVersion("recipes", recipe!!.id, 2)
        write(ImageBucket.RECIPE, "${recipe!!.id}-v2.webp", size = 100)
        write(ImageBucket.RECIPE, "${recipe!!.id}-v1.webp", size = 200)
        write(ImageBucket.RECIPE, "999999-v1.webp", size = 300)
        write(ImageBucket.RECIPE, "notes.txt", size = 400)

        runAsAdmin {
            val result = client.cleanupStorage()
            assertEquals(2, result.files)
            assertEquals(500L, result.bytes)
            assertEquals(0, result.failed)

            assertTrue(exists(ImageBucket.RECIPE, "${recipe!!.id}-v2.webp"))
            assertFalse(exists(ImageBucket.RECIPE, "${recipe!!.id}-v1.webp"))
            assertFalse(exists(ImageBucket.RECIPE, "999999-v1.webp"))
            assertTrue(exists(ImageBucket.RECIPE, "notes.txt"))
        }
    }

    @Test
    fun `a dry run counts without deleting`() = runTestAsAdmin {
        write(ImageBucket.RECIPE, "999999-v1.webp", size = 300)

        val result = client.cleanupStorage(dryRun = true)
        assertTrue(result.dryRun)
        assertEquals(1, result.files)
        assertEquals(300L, result.bytes)
        assertTrue(exists(ImageBucket.RECIPE, "999999-v1.webp"))
    }

    @Test
    fun `cleanup can be scoped to one bucket`() = runTestAsAdmin {
        write(ImageBucket.RECIPE, "999999-v1.webp")
        write(ImageBucket.USER, "999999-v1.webp")

        assertEquals(1, client.cleanupStorage(buckets = listOf(ImageBucket.USER)).files)
        assertTrue(exists(ImageBucket.RECIPE, "999999-v1.webp"))
        assertFalse(exists(ImageBucket.USER, "999999-v1.webp"))
    }

    @Test
    fun `cleanup refuses statuses that describe a live or absent file`() = runTestAsAdmin {
        write(ImageBucket.RECIPE, "999999-v1.webp")

        client.cleanupStorageRaw(statuses = listOf(ImageStatus.CURRENT))
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
        client.cleanupStorageRaw(statuses = listOf(ImageStatus.ORPHAN, ImageStatus.MISSING))
            .apply { assertEquals(HttpStatusCode.BadRequest, status) }
        assertTrue(exists(ImageBucket.RECIPE, "999999-v1.webp"))
    }

    @Test
    fun `an unknown file is only removed when explicitly asked for`() = runTestAsAdmin {
        write(ImageBucket.RECIPE, "notes.txt")

        assertEquals(0, client.cleanupStorage().files)
        assertTrue(exists(ImageBucket.RECIPE, "notes.txt"))

        assertEquals(1, client.cleanupStorage(statuses = listOf(ImageStatus.UNKNOWN)).files)
        assertFalse(exists(ImageBucket.RECIPE, "notes.txt"))
    }

    @Test
    fun `recipe images and thumbnails answer to the same recipe`() = runTest {
        var recipe: RecipeInfo? = null
        runAsUser1 { recipe = client.createRecipe() }
        setImageVersion("recipes", recipe!!.id, 1)
        write(ImageBucket.RECIPE, "${recipe!!.id}-v1.webp")
        // The thumbnail was never written, which is what a recipe predating them looks like
        runAsAdmin {
            val overview = client.getStorageOverview()
            assertEquals(0, overview.bucket(ImageBucket.RECIPE).count(ImageStatus.MISSING))
            assertEquals(1, overview.bucket(ImageBucket.RECIPE_THUMBNAIL).count(ImageStatus.MISSING))
            assertNotNull(overview.bucket(ImageBucket.RECIPE).lastModified)
        }
    }
}
