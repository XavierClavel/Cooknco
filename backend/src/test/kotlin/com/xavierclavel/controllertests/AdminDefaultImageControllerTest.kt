package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.listDefaultImages
import main.com.xavierclavel.utils.listDefaultImagesRaw
import main.com.xavierclavel.utils.resetDefaultImageRaw
import main.com.xavierclavel.utils.uploadDefaultImageRaw
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import shared.enums.DefaultImage
import shared.enums.ImageBucket
import shared.utils.Filepath.DEFAULT_IMAGE
import shared.utils.URL.ADMIN_URL
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pictures shown in place of the ones users never uploaded.
 *
 * They are the one thing on the image volume with no owning row, which is why they get
 * their own endpoints rather than going through the file listing: what an operator
 * replaces is "the recipe picture", not the two files it happens to be written to.
 */
class AdminDefaultImageControllerTest : ApplicationTest() {

    /** As in the storage tests, the volume outlives [cleanDb] and has to be cleared by hand. */
    @BeforeEach
    fun emptyVolume() {
        ImageBucket.entries.forEach { bucket ->
            val dir = Path(bucket.path)
            if (dir.exists()) dir.listDirectoryEntries().forEach { it.deleteIfExists() }
        }
    }

    private fun uploadedFile(bucket: ImageBucket) = Path("${bucket.path}/$DEFAULT_IMAGE")

    /** A recognisable picture: metadata-extractor rejects anything that is not really one. */
    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color.MAGENTA
            fillRect(0, 0, width, height)
            dispose()
        }
        return ByteArrayOutputStream().use {
            ImageIO.write(image, "png", it)
            it.toByteArray()
        }
    }

    private fun dimensionsOf(bytes: ByteArray): Pair<Int, Int> =
        ImageIO.read(ByteArrayInputStream(bytes)).let { it.width to it.height }

    // ----------------------------------------------------------- authorisation

    @Test
    fun `default images are closed to anonymous callers`() = runTest {
        client.listDefaultImagesRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.uploadDefaultImageRaw(DefaultImage.USER, png(64, 64))
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.resetDefaultImageRaw(DefaultImage.USER)
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    @Test
    fun `default images are closed to regular users`() = runTestAsUser {
        client.listDefaultImagesRaw().apply { assertEquals(HttpStatusCode.Unauthorized, status) }
        client.uploadDefaultImageRaw(DefaultImage.RECIPE, png(64, 64))
            .apply { assertEquals(HttpStatusCode.Unauthorized, status) }
    }

    // --------------------------------------------------------------- listing

    @Test
    fun `an untouched volume serves the pictures packaged with the app`() = runTestAsAdmin {
        val defaults = client.listDefaultImages()

        assertEquals(DefaultImage.entries, defaults.map { it.image })
        defaults.forEach { default ->
            assertFalse(default.custom, "${default.image} should not be reported as custom")
            assertEquals(default.image.buckets, default.files.map { it.bucket })
            default.files.forEach {
                assertNull(it.lastModified, "${it.bucket} has nothing uploaded to date")
                // The packaged picture still has a size: it is what visitors are served
                assertTrue(it.bytes > 0, "${it.bucket} should report the packaged picture's size")
                assertFalse(uploadedFile(it.bucket).exists(), "${it.bucket} must stay empty")
            }
        }
    }

    @Test
    fun `a bucket serves its default even with nothing on the volume`() = runTest {
        val response = client.get("/image/users/$DEFAULT_IMAGE")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/webp", response.headers["Content-Type"])
        val (width, height) = dimensionsOf(response.bodyAsBytes())
        assertEquals(ImageBucket.USER.width, width)
        assertEquals(ImageBucket.USER.height, height)
    }

    @Test
    fun `a bucket still serves its own files alongside the default`() = runTest {
        val bytes = png(40, 40)
        Path("${ImageBucket.COOKBOOK.path}/7-v2.webp").createParentDirectories().writeBytes(bytes)

        // The default sits on a constant path inside the same tree; it must not shadow it
        val response = client.get("/image/cookbooks/7-v2.webp")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(bytes, response.bodyAsBytes())
    }

    @Test
    fun `a picture that is not on the volume falls back to the uploaded default`() = runTestAsAdmin {
        client.uploadDefaultImageRaw(DefaultImage.COOKBOOK, png(600, 600))

        // Nothing was ever written for cookbook 7, so the static tree answers with the default
        val response = client.get("/image/cookbooks/7-v2.webp")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(uploadedFile(ImageBucket.COOKBOOK).toFile().readBytes(), response.bodyAsBytes())
    }

    // -------------------------------------------------------------- replacing

    @Test
    fun `replacing a default writes it to every bucket that shows it`() = runTestAsAdmin {
        client.uploadDefaultImageRaw(DefaultImage.RECIPE, png(3200, 2400))
            .apply { assertEquals(HttpStatusCode.OK, status) }

        // One upload, both the full-size picture and the thumbnail
        assertTrue(uploadedFile(ImageBucket.RECIPE).exists())
        assertTrue(uploadedFile(ImageBucket.RECIPE_THUMBNAIL).exists())
        assertFalse(uploadedFile(ImageBucket.USER).exists(), "only the recipe default was replaced")

        val recipe = client.listDefaultImages().first { it.image == DefaultImage.RECIPE }
        assertTrue(recipe.custom)
        recipe.files.forEach { assertNotNull(it.lastModified) }
    }

    @Test
    fun `a replaced default is what the bucket then serves`() = runTestAsAdmin {
        val before = client.get("/image/users/$DEFAULT_IMAGE").bodyAsBytes()

        client.uploadDefaultImageRaw(DefaultImage.USER, png(800, 800))
            .apply { assertEquals(HttpStatusCode.OK, status) }

        val after = client.get("/image/users/$DEFAULT_IMAGE").bodyAsBytes()
        assertFalse(before.contentEquals(after), "the packaged picture should have been replaced")
        assertContentEquals(uploadedFile(ImageBucket.USER).toFile().readBytes(), after)
        assertEquals(ImageBucket.USER.size, dimensionsOf(after))
    }

    @Test
    fun `a default is never enlarged past what was uploaded`() = runTestAsAdmin {
        // Square source, square bucket: the crop keeps all of it, and 120 < 400
        client.uploadDefaultImageRaw(DefaultImage.USER, png(120, 120))
            .apply { assertEquals(HttpStatusCode.OK, status) }

        assertEquals(120 to 120, dimensionsOf(client.get("/image/users/$DEFAULT_IMAGE").bodyAsBytes()))
    }

    // ---------------------------------------------------------------- reset

    @Test
    fun `resetting a default puts the packaged picture back`() = runTestAsAdmin {
        val packaged = client.get("/image/cookbooks/$DEFAULT_IMAGE").bodyAsBytes()
        client.uploadDefaultImageRaw(DefaultImage.COOKBOOK, png(700, 700))

        client.resetDefaultImageRaw(DefaultImage.COOKBOOK)
            .apply { assertEquals(HttpStatusCode.OK, status) }

        assertFalse(uploadedFile(ImageBucket.COOKBOOK).exists())
        assertFalse(client.listDefaultImages().first { it.image == DefaultImage.COOKBOOK }.custom)
        assertContentEquals(packaged, client.get("/image/cookbooks/$DEFAULT_IMAGE").bodyAsBytes())
    }

    @Test
    fun `resetting a default nobody replaced reports nothing to do`() = runTestAsAdmin {
        client.resetDefaultImageRaw(DefaultImage.USER)
            .apply { assertEquals(HttpStatusCode.NotFound, status) }
    }

    @Test
    fun `a default nothing stands for is refused`() = runTestAsAdmin {
        assertEquals(HttpStatusCode.BadRequest, client.delete("$ADMIN_URL/storage/defaults/nonsense").status)
    }
}
