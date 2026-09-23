package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a screen is handed for a picture, and why the naming matters.
 *
 * Coil resolves an `okio.Path` through a mapper it registers in its common components, so a
 * local file needs no fetcher of its own — which is what lets a pinned picture and a remote
 * one go through the same `AsyncImage` call.
 */
class OfflineImagesTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-offline-${Random.nextLong()}"
    private val store = OfflineStore(root)
    private val images = OfflineImages(store, HttpClient(MockEngine { respond(byteArrayOf(1, 2, 3)) }))

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)

    @Test
    fun a_picture_that_is_not_here_stays_a_url() = runTest {
        images.refresh()

        assertEquals(
            ImageUrls.recipe(1, 1),
            images.resolve(ImageUrls.recipe(1, 1)),
            "nothing pinned, so nothing to hand Coil but the address",
        )
    }

    @Test
    fun a_downloaded_picture_becomes_a_local_file() = runTest {
        images.download(ImageUrls.recipe(1, 1))
        images.refresh()

        val resolved = images.resolve(ImageUrls.recipe(1, 1))
        assertTrue(resolved is Path, "Coil reads an okio.Path with no fetcher of our own")
    }

    /**
     * `recipes/1-v1.webp` and `recipes-thumbnails/1-v1.webp` share a last path segment and are
     * different pictures. Naming a file after the segment alone would have the hero image and
     * its thumbnail overwrite each other.
     */
    @Test
    fun a_picture_and_its_thumbnail_do_not_collide() = runTest {
        images.download(ImageUrls.recipe(1, 1))
        images.download(ImageUrls.recipeThumbnail(1, 1))

        assertEquals(2, store.imageNames().size)
    }

    @Test
    fun a_url_that_is_not_ours_is_left_alone() = runTest {
        val foreign = "https://example.com/whatever.webp"
        assertEquals(foreign, images.resolve(foreign))
    }

    @Test
    fun pictures_nothing_points_at_any_more_are_deleted() = runTest {
        images.download(ImageUrls.recipe(1, 1))
        images.download(ImageUrls.recipe(2, 1))

        images.retainOnly(setOf(ImageUrls.recipe(1, 1)))

        assertEquals(setOf("recipes_1-v1.webp"), store.imageNames())
    }

    /** The version is in the filename, so a new photograph can never be read as the old one. */
    @Test
    fun a_new_version_is_a_new_name() = runTest {
        images.download(ImageUrls.recipe(1, 1))
        images.download(ImageUrls.recipe(1, 2))

        assertEquals(setOf("recipes_1-v1.webp", "recipes_1-v2.webp"), store.imageNames())
        assertTrue(ApiClient.IMAGE_URL.isNotBlank())
    }
}
