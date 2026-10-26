package com.xavierclavel.cooknco.network

import com.xavierclavel.cooknco.data.ImageCrop
import com.xavierclavel.cooknco.platform.PickedImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The app's half of the crop: every picture upload posts the file as it was picked, and the
 * frame beside it in the query, where the backend reads it.
 */
class ImageUploadTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val client = HttpClient(MockEngine { request ->
        requests += request
        respond("", HttpStatusCode.OK)
    })

    /** Bytes no encoder would produce, so finding them in the body means nothing re-encoded them. */
    private val bytes = byteArrayOf(0x13, 0x37, 0x42, 0x07, 0x7F, 0x00, 0x55)
    private val crop = ImageCrop(left = 0.25, top = 0.0, width = 0.5, height = 0.375)

    @Test
    fun every_upload_sends_its_crop_beside_the_untouched_picture() = runTest {
        val framed = PickedImage(bytes, "image/jpeg", crop)
        RecipeApi(client).uploadRecipeImage("token", 12, framed)
        RecipeApi(client).uploadStepImage("token", 34, framed)
        UserApi(client).uploadProfileImage("token", 56, framed)
        CookbookApi(client).uploadCookbookImage("token", 78, framed)

        assertEquals(
            listOf("/image/recipes/12", "/image/recipe-steps/34", "/image/users/56", "/image/cookbooks/78"),
            requests.map { it.url.encodedPath },
        )
        requests.forEach { request ->
            assertEquals("0.250000,0.000000,0.500000,0.375000", request.url.parameters["crop"], request.url.encodedPath)
            val body = request.body.toByteArray()
            assertTrue(body.containsSlice(bytes), "${request.url.encodedPath} should carry the picture as picked")
        }
    }

    /** What the backend reads as "centre it", which is what every upload was before. */
    @Test
    fun a_picture_nobody_framed_is_sent_with_no_crop() = runTest {
        UserApi(client).uploadProfileImage("token", 56, PickedImage(bytes, "image/png"))

        assertNull(requests.single().url.parameters["crop"])
    }

    private fun ByteArray.containsSlice(slice: ByteArray): Boolean =
        (0..size - slice.size).any { start -> slice.indices.all { this[start + it] == slice[it] } }
}
