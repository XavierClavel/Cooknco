package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.ServiceUnavailableCause
import com.xavierclavel.exceptions.ServiceUnavailableException
import com.xavierclavel.exceptions.TooManyRequestsException
import com.xavierclavel.models.User
import com.xavierclavel.services.CooklangService.Companion.KEY_COOK_TIME
import com.xavierclavel.services.CooklangService.Companion.KEY_COURSE
import com.xavierclavel.services.CooklangService.Companion.KEY_DESCRIPTION
import com.xavierclavel.services.CooklangService.Companion.KEY_PREP_TIME
import com.xavierclavel.services.CooklangService.Companion.KEY_SERVINGS
import com.xavierclavel.services.CooklangService.Companion.KEY_TEMPERATURE
import com.xavierclavel.services.CooklangService.Companion.KEY_TIPS
import com.xavierclavel.services.CooklangService.Companion.KEY_TITLE
import com.xavierclavel.services.CooklangService.Companion.amountOf
import com.xavierclavel.services.CooklangService.Companion.fold
import com.xavierclavel.services.CooklangService.Companion.unitOf
import com.xavierclavel.utils.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.AiFeature
import shared.enums.Locale
import shared.enums.AiUsageOutcome
import shared.infodto.CooklangImportInfo
import kotlin.math.roundToInt

/**
 * Turns photographs of a recipe into what the editor should be filled in with — the premium
 * counterpart of the scanner, which reads on the phone and does not understand what it reads.
 *
 * **It ends where a Cooklang import ends**, and that is the design. The model is asked for a
 * small JSON transcription ([Transcription]), which is mapped onto the very [CooklangService.Parsed]
 * a `.cook` file is read into and then handed to [CooklangService.toRecipe]. So everything the
 * save path refuses — a count with no unit, step claims that do not add up, a catalogue entry
 * that cannot be measured the way the page measured it, a step past 255 characters — is
 * resolved by code that is already written and tested, and the app fills the editor with the
 * same [CooklangImportInfo] it already handles for a file. A second assembler written for the
 * model would be a second set of answers to the same three questions.
 *
 * JSON rather than asking the model to write Cooklang itself: models write JSON far more
 * reliably than a niche markup, and a malformed answer is then refused loudly instead of
 * being half-read into an ingredient called "olive".
 *
 * Saves nothing, for the reason the Cooklang import saves nothing: an import that created a
 * recipe would notify every follower of something its owner has not read yet.
 */
class RecipeScanService : KoinComponent {
    private val reader: RecipePhotoReader by inject()
    private val cooklangService: CooklangService by inject()
    private val usageService: AiUsageService by inject()

    companion object {
        /**
         * What a model's answer is read with: anything it added is ignored, a field it got the
         * wrong type of falls back to its default, and a number written as a string still counts.
         */
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        /** The magic numbers of what a phone produces, so a provider is never sent a non-image. */
        fun mediaTypeOf(bytes: ByteArray): String? = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) -> "image/png"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> null
        }

        /**
         * The object inside a model's answer.
         *
         * `response_format: json_object` is asked for but not every provider enforces it, and
         * the commonest way a model breaks it is a Markdown fence or a sentence around the
         * object. Taking the outermost braces recovers both without guessing at anything else.
         */
        fun objectIn(answer: String): String? {
            val start = answer.indexOf('{')
            val end = answer.lastIndexOf('}')
            return if (start in 0 until end) answer.substring(start, end + 1) else null
        }
    }

    /**
     * What the model is asked to answer with: [RecipePhotoReader.PROMPT] describes the same
     * shape in words. Numbers are read as primitives rather than as `Int`, because "20",
     * "1/2" and 0.5 are all answers a model gives and all mean something.
     */
    @Serializable
    data class Transcription(
        val title: String? = null,
        val description: String? = null,
        val servings: JsonPrimitive? = null,
        val prepMinutes: JsonPrimitive? = null,
        val cookMinutes: JsonPrimitive? = null,
        val ovenTemperatureCelsius: JsonPrimitive? = null,
        val course: String? = null,
        val tips: String? = null,
        val ingredients: List<Ingredient> = emptyList(),
        val steps: List<Step> = emptyList(),
    ) {
        @Serializable
        data class Ingredient(
            val name: String? = null,
            val amount: JsonPrimitive? = null,
            val unit: String? = null,
            val note: String? = null,
        )

        @Serializable
        data class Step(
            val text: String? = null,
            val minutes: JsonPrimitive? = null,
            val uses: List<String> = emptyList(),
        )
    }

    /**
     * Reads [photos] into a recipe, counting the import against [user]'s day.
     *
     * The caller has already checked the subscription and the pages; this owns the cost side
     * — the month's budget, the daily allowance, the bound on readings in flight, and the
     * usage row every billed answer leaves ([AiUsageService.record]) — and the
     * reading itself. The limits are the backoffice's, read on every import, so a change
     * there bites on the next one.
     *
     * @throws ServiceUnavailableException with `ai_budget_exhausted` once the
     *   month's spend has reached the budget
     * @throws TooManyRequestsException past the day's allowance
     * @throws ServiceUnavailableException when no provider is configured — [UnconfiguredPhotoReader]
     *   says so itself, which is what lets a test bind a fake — or none answered; the import
     *   is not counted then, since nothing was read
     * @throws BadRequestException when the photographs held no recipe the model could read
     */
    suspend fun read(user: User, photos: List<RecipePhoto>, locale: Locale): CooklangImportInfo {
        val userId = user.id
        val admission = usageService.admit(userId)

        // An answer that is not the transcription is refunded too: the provider was paid,
        // but the cook got nothing, and that failure is ours to absorb rather than theirs.
        val read = try {
            val reading = usageService.withRequestSlot { reader.read(photos) }
            // Not `logEdit`: the recipe is not saved, and the usage row below is bookkeeping
            // rather than an edit anybody made. This line and that row are the cost trail.
            logger.info {
                "Recipe scan by user $userId: ${photos.size} page(s), " +
                    "${reading.inputTokens ?: "?"} tokens in, ${reading.outputTokens ?: "?"} out"
            }
            // Billed whatever it comes to, so recorded before anything can refuse it — the
            // budget is checked against these rows, failures included.
            val parsed = runCatching { toParsed(parse(reading.json)) }
            usageService.record(
                feature = AiFeature.RECIPE_SCAN,
                user = user,
                pages = photos.size,
                reading = reading,
                outcome = when {
                    parsed.isFailure -> AiUsageOutcome.FAILED
                    parsed.getOrThrow().isEmpty -> AiUsageOutcome.NOTHING_READ
                    else -> AiUsageOutcome.READ
                },
            )
            parsed.getOrThrow()
        } catch (e: ServiceUnavailableException) {
            usageService.refund(admission)
            throw e
        }
        if (read.isEmpty) throw BadRequestException(BadRequestCause.RECIPE_SCAN_NOTHING_READ)
        return cooklangService.toRecipe(read, locale)
    }

    /**
     * The model's answer as a [Transcription], or a refusal.
     *
     * An answer that is not the object asked for is the provider failing, not the photograph
     * being unreadable — a model that saw no recipe answers with empty lists, as it is told
     * to — so it is a 503 to retry rather than a 400 telling the cook to take the photo again.
     */
    fun parse(answer: String): Transcription {
        val body = objectIn(answer)
        return body?.let { runCatching { json.decodeFromString<Transcription>(it) }.getOrNull() }
            ?: run {
                logger.error { "Recipe photo reader answered with something that is not the transcription: ${answer.take(500)}" }
                throw ServiceUnavailableException(ServiceUnavailableCause.RECIPE_READER_FAILED)
            }
    }

    /**
     * A transcription as the [CooklangService.Parsed] a `.cook` file would have produced.
     *
     * The ingredient list goes in first, carrying every amount, and each step's [Transcription.Step.uses]
     * follows as a mention with no amount. [CooklangService.toRecipe] then does what it does
     * for a Cooklang file that declares an ingredient up front and uses it in a step: the row
     * keeps the declared amount, and the step links to it without claiming a share. That is
     * the honest reading of a printed recipe, whose list says how much and whose method only
     * says when.
     *
     * A use naming nothing in the list is dropped rather than turned into a new row: the list
     * is the model's statement of what the recipe needs, and a second "Flour" appearing because
     * a step said "farine" is noise the cook would have to delete.
     */
    fun toParsed(transcription: Transcription): CooklangService.Parsed {
        val mentions = mutableListOf<CooklangService.Mention>()

        transcription.ingredients.forEach { ingredient ->
            val name = ingredient.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            // The same split a `.cook` file's `{2%cloves}` gets: a unit this product has no
            // column for stays beside the amount as words rather than being lost.
            val (unit, leftover) = unitOf(ingredient.unit.orEmpty())
            val complement = listOfNotNull(leftover, ingredient.note?.trim()?.takeIf { it.isNotEmpty() })
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            mentions += CooklangService.Mention(name, numberOf(ingredient.amount), unit, complement, step = null)
        }

        val listed = mentions.associateBy { fold(it.name) }
        val steps = mutableListOf<CooklangService.Parsed.Step>()
        var split = false

        transcription.steps.forEach { step ->
            val text = step.text?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return@forEach
            val position = steps.size
            val pieces = cooklangService.splitStep(text)
            if (pieces.size > 1) split = true
            val seconds = numberOf(step.minutes)?.let { (it * 60).roundToInt() }?.takeIf { it > 0 }
            pieces.forEachIndexed { index, piece ->
                steps += CooklangService.Parsed.Step(piece, seconds.takeIf { index == 0 })
            }
            // Attributed to the first piece, as a Cooklang paragraph's mentions are.
            step.uses.mapNotNull { use -> listed[fold(use)] ?: listed[fold(use).removeSuffix("s")] }
                .distinctBy { fold(it.name) }
                .forEach { mentions += CooklangService.Mention(it.name, null, it.unit, null, step = position) }
        }

        val metadata = buildMap {
            fun putText(key: String, value: String?) { value?.trim()?.takeIf { it.isNotEmpty() }?.let { put(key, it) } }
            fun putNumber(key: String, value: JsonPrimitive?) {
                numberOf(value)?.roundToInt()?.takeIf { it > 0 }?.let { put(key, it.toString()) }
            }
            putText(KEY_TITLE, transcription.title)
            putText(KEY_DESCRIPTION, transcription.description)
            putNumber(KEY_SERVINGS, transcription.servings)
            putNumber(KEY_PREP_TIME, transcription.prepMinutes)
            putNumber(KEY_COOK_TIME, transcription.cookMinutes)
            putNumber(KEY_TEMPERATURE, transcription.ovenTemperatureCelsius)
            putText(KEY_COURSE, transcription.course)
            putText(KEY_TIPS, transcription.tips)
        }

        return CooklangService.Parsed(metadata, steps, mentions, stepsWereSplit = split)
    }

    /** A number however the model wrote it — 2, 0.5, "2", "1/2" — or null. */
    private fun numberOf(value: JsonPrimitive?): Float? =
        value?.contentOrNull?.let { amountOf(it) }?.takeIf { it.isFinite() }
}
