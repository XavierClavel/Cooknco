package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.callToolOk
import main.com.xavierclavel.utils.mcpToken
import main.com.xavierclavel.utils.uploadRecipeImage
import main.com.xavierclavel.utils.uploadToTicketUrl
import org.junit.jupiter.api.Test
import shared.dto.RecipeDTO
import shared.utils.URL.IMAGE_URL
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPES_THUMBNAIL_PATH
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.test.assertContains
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

    // The upload endpoint a ticket unlocks. What a ticket is worth, and the round trip through
    // the MCP tool that hands one out, is covered in McpControllerTest.

    @Test
    fun `an upload URL naming a ticket that was never issued is refused`() = runTest {
        client.uploadToTicketUrl("http://localhost/$IMAGE_URL/upload/not-a-ticket").apply {
            assertEquals(HttpStatusCode.Unauthorized, status)
            assertContains(bodyAsText(), "invalid_upload_ticket")
        }
    }

    /**
     * The bound is enforced here rather than left to `client_max_body_size`, because this is the
     * one image endpoint reachable with no account behind it. The body is not a picture: the size
     * is checked as the bytes are read, before anything tries to decode them.
     */
    @Test
    fun `an upload past the size cap is refused`() = runTestAsUser {
        val token = client.tokenFor(USER1)
        val recipe = client.createRecipe(RecipeDTO(title = "Ratatouille"))
        val uploadUrl = client.callToolOk(
            token,
            "prepare_recipe_image_upload",
            buildJsonObject { put("recipe_id", recipe.id) },
        )["uploadUrl"]!!.jsonPrimitive.content

        val oversized = ByteArray(configuration.images.maxUploadBytes.toInt() + 1)
        client.uploadToTicketUrl(uploadUrl, oversized).apply {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertContains(bodyAsText(), "image_too_large")
        }
        assertEquals(0, client.getRecipe(recipe.id).version)
    }

    private suspend fun HttpClient.tokenFor(username: String): String = mcpToken(username, password)

    private fun assertImageExists(path: String, id: Long, version: Long) {
        val file = Path("$path/$id-v$version.webp")
        assertTrue(file.exists(), "expected an image at $file")
        assertTrue(file.fileSize() > 0, "expected $file not to be empty")
    }
}
