package com.xavierclavel.services

import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.utils.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** One photographed page, as the client posted it. */
class RecipePhoto(val bytes: ByteArray, val mediaType: String)

/**
 * What a model answered, before anything has been made of it.
 *
 * [json] is the model's own text, unparsed: deciding what it means is [PhotoImportService]'s
 * job, so that every provider is held to one reading of it rather than each to its own.
 */
class RecipePhotoReading(
    val json: String,
    /** Tokens billed, as the provider reported them, or null when it did not. For the log. */
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    /** Which model answered, for the usage history. */
    val model: String = "",
)

/**
 * Something that can look at photographs of a recipe and write down what is on them.
 *
 * An interface, and the only thing in the photo import that knows a model is involved,
 * because which model is a decision about price and hosting rather than about the feature:
 * swapping providers must be a configuration change and nothing else. Everything a provider
 * gets wrong — the shape of the answer, units it invented, an ingredient a step names but the
 * list does not — is dealt with downstream, in one place, whoever answered.
 *
 * The prompt lives here rather than with each implementation for the same reason: it is the
 * contract [PhotoImportService] parses against, and a second copy would drift from it.
 */
interface RecipePhotoReader {
    /**
     * @throws ServiceUnavailableException when no reading could be had — the provider could
     *   not be reached, refused, or answered with nothing
     */
    suspend fun read(photos: List<RecipePhoto>): RecipePhotoReading

    companion object {
        /**
         * What the model is asked to do, and the JSON it must answer with.
         *
         * **An ingredient's quantity is stated once.** The top-level list carries every
         * amount and a step only *names* what it uses. Letting a step restate a quantity
         * would have the import add the list's 200 g to the step's 200 g, and asking the model
         * to split one amount across steps is asking it to invent arithmetic the page never
         * did — so a step's link says "uses flour" and the amount stays on the row, which is
         * the same thing a Cooklang import does when the two cannot be reconciled.
         *
         * **Transcribe, never translate.** The ingredient catalogue is searched in the
         * language the client asked for, but what the cook photographed is what they want to
         * read, and a translated method is a different recipe with the same title.
         */
        val PROMPT = """
            You transcribe photographs of recipes — printed cookbook pages, magazine
            cut-outs, handwritten cards — into JSON. The photographs are pages of one recipe,
            in order.

            Answer with a single JSON object and nothing else, of exactly this shape:
            {
              "title": string or null,
              "description": string or null,
              "servings": integer or null,
              "prepMinutes": integer or null,
              "cookMinutes": integer or null,
              "ovenTemperatureCelsius": integer or null,
              "course": one of "ENTREE", "MAIN_DISH", "DESERT", "SALTY_SNACK", "SUGARY_SNACK", "DRINK", "OTHER", or null,
              "tips": string or null,
              "ingredients": [
                { "name": string, "amount": number or null, "unit": string or null, "note": string or null }
              ],
              "steps": [
                { "text": string, "minutes": integer or null, "uses": [string] }
              ]
            }

            Rules:
            - Transcribe what is on the page. Do not invent, complete or improve anything.
              A field the page does not state is null; a list it does not have is empty.
            - Keep the language of the page. Never translate.
            - "ingredients" lists every ingredient once, with the full quantity the recipe
              needs. If the page has no ingredient list, take them from the method.
              "name" is the ingredient alone ("flour", "farine"), without quantity or
              preparation; "note" holds the rest ("sifted", "en dés", "at room temperature").
            - "unit" is a short unit as written on the page: g, kg, ml, cl, l, tsp, tbsp,
              cup, oz, lb, or the page's own word (clove, pinch, gousse, pincée). Null for a
              plain count ("3 eggs").
            - "amount" is a number: 0.5 for "1/2", 1.5 for "1 ½". Null when none is given.
            - "steps" is the method, one entry per step or paragraph as printed, worded as
              printed. "minutes" is how long that step says it takes, or null.
              "uses" names the ingredients that step uses, spelled exactly as in
              "ingredients", without quantities.
            - "ovenTemperatureCelsius" is in °C: convert a temperature given in °F or as a
              gas mark.
            - Leave out page numbers, headers, photo captions and anything that is not
              part of this recipe.
        """.trimIndent()
    }
}

/**
 * Reads through any provider that speaks the OpenAI chat-completions API.
 *
 * That API is the one every candidate shares — Scaleway, OVHcloud, Mistral, DeepSeek,
 * OpenRouter, OpenAI itself, and Gemini and Claude through their compatibility endpoints —
 * so one implementation is a choice of every one of them, made in configuration
 * (`Configuration.Ai`). A provider that needs a client of its own is a second
 * implementation of [RecipePhotoReader], not a branch in this one.
 *
 * Pictures travel as `data:` URLs inside the request rather than as links: the photographs
 * are not stored anywhere a provider could fetch them from, and should not be.
 */
class OpenAiCompatiblePhotoReader(
    /** Up to and including the version segment: `https://api.scaleway.ai/v1`. */
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    /**
     * A ceiling on the answer, which is the part of a reading that costs the most. A long
     * recipe is a couple of thousand tokens; this leaves room for one and stops a model that
     * has started repeating itself from billing the rest of its context.
     */
    private val maxOutputTokens: Int = 4096,
    /**
     * Whether to ask for `response_format: json_object`. Every provider named above honours
     * it, and it is what keeps a chatty model from wrapping the object in prose — but a
     * provider that rejects the field outright needs a way to leave it out.
     */
    private val jsonMode: Boolean = true,
    timeoutSeconds: Long = 90,
    private val client: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            // A vision model takes several seconds a page and more under load. The cook is
            // watching a spinner, so this is generous, but not so generous that a provider
            // that has stopped answering holds the request for ever.
            requestTimeoutMillis = timeoutSeconds * 1_000
            connectTimeoutMillis = 10_000
        }
    },
) : RecipePhotoReader {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(photos: List<RecipePhoto>): RecipePhotoReading {
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val response = try {
            client.post(url) {
                bearerAuth(apiKey)
                setBody(TextContent(requestOf(photos).toString(), ContentType.Application.Json))
            }
        } catch (e: Exception) {
            logger.error(e) { "Could not reach the recipe photo reader at $url" }
            throw ServiceUnavailableException(ServiceUnavailableCause.RECIPE_READER_UNAVAILABLE)
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // The body names the reason — an unknown model, a bad key, a quota — and never
            // carries the key itself, so it is safe to log and is the only clue there is.
            logger.error { "Recipe photo reader refused the request: ${response.status} ${body.take(500)}" }
            throw ServiceUnavailableException(
                if (response.status == HttpStatusCode.TooManyRequests) ServiceUnavailableCause.RECIPE_READER_BUSY
                else ServiceUnavailableCause.RECIPE_READER_FAILED
            )
        }

        val answer = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val content = answer?.get("choices")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        if (content.isNullOrBlank()) {
            logger.error { "Recipe photo reader answered with no content: ${body.take(500)}" }
            throw ServiceUnavailableException(ServiceUnavailableCause.RECIPE_READER_FAILED)
        }

        val usage = answer["usage"] as? JsonObject
        return RecipePhotoReading(
            json = content,
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull,
            model = model,
        )
    }

    private fun requestOf(photos: List<RecipePhoto>): JsonObject = buildJsonObject {
        put("model", model)
        // Transcription, not composition: the same page should read the same way twice.
        put("temperature", 0)
        put("max_tokens", maxOutputTokens)
        if (jsonMode) putJsonObject("response_format") { put("type", "json_object") }
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", RecipePhotoReader.PROMPT)
            }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", if (photos.size == 1) "The recipe:" else "The recipe, in ${photos.size} pages:")
                    }
                    photos.forEach { photo ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:${photo.mediaType};base64,${Base64.getEncoder().encodeToString(photo.bytes)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The reader of an install with no provider configured.
 *
 * Bound rather than left out so that the route stays registered and answers a reason — a 503
 * the app can turn into a sentence — instead of the feature disappearing into a 404 that reads
 * like a bug in the client.
 */
class UnconfiguredPhotoReader : RecipePhotoReader {
    override suspend fun read(photos: List<RecipePhoto>): RecipePhotoReading =
        throw ServiceUnavailableException(ServiceUnavailableCause.RECIPE_READER_NOT_CONFIGURED)
}

