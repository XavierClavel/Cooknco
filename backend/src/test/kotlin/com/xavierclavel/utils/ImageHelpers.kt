package main.com.xavierclavel.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import shared.utils.URL.IMAGE_URL
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.Path
import kotlin.math.abs
import kotlin.test.assertTrue

/** A real jpeg, as the browser would send it. */
fun testImageBytes(width: Int = 40, height: Int = 30): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, (x * 255 / width) shl 8)
    return ByteArrayOutputStream().use {
        ImageIO.write(image, "jpg", it)
        it.toByteArray()
    }
}

/**
 * A picture split into four flat quarters, so that a stored picture says which part of it was
 * kept: top left [RED], top right [GREEN], bottom left [BLUE], bottom right [WHITE].
 *
 * [storedForExif6] writes the pixels a camera held upright leaves on disk under EXIF
 * orientation 6 — this picture turned a quarter anticlockwise, [height] wide and [width] tall —
 * so that once [withExifOrientation] has tagged it, what is *shown* is the same four quarters.
 */
fun quartersImageBytes(width: Int, height: Int, storedForExif6: Boolean = false): ByteArray {
    fun shown(x: Int, y: Int): Color = when {
        y < height / 2 -> if (x < width / 2) RED else GREEN
        else -> if (x < width / 2) BLUE else WHITE
    }
    if (!storedForExif6) return jpegOf(width, height, ::shown)
    // Orientation 6 is "turn a quarter clockwise to show": stored (x, y) is shown at
    // (storedHeight - 1 - y, x).
    return jpegOf(height, width) { x, y -> shown(width - 1 - y, x) }
}

/** A jpeg painted pixel by pixel. */
fun jpegOf(width: Int, height: Int, colorAt: (x: Int, y: Int) -> Color): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, colorAt(x, y).rgb)
    return ByteArrayOutputStream().use {
        ImageIO.write(image, "jpg", it)
        it.toByteArray()
    }
}

val RED: Color = Color(220, 30, 30)
val GREEN: Color = Color(30, 180, 60)
val BLUE: Color = Color(30, 60, 220)
val WHITE: Color = Color(250, 250, 250)

/**
 * The same jpeg carrying an EXIF orientation tag, as a phone writes one rather than turning
 * the pixels.
 *
 * Spliced in by hand, after the JFIF segment ImageIO writes first: the JDK has no EXIF writer,
 * and the tag is the whole of what this needs — a big-endian TIFF header and one IFD holding
 * the one SHORT.
 */
fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
    val tiff = byteArrayOf(
        0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // "MM", 42, first IFD at offset 8
        0x00, 0x01, // one entry
        0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, // Orientation, SHORT, one value
        0x00, orientation.toByte(), 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, // no next IFD
    )
    val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
    val length = payload.size + 2
    val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    // SOI, then the APP0 segment, whose length is the two bytes after its marker
    check(jpeg[2] == 0xFF.toByte() && jpeg[3] == 0xE0.toByte()) { "expected a JFIF segment first" }
    val afterApp0 = 4 + ((jpeg[4].toInt() and 0xFF) shl 8 or (jpeg[5].toInt() and 0xFF))
    return jpeg.copyOfRange(0, afterApp0) + app1 + jpeg.copyOfRange(afterApp0, jpeg.size)
}

/** What a bucket holds for one entity, decoded. */
fun storedImage(path: String, id: Long, version: Long): BufferedImage =
    ImageIO.read(Path("$path/$id-v$version.webp").toFile())

/**
 * That [image] is [expected] throughout — sampled at its middle and near each corner, inset so
 * that the smoothing a resize does across an edge is not what gets measured.
 */
fun assertFilledWith(expected: Color, image: BufferedImage) {
    val points = listOf(0.1 to 0.1, 0.9 to 0.1, 0.5 to 0.5, 0.1 to 0.9, 0.9 to 0.9)
    points.forEach { (fx, fy) ->
        val actual = Color(image.getRGB((fx * image.width).toInt(), (fy * image.height).toInt()))
        val close = abs(actual.red - expected.red) < 40 &&
            abs(actual.green - expected.green) < 40 &&
            abs(actual.blue - expected.blue) < 40
        assertTrue(close, "expected $expected at ($fx, $fy) of the stored picture, found $actual")
    }
}

/** [crop] is sent as the client sends it, `left,top,width,height`; null sends none. */
suspend fun HttpClient.uploadImage(
    path: String,
    id: Long,
    bytes: ByteArray = testImageBytes(),
    crop: String? = null,
): HttpResponse =
    this.post("$IMAGE_URL/$path/$id") {
        crop?.let { parameter("crop", it) }
        setBody(MultiPartFormDataContent(formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=pic.jpg")
            })
        }))
    }

suspend fun HttpClient.uploadRecipeImage(
    recipeId: Long,
    bytes: ByteArray = testImageBytes(),
    crop: String? = null,
): HttpResponse = uploadImage("recipes", recipeId, bytes, crop)

suspend fun HttpClient.uploadCookbookImage(cookbookId: Long, bytes: ByteArray = testImageBytes()): HttpResponse =
    uploadImage("cookbooks", cookbookId, bytes)

/** A picture for one step, addressed by the step's own row rather than by the recipe's. */
suspend fun HttpClient.uploadStepImage(
    stepId: Long,
    bytes: ByteArray = testImageBytes(),
    crop: String? = null,
): HttpResponse = uploadImage("recipe-steps", stepId, bytes, crop)

suspend fun HttpClient.deleteStepImage(stepId: Long): HttpResponse =
    this.delete("$IMAGE_URL/recipe-steps/$stepId")

suspend fun HttpClient.deleteCookbookImage(cookbookId: Long): HttpResponse =
    this.delete("$IMAGE_URL/cookbooks/$cookbookId")

/**
 * Posts a picture to an upload URL a ticket was minted for, the way a client holding one does.
 *
 * Takes the absolute URL the tool returned and keeps only its path: the test server answers on
 * its own host, while the tool builds the URL against the configured `frontend.url`.
 */
suspend fun HttpClient.uploadToTicketUrl(uploadUrl: String, bytes: ByteArray = testImageBytes()): HttpResponse =
    this.post(Url(uploadUrl).encodedPath) {
        setBody(MultiPartFormDataContent(formData {
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "filename=pic.jpg")
            })
        }))
    }
