package main.com.xavierclavel.controllertests

import com.xavierclavel.ApplicationTest
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import main.com.xavierclavel.utils.createCookbook
import main.com.xavierclavel.utils.createRecipe
import main.com.xavierclavel.utils.getRecipe
import main.com.xavierclavel.utils.callToolOk
import main.com.xavierclavel.utils.sessionToken
import main.com.xavierclavel.utils.deleteCookbookImage
import main.com.xavierclavel.utils.deleteStepImage
import main.com.xavierclavel.utils.uploadCookbookImage
import main.com.xavierclavel.utils.uploadRecipeImage
import main.com.xavierclavel.utils.uploadStepImage
import main.com.xavierclavel.utils.uploadToTicketUrl
import main.com.xavierclavel.utils.BLUE
import main.com.xavierclavel.utils.GREEN
import main.com.xavierclavel.utils.RED
import main.com.xavierclavel.utils.WHITE
import main.com.xavierclavel.utils.assertFilledWith
import main.com.xavierclavel.utils.jpegOf
import main.com.xavierclavel.utils.quartersImageBytes
import main.com.xavierclavel.utils.storedImage
import main.com.xavierclavel.utils.withExifOrientation
import org.junit.jupiter.api.Test
import shared.dto.RecipeDTO
import shared.utils.URL.IMAGE_URL
import shared.utils.Filepath.RECIPES_IMG_PATH
import shared.utils.Filepath.RECIPE_STEPS_IMG_PATH
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

    // ── The part of a picture its uploader kept ─────────────────────────────────
    //
    // Both clients put the picture under a frame the cook moves and zooms, and post what the
    // frame covered as fractions of the picture. The backend is what cuts it out, so neither
    // client has to re-encode a photograph to send it.

    @Test
    fun `a crop keeps the part of the picture it names`() = runTestAsAdmin {
        val recipe = client.createRecipe()

        // The top right quarter of an 800x600 picture: 400x300, the recipe frame's own 4:3.
        client.uploadRecipeImage(recipe.id, quartersImageBytes(800, 600), crop = "0.5,0,0.5,0.5").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        assertFilledWith(GREEN, storedImage(RECIPES_IMG_PATH, recipe.id, 1))
        // The thumbnail is cut from the same upload, so it is the same quarter.
        assertFilledWith(GREEN, storedImage(RECIPES_THUMBNAIL_PATH, recipe.id, 1))
    }

    /**
     * The contract both clients rely on. They frame the picture as it is shown — a browser
     * turns an `<img>` by its EXIF tag, and so does the app's decoder — so a crop read off the
     * stored pixels instead lands on another part of every photograph a phone took upright.
     */
    @Test
    fun `a crop is measured on the picture as it is shown, after its rotation`() = runTestAsAdmin {
        val recipe = client.createRecipe()
        val sideways = withExifOrientation(quartersImageBytes(800, 600, storedForExif6 = true), 6)

        // Measured on the stored pixels, the same fractions are the white quarter.
        client.uploadRecipeImage(recipe.id, sideways, crop = "0.5,0,0.5,0.5").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        assertFilledWith(GREEN, storedImage(RECIPES_IMG_PATH, recipe.id, 1))
    }

    /** What every upload got before there was a crop, and what a build that sends none still gets. */
    @Test
    fun `without a crop the picture is centred in its frame`() = runTestAsAdmin {
        val recipe = client.createRecipe()
        // Twice as wide as tall: its 4:3 middle is green, and the sixth either side red.
        val wide = jpegOf(1200, 600) { x, _ -> if (x in 200 until 1000) GREEN else RED }

        assertEquals(HttpStatusCode.OK, client.uploadRecipeImage(recipe.id, wide).status)

        assertFilledWith(GREEN, storedImage(RECIPES_IMG_PATH, recipe.id, 1))
    }

    /** A frame pushed against an edge comes back a hair past it, once it has been a float and a string. */
    @Test
    fun `a crop rounded a hair past the edge is still the crop against the edge`() = runTestAsAdmin {
        val recipe = client.createRecipe()

        client.uploadRecipeImage(recipe.id, quartersImageBytes(800, 600), crop = "0.5,0.5,0.5000004,0.5000004").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        assertFilledWith(WHITE, storedImage(RECIPES_IMG_PATH, recipe.id, 1))
    }

    /**
     * Refused rather than clamped into the picture: a client sending one is measuring against
     * some other picture, and what a clamp kept would not be what the cook saw. Read before the
     * body, so nothing is saved and the version does not move.
     */
    @Test
    fun `a crop that does not fit in the picture is refused`() = runTestAsAdmin {
        val recipe = client.createRecipe()

        listOf("0.5,0,0.6,1", "0,0.5,1,0.6", "-0.1,0,0.5,0.5", "0,0,0,1", "0,0,1", "a,b,c,d", "0,0,NaN,1")
            .forEach { crop ->
                client.uploadRecipeImage(recipe.id, crop = crop).apply {
                    assertEquals(HttpStatusCode.BadRequest, status, "crop $crop")
                    assertContains(bodyAsText(), "invalid_image_crop")
                }
            }

        assertEquals(0, client.getRecipe(recipe.id).version)
    }

    @Test
    fun `a step's picture keeps its crop too`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(title = "Crepes", steps = mutableListOf(
            RecipeDTO.RecipeStepDTO(text = "Melt the butter"),
        )))
        val stepId = recipe.steps.single().id!!

        // The bottom left quarter, in the step frame's 4:3.
        client.uploadStepImage(stepId, quartersImageBytes(800, 600), crop = "0,0.5,0.5,0.5").apply {
            assertEquals(HttpStatusCode.OK, status)
        }

        assertFilledWith(BLUE, storedImage(RECIPE_STEPS_IMG_PATH, stepId, 1))
    }

    // ── A step's own picture ────────────────────────────────────────────────────
    //
    // Addressed by the step's row, not by the recipe's - which is what the steps having ids
    // of their own bought. Rights still come from the recipe: a step is not something anybody
    // owns on its own.

    @Test
    fun `upload a picture on a step that has none`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(title = "Crepes", steps = mutableListOf(
            RecipeDTO.RecipeStepDTO(text = "Melt the butter"),
        )))
        val step = recipe.steps.single()
        assertEquals(0, step.imageVersion)

        val response = client.uploadStepImage(step.id!!)
        assertEquals(HttpStatusCode.OK, response.status)

        assertEquals(1, client.getRecipe(recipe.id).steps.single().imageVersion)
        assertImageExists(RECIPE_STEPS_IMG_PATH, step.id!!, 1)
        // No thumbnail: a step picture is drawn at one size, so a second file would be one
        // more thing to keep in step with the first for nobody to look at.
        assertTrue(!Path("$RECIPE_STEPS_IMG_PATH/${step.id}-v1-thumbnail.webp").exists())
    }

    @Test
    fun `a second picture replaces the first`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(title = "Crepes", steps = mutableListOf(
            RecipeDTO.RecipeStepDTO(text = "Melt the butter"),
        )))
        val stepId = recipe.steps.single().id!!
        client.uploadStepImage(stepId)

        assertEquals(HttpStatusCode.OK, client.uploadStepImage(stepId).status)

        assertEquals(2, client.getRecipe(recipe.id).steps.single().imageVersion)
        assertImageExists(RECIPE_STEPS_IMG_PATH, stepId, 2)
        assertTrue(!Path("$RECIPE_STEPS_IMG_PATH/$stepId-v1.webp").exists())
    }

    /**
     * Back to zero, where the other buckets bump. They can afford to bump: a recipe whose
     * picture is gone falls back to the default this controller serves. A step has no default
     * - it shows nothing rather than a stand-in - so a version left pointing at a deleted file
     * would be a broken image for every reader of the recipe.
     */
    @Test
    fun `deleting a step's picture takes its version back to zero`() = runTestAsAdmin {
        val recipe = client.createRecipe(RecipeDTO(title = "Crepes", steps = mutableListOf(
            RecipeDTO.RecipeStepDTO(text = "Melt the butter"),
        )))
        val stepId = recipe.steps.single().id!!
        client.uploadStepImage(stepId)

        assertEquals(HttpStatusCode.OK, client.deleteStepImage(stepId).status)

        assertEquals(0, client.getRecipe(recipe.id).steps.single().imageVersion)
        assertTrue(!Path("$RECIPE_STEPS_IMG_PATH/$stepId-v1.webp").exists())
    }

    /** The recipe's author, or nobody: the step itself has no owner to ask. */
    @Test
    fun `a step's picture may only be set by the recipe's author`() = runTest {
        var stepId = 0L
        runAsUser1 {
            val recipe = client.createRecipe(RecipeDTO(title = "Crepes", steps = mutableListOf(
                RecipeDTO.RecipeStepDTO(text = "Melt the butter"),
            )))
            stepId = recipe.steps.single().id!!
        }
        runAsUser2 {
            assertEquals(HttpStatusCode.Forbidden, client.uploadStepImage(stepId).status)
            assertEquals(HttpStatusCode.Forbidden, client.deleteStepImage(stepId).status)
        }
    }

    /**
     * A cookbook's picture is one of its administrators' to set, exactly as its title is.
     * `uploadCookbookImage` checked nothing at all — and read the body before it would have,
     * so a refused caller uploaded the whole file first. The check now runs before the body,
     * which is also what lets this assert without a real picture.
     */
    @Test
    fun `a cookbook's picture may only be set by one of its administrators`() = runTest {
        var cookbookId = 0L
        runAsUser1 { cookbookId = client.createCookbook().id }
        runAsUser2 {
            assertEquals(HttpStatusCode.Forbidden, client.uploadCookbookImage(cookbookId).status)
            assertEquals(HttpStatusCode.Forbidden, client.deleteCookbookImage(cookbookId).status)
        }
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

    private suspend fun HttpClient.tokenFor(username: String): String = sessionToken(username, password)

    private fun assertImageExists(path: String, id: Long, version: Long) {
        val file = Path("$path/$id-v$version.webp")
        assertTrue(file.exists(), "expected an image at $file")
        assertTrue(file.fileSize() > 0, "expected $file not to be empty")
    }
}
