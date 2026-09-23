package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.network.dto.RecipeInfo
import com.xavierclavel.cooknco.network.dto.RecipeOwner
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineStoreTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "cooknco-offline-${Random.nextLong()}"
    private val store = OfflineStore(root)

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)

    private fun recipe(id: Long) = RecipeInfo(
        id = id,
        version = 1,
        title = "Tarte",
        dishClass = "DESSERT",
        owner = RecipeOwner(id = 1, version = 0, username = "xavier"),
        creationDate = 0,
        likesCount = 0,
    )

    @Test
    fun a_recipe_written_comes_back() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = recipe(1), notes = "half the sugar", isLiked = true))

        val held = store.readRecipe(1)

        assertEquals("Tarte", held?.recipe?.title)
        assertEquals("half the sugar", held?.notes, "the cook's own writing, kept with the recipe")
        assertEquals(true, held?.isLiked)
    }

    @Test
    fun a_recipe_never_written_is_absent_rather_than_an_error() = runTest {
        assertNull(store.readRecipe(404))
    }

    /**
     * The [CookTimerStore] rule, and the reason this store can be upgraded without a migration:
     * a file from a build whose DTOs had a different shape reads as a recipe we do not hold,
     * which is a thing every screen already knows how to say. Throwing would crash the screen
     * that was meant to be the offline one.
     */
    @Test
    fun a_file_that_will_not_parse_reads_as_absent() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = recipe(1)))
        FileSystem.SYSTEM.write(root / "recipes" / "1.json") { writeUtf8("{ truncated") }

        assertNull(store.readRecipe(1))
    }

    @Test
    fun a_write_replaces_what_was_there() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = recipe(1), notes = "first"))
        store.writeRecipe(OfflineRecipe(recipe = recipe(1), notes = "second"))

        assertEquals("second", store.readRecipe(1)?.notes)
    }

    /** Nothing is left behind by an atomic write — a stray `.tmp` would be read as a recipe. */
    @Test
    fun a_write_leaves_no_temporary_file() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = recipe(1)))

        val names = FileSystem.SYSTEM.list(root / "recipes").map { it.name }
        assertEquals(listOf("1.json"), names)
    }

    @Test
    fun clearing_forgets_everything() = runTest {
        store.writeRecipe(OfflineRecipe(recipe = recipe(1)))
        store.writeIndex(OfflineIndex(userId = 1, syncedAt = 5))
        store.writeImage("recipes_1-v1.webp", byteArrayOf(1, 2, 3))

        store.clear()

        assertNull(store.readRecipe(1))
        assertNull(store.readIndex())
        assertTrue(store.imageNames().isEmpty())
    }

    @Test
    fun images_are_listed_and_deleted_by_name() = runTest {
        store.writeImage("recipes_1-v1.webp", byteArrayOf(1))
        store.writeImage("recipes-thumbnails_1-v1.webp", byteArrayOf(2))

        assertEquals(
            setOf("recipes_1-v1.webp", "recipes-thumbnails_1-v1.webp"),
            store.imageNames(),
            "a picture and its thumbnail share a last path segment and must not share a name",
        )

        store.deleteImage("recipes_1-v1.webp")
        assertEquals(setOf("recipes-thumbnails_1-v1.webp"), store.imageNames())
    }
}
