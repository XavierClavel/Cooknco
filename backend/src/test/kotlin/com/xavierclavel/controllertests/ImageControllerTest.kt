package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.http.HttpStatusCode
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.uploadRecipeImage
import org.junit.jupiter.api.Test
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageControllerTest : ApplicationTest() {

    @Test
    fun `upload image on a recipe that has none`() = runTestAsAdmin {
        val recipe = client.createRecipe()
        assertEquals(0, recipe.version)

        val response = client.uploadRecipeImage(recipe.id)
        assertEquals(HttpStatusCode.OK, response.status)

        assertEquals(1, client.getRecipe(recipe.id).version)
        assertImageExists(RECIPES_IMG_PATH, recipe.id, 1)
        assertImageExists(RECIPES_THUMBNAIL_PATH, recipe.id, 1)
    }

    @Test
    fun `upload image on a recipe that already has one`() = runTestAsAdmin {
        val recipe = client.createRecipe()
        client.uploadRecipeImage(recipe.id)

        val response = client.uploadRecipeImage(recipe.id)
        assertEquals(HttpStatusCode.OK, response.status)

        assertEquals(2, client.getRecipe(recipe.id).version)
        assertImageExists(RECIPES_IMG_PATH, recipe.id, 2)
        // the replaced version is cleaned up
        assertTrue(!Path("$RECIPES_IMG_PATH/${recipe.id}-v1.webp").exists())
    }

    private fun assertImageExists(path: String, id: Long, version: Long) {
        val file = Path("$path/$id-v$version.webp")
        assertTrue(file.exists(), "expected an image at $file")
        assertTrue(file.fileSize() > 0, "expected $file not to be empty")
    }
}
