package com.xavierclavel.services

import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import shared.dto.CUSTOM_INGREDIENT_NAME_MAX_LENGTH
import shared.dto.RECIPE_STEP_TEXT_MAX_LENGTH
import shared.dto.RecipeDTO
import org.koin.core.component.inject
import shared.enums.AmountUnit
import shared.enums.DishClass
import shared.enums.Locale
import shared.infodto.CooklangImportInfo
import shared.infodto.RecipeInfo
import shared.utils.URL.RECIPE_VIEW_URL
import java.text.Normalizer
import kotlin.math.abs

/**
 * Reads and writes [Cooklang](https://cooklang.org), the plain-text recipe markup, so that a
 * recipe can leave this product and come back into it.
 *
 * The format's one idea is that a recipe is its method, and everything else is derived from
 * it: there is no ingredient list in a `.cook` file, only steps with the ingredients marked
 * up where they are used — `Whisk the @eggs{3} into the @flour{500%g}`. That is the opposite
 * of this product's model, where the list is the thing stored and a step *may* point at rows
 * of it ([RecipeDTO.RecipeStepIngredientDTO]), so neither direction is a field-for-field copy.
 *
 * The two translations are built to be each other's inverse, and [CooklangServiceTest] holds
 * them to it on a round trip rather than on each half separately. Where they cannot be —
 * a file written by another app names units and ingredients this product has never heard of —
 * the rule is the one the scanner already follows: **keep what was said**. An unreadable unit
 * becomes part of the ingredient's complement rather than being dropped, and an ingredient the
 * catalogue does not obviously hold stays free text rather than becoming a near neighbour.
 *
 * ### Why an import returns a [RecipeDTO] and saves nothing
 *
 * Creating the recipe here would mean a second copy of the five ordered steps
 * `RecipeController.createRecipe` performs — validate, insert, replace the ingredients, link
 * the steps, fan out to the followers — and the last of those is the one that settles it: an
 * import that saved would notify every follower of a recipe its owner has not read yet. So
 * this parses and hands back what the editor should be filled in with, exactly as a scan does
 * (`RecipeEditViewModel.prefillFromScan`), and the ordinary create path is what writes it.
 *
 * It is also what makes the import safe to offer for nothing while the export is paid for: a
 * route that stores no rows is not a way to fill the database.
 */
class CooklangService: KoinComponent {
    private val ingredientService: IngredientService by inject()
    private val configuration: Configuration by inject()

    companion object {
        /** Combining marks, which is what an accent decomposes into under NFD. */
        private val DIACRITICS = Regex("\\p{Mn}+")

        /** Where a paragraph too long for one step may be cut. See [splitStep]. */
        private val SENTENCE_END = Regex("(?<=[.!?;:])\\s+")

        /**
         * How this product's units are spelled in a `.cook` file.
         *
         * The short forms, because they are what a recipe is written in and what every other
         * Cooklang tool emits. [UNIT_ALIASES] is what reads them back, and it is deliberately
         * wider than this map is: writing one spelling does not mean only that one is
         * understood.
         *
         * [AmountUnit.UNIT] and [AmountUnit.NONE] map to no unit at all — `@eggs{3}` rather
         * than `@eggs{3%unit}`, which is both what the format means by a bare count and what
         * a reader expects to see.
         */
        private val UNIT_NAMES = mapOf(
            AmountUnit.GRAM to "g",
            AmountUnit.KILOGRAM to "kg",
            AmountUnit.MILLILITERS to "ml",
            AmountUnit.CENTILITER to "cl",
            AmountUnit.LITER to "l",
            AmountUnit.OUNCE to "oz",
            AmountUnit.POUND to "lb",
            AmountUnit.FLUID_OUNCE to "fl oz",
            AmountUnit.TEASPOON to "tsp",
            AmountUnit.TABLESPOON to "tbsp",
            AmountUnit.CUP to "cup",
        )

        /**
         * Every spelling a unit is recognised by on the way in, folded and lower-cased.
         *
         * Wider than [UNIT_NAMES] because the files being read were not written here: the
         * plural, the word in full and the common abbreviation all mean the same unit, and a
         * file saying `500%grams` is not one to give up on. French is in here too — an export
         * of this product is read back by it, and the people using it write `cuillère à soupe`.
         *
         * Anything not in this map is kept rather than guessed at: see [unitOf].
         */
        private val UNIT_ALIASES: Map<String, AmountUnit> = buildMap {
            fun put(unit: AmountUnit, vararg names: String) = names.forEach { put(fold(it), unit) }
            put(AmountUnit.GRAM, "g", "gr", "gram", "grams", "gramme", "grammes")
            put(AmountUnit.KILOGRAM, "kg", "kilo", "kilos", "kilogram", "kilograms", "kilogramme", "kilogrammes")
            put(AmountUnit.MILLILITERS, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
            put(AmountUnit.CENTILITER, "cl", "centiliter", "centiliters", "centilitre", "centilitres")
            put(AmountUnit.LITER, "l", "lt", "liter", "liters", "litre", "litres")
            put(AmountUnit.OUNCE, "oz", "ounce", "ounces", "once", "onces")
            put(AmountUnit.POUND, "lb", "lbs", "pound", "pounds", "livre", "livres")
            put(AmountUnit.FLUID_OUNCE, "fl oz", "floz", "fl. oz", "fluid ounce", "fluid ounces")
            put(AmountUnit.TEASPOON, "tsp", "tsps", "teaspoon", "teaspoons", "cuillere a cafe", "cuilleres a cafe", "cac")
            put(AmountUnit.TABLESPOON, "tbsp", "tbsps", "tablespoon", "tablespoons", "cuillere a soupe", "cuilleres a soupe", "cas")
            put(AmountUnit.CUP, "cup", "cups", "tasse", "tasses")
            // A bare count. The format writes `@eggs{3}` for this, so these only turn up in a
            // file that spelled the unit out, and they mean the same as saying nothing.
            put(AmountUnit.UNIT, "unit", "units", "piece", "pieces", "piece(s)", "x")
        }

        /**
         * The metadata keys this writes and reads.
         *
         * `title`, `servings`, `source` and `course` are the format's own canonical names, so
         * another tool understands an export of ours. The rest name things Cooklang has no
         * canonical key for; they are read back by us and ignored by everything else, which is
         * the right outcome either way — a reader that does not know `cooking temperature`
         * loses the temperature, not the recipe.
         */
        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_SERVINGS = "servings"
        private const val KEY_PREP_TIME = "prep time"
        private const val KEY_COOK_TIME = "cook time"
        private const val KEY_TEMPERATURE = "cooking temperature"
        private const val KEY_COURSE = "course"
        private const val KEY_TIPS = "tips"
        private const val KEY_AUTHOR = "author"
        private const val KEY_SOURCE = "source"

        /** What a `.cook` file is called and served as. */
        const val COOKLANG_EXTENSION = "cook"

        /**
         * The format has no registered media type, so this is the convention its own tools
         * use. `text/plain` would be true and would also invite a browser to display the file
         * instead of saving it, which is not what an export is for.
         */
        const val COOKLANG_MEDIA_TYPE = "text/x-cooklang"

        /**
         * The largest file an import reads into memory.
         *
         * A recipe is a page of text; this is three hundred times that, so nothing anyone
         * actually cooks from is near it. It is a bound on what an unpaid route will hold in
         * memory, not a judgement about recipes — the import is open to every signed-in
         * account, so it is the one that needs one.
         */
        const val MAX_IMPORT_BYTES = 1024L * 1024L

        /** Case, accents and surrounding space removed — the form names are compared in. */
        fun fold(value: String): String =
            DIACRITICS.replace(Normalizer.normalize(value.trim(), Normalizer.Form.NFD), "")
                .lowercase()
                .replace(Regex("\\s+"), " ")

        /** The number a Cooklang quantity states, including the `1/2` the format allows. */
        fun amountOf(raw: String): Float? {
            val value = raw.trim().replace(',', '.')
            if (value.isEmpty()) return null
            val fraction = value.split('/')
            if (fraction.size == 2) {
                val top = fraction[0].trim().toFloatOrNull() ?: return null
                val bottom = fraction[1].trim().toFloatOrNull() ?: return null
                return if (bottom == 0f) null else top / bottom
            }
            return value.toFloatOrNull()
        }

        /**
         * The unit a file named, and whatever of it could not be read.
         *
         * Returns [AmountUnit.NONE] and the original words when the name is one this product
         * has no column for, so that [amountsOf] can keep them beside the amount rather than
         * throw either half away — "2 cloves of garlic" survives as a count of 2 with
         * "cloves" in the complement. Dropping the word would leave "2 garlic", and dropping
         * the number would leave "garlic, cloves"; both read as correct and neither is.
         */
        fun unitOf(name: String): Pair<AmountUnit, String?> {
            if (name.isBlank()) return AmountUnit.NONE to null
            val unit = UNIT_ALIASES[fold(name)]
            return if (unit != null) unit to null else AmountUnit.NONE to name.trim()
        }
    }

    /**
     * Where the recipe lives on the web, for the file's `source` key.
     *
     * A `.cook` file travels — that is the whole point of exporting one — and a recipe that
     * says where it came from can be found again by whoever it is passed to. The same address
     * the PDF sheet prints, built the same way.
     */
    fun originOf(recipeId: Long): String =
        "${configuration.frontend.url.trimEnd('/')}/$RECIPE_VIEW_URL?id=$recipeId"

    // ── Writing ───────────────────────────────────────────────────────────────

    /**
     * The recipe as a `.cook` file.
     *
     * @param locale which language the ingredients are named in. The same parameter the PDF
     *   sheet takes, for the same reason: the file is written to be handed over, and only the
     *   caller knows who to.
     * @param origin where the recipe lives on the web, written into `source` so that a file
     *   passed around still says where it came from. Null leaves the key out.
     */
    fun write(recipe: RecipeInfo, locale: Locale, origin: String? = null): String = buildString {
        appendMetadata(KEY_TITLE, recipe.title)
        appendMetadata(KEY_DESCRIPTION, recipe.description)
        appendMetadata(KEY_AUTHOR, recipe.owner.username)
        appendMetadata(KEY_SERVINGS, recipe.yield?.toString())
        appendMetadata(KEY_PREP_TIME, recipe.preparationTime?.let { "$it min" })
        appendMetadata(KEY_COOK_TIME, recipe.cookingTime?.let { "$it min" })
        appendMetadata(KEY_TEMPERATURE, recipe.cookingTemperature?.let { "$it °C" })
        appendMetadata(KEY_COURSE, recipe.dishClass.name.lowercase().replace('_', ' '))
        appendMetadata(KEY_TIPS, recipe.tips)
        appendMetadata(KEY_SOURCE, origin)
        if (isNotEmpty()) append('\n')

        // Which rows each step speaks for, so that an ingredient is written where it is used
        // and the ones no step claims can be told apart below.
        val claimed = recipe.steps.flatMap { step -> step.ingredients.map { it.index } }.toSet()
        val unclaimed = recipe.ingredients.indices.filterNot { claimed.contains(it) }

        // Every ingredient no step points at, on a line of its own before the method.
        //
        // Cooklang has nowhere else to put them — its ingredient list *is* what the steps
        // mention, so a row left out of the file is a row lost. The line carries annotations
        // and no prose, which is what [parse] recognises to give the ingredients back without
        // inventing a step out of them. Another tool reads it as a step that renders as the
        // list it is, which is the least wrong thing it could do with it.
        if (unclaimed.isNotEmpty()) {
            appendLine(unclaimed.writeIngredientList(recipe, locale))
            appendLine()
        }

        recipe.steps.forEachIndexed { index, step ->
            if (index > 0) appendLine()
            appendLine(writeStep(step, recipe, locale))
        }
    }

    /** The ingredients at [this] positions, marked up one after another. */
    private fun List<Int>.writeIngredientList(recipe: RecipeInfo, locale: Locale): String =
        joinToString(" ") { position ->
            val ingredient = recipe.ingredients[position]
            writeIngredient(ingredient.name, ingredient.amount, ingredient.unit, ingredient.complement)
        }

    /**
     * One step, with the ingredients it uses marked up inside its own wording.
     *
     * The annotation replaces the ingredient's name where the step already names it — which is
     * what makes the file read like a recipe rather than like a list with prose after it — and
     * is appended when it does not. Appending is not a failure: plenty of steps use an
     * ingredient without naming it ("fold the two together"), and a `.cook` file that dropped
     * those would lose the amounts with them.
     *
     * A step's own timer is written as one, so that it survives to the reader's cook mode
     * instead of being a number in a sentence they have to find again.
     */
    private fun writeStep(step: RecipeDTO.RecipeStepDTO, recipe: RecipeInfo, locale: Locale): String {
        var text = step.text
        val appended = mutableListOf<String>()

        step.ingredients.forEach { used ->
            val ingredient = recipe.ingredients.getOrNull(used.index) ?: return@forEach
            // The step's own share when it states one, and the row's amount when it does not.
            // A blank means "the rest of it", and what that comes to is the reader's to work
            // out — as it is everywhere else in this product ([RecipeStepIngredientDTO.amount]).
            val markup = writeIngredient(ingredient.name, used.amount, ingredient.unit, ingredient.complement)
            val at = text.indexOfFolded(ingredient.name)
            if (at >= 0) {
                text = text.substring(0, at) + markup + text.substring(at + ingredient.name.length)
            } else {
                appended += markup
            }
        }

        val timer = step.durationSeconds?.let { " ${writeTimer(it)}" }.orEmpty()
        val trailing = if (appended.isEmpty()) "" else " ${appended.joinToString(" ")}"
        return (text + trailing + timer).trim()
    }

    /**
     * `@name{amount%unit}(complement)`, always braced.
     *
     * Braced even for a one-word name with nothing to say about it, because the unbraced form
     * ends at the first space and would swallow the punctuation after it — `@salt.` names an
     * ingredient called "salt." to every reader of the format.
     */
    private fun writeIngredient(name: String, amount: Float?, unit: AmountUnit, complement: String?): String {
        val quantity = writeAmount(amount)
        val unitName = UNIT_NAMES[unit].orEmpty()
        val body = when {
            quantity.isEmpty() -> ""
            unitName.isEmpty() -> quantity
            else -> "$quantity%$unitName"
        }
        val note = complement?.takeIf { it.isNotBlank() }?.let { "(${escape(it)})" }.orEmpty()
        return "@${escape(name)}{$body}$note"
    }

    /**
     * The step's countdown as a Cooklang timer.
     *
     * Written in whichever of hours, minutes and seconds says it without a remainder, because
     * `~{90%minutes}` and `~{1.5%hours}` are the same timer and only one of them reads like
     * something a cook wrote.
     */
    private fun writeTimer(seconds: Int): String = when {
        seconds % 3600 == 0 -> "~{${seconds / 3600}%hours}"
        seconds % 60 == 0 -> "~{${seconds / 60}%minutes}"
        else -> "~{$seconds%seconds}"
    }

    /** A whole number without its `.0`, which is how an amount is written everywhere else. */
    private fun writeAmount(amount: Float?): String = when {
        amount == null -> ""
        abs(amount - amount.toInt()) < 0.001f -> amount.toInt().toString()
        else -> amount.toString().trimEnd('0').trimEnd('.')
    }

    /** The characters that would otherwise start or end something in the markup. */
    private fun escape(value: String): String =
        value.replace(Regex("[@#~{}%()\\[\\]]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun StringBuilder.appendMetadata(key: String, value: String?) {
        if (value.isNullOrBlank()) return
        appendLine(">> $key: ${value.replace('\n', ' ').trim()}")
    }

    /** Where [needle] sits in this string, comparing case and accents loosely, or -1. */
    private fun String.indexOfFolded(needle: String): Int {
        if (needle.isBlank() || needle.length > length) return -1
        val hay = fold(this)
        val at = hay.indexOf(fold(needle))
        // Folding preserves length for everything but surrounding space, which `fold` trims —
        // so an offset into the folded string is one into this one only when nothing was cut.
        return if (at >= 0 && hay.length == length) at else -1
    }

    // ── Reading ───────────────────────────────────────────────────────────────

    /** One ingredient as a file mentioned it, before the catalogue has been asked about it. */
    data class Mention(
        val name: String,
        val amount: Float?,
        val unit: AmountUnit,
        val complement: String?,
        /** The step it was mentioned in, or null when it came from the declaration line. */
        val step: Int?,
    )

    /** A `.cook` file, read. Names are as the file spelled them: nothing is matched up yet. */
    data class Parsed(
        val metadata: Map<String, String>,
        val steps: List<Step>,
        val mentions: List<Mention>,
        /**
         * Whether a paragraph had to be cut because it was longer than a step may be.
         *
         * Recorded where the cut is made rather than worked out afterwards: nothing about the
         * finished list says whether two steps were one paragraph or two, so anything derived
         * from it would be a guess. See [splitStep].
         */
        val stepsWereSplit: Boolean = false,
    ) {
        data class Step(val text: String, val durationSeconds: Int?)

        val isEmpty: Boolean get() = steps.isEmpty() && mentions.isEmpty() && metadata.isEmpty()
    }

    /**
     * Reads a `.cook` file into its metadata, its steps and every ingredient it mentions.
     *
     * Best effort throughout, in the sense the scanner is: a line this cannot make sense of
     * becomes a step saying what it said, and a file that is not Cooklang at all comes back as
     * its own text rather than as an error. What decides whether the import is refused is
     * [Parsed.isEmpty] and nothing finer — the caller is a cook holding a file, and "this had
     * nothing in it" is the only verdict they can act on.
     *
     * A line whose annotations leave no prose behind declares ingredients without being a step.
     * That is what [write] emits for the rows no step claims, and it is what makes the two
     * directions inverses of each other rather than nearly so.
     */
    fun parse(source: String): Parsed {
        val metadata = mutableMapOf<String, String>()
        val steps = mutableListOf<Parsed.Step>()
        val mentions = mutableListOf<Mention>()
        var split = false

        strip(source).lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith(">>")) {
                val body = line.removePrefix(">>")
                val at = body.indexOf(':')
                if (at > 0) metadata[fold(body.substring(0, at))] = body.substring(at + 1).trim()
                return@forEach
            }

            // Parsed against the step this would become, so that a line turning out to be a
            // declaration can hand its mentions back with no step attached.
            val position = steps.size
            val read = readLine(line, position)
            if (!read.hasProse) {
                mentions += read.mentions.map { it.copy(step = null) }
            } else {
                // A paragraph longer than a step may be is cut rather than refused or trimmed:
                // the file said all of it, and a cook can join two steps far more easily than
                // they can recover a sentence the import ate.
                val pieces = splitStep(read.text)
                if (pieces.size > 1) split = true
                pieces.forEachIndexed { index, piece ->
                    steps += Parsed.Step(piece, read.durationSeconds.takeIf { index == 0 })
                }
                // Everything the paragraph mentioned belongs to the first piece of it: the
                // cut is arbitrary, so attributing amounts across it would be too.
                mentions += read.mentions.map { it.copy(step = position) }
            }
        }

        return Parsed(metadata, steps, mentions, stepsWereSplit = split)
    }

    private data class ReadLine(
        val text: String,
        val durationSeconds: Int?,
        val mentions: List<Mention>,
        /**
         * Whether the line said anything of its own.
         *
         * An annotation puts the ingredient's name into the wording, so [text] is never empty
         * on a line that mentions one — which is exactly what a declaration line is. The
         * question "was there a step here" is therefore about what the line held *between*
         * its annotations, and that is what this answers.
         */
        val hasProse: Boolean,
    )

    /**
     * One line of a method: its wording with the markup taken out, and what the markup said.
     *
     * Cookware (`#pan`) is read so that it can be *removed* — this product has no model for
     * it, and leaving `#` in the step's wording would be worse than losing the fact that a pan
     * was involved. Its name stays in the sentence, which is where a cook reads it anyway.
     */
    private fun readLine(line: String, position: Int): ReadLine {
        val text = StringBuilder()
        // Only what was written outside the annotations, which is what says whether this is a
        // step at all. See [ReadLine.hasProse].
        val prose = StringBuilder()
        val mentions = mutableListOf<Mention>()
        var duration: Int? = null
        var i = 0

        while (i < line.length) {
            when (line[i]) {
                '@', '#', '~' -> {
                    val marker = line[i]
                    val token = readToken(line, i)
                    if (token == null) {
                        text.append(line[i])
                        i++
                        continue
                    }
                    when (marker) {
                        '@' -> {
                            mentions += token.toMention(position)
                            text.append(token.name)
                        }
                        // The name is what a cook reads; the amount belongs to the pan, not
                        // to the recipe, so it is dropped with the marker.
                        '#' -> text.append(token.name)
                        // The first timer on the line is the step's. A second one is a
                        // sentence about time rather than a countdown to show.
                        '~' -> {
                            val seconds = token.toSeconds()
                            val taken = duration == null && seconds != null
                            if (taken) duration = seconds
                            // Put the words back only when they are *not* being carried by
                            // the step's own timer. Doing both would spell the duration into
                            // the wording as well, and the next export would write a second
                            // timer after it — a round trip that grows a sentence each time.
                            text.append(if (taken) token.name else token.spelledOut())
                            if (!taken) prose.append(token.spelledOut())
                        }
                    }
                    i = token.end
                }
                else -> {
                    text.append(line[i])
                    prose.append(line[i])
                    i++
                }
            }
        }

        return ReadLine(
            // The space a removed annotation leaves in front of the punctuation that followed
            // it goes with it: "Fry for ~{2%minutes}." must not read "Fry for ."
            text = text.toString()
                .replace(Regex("\\s+"), " ")
                .replace(Regex("\\s+([.,;:!?])"), "$1")
                .trim(),
            durationSeconds = duration,
            mentions = mentions,
            hasProse = prose.any { it.isLetterOrDigit() },
        )
    }

    /** `@name{quantity%unit}(note)` as it was written, with where it ended. */
    private data class Token(
        val name: String,
        val quantity: String,
        val unit: String,
        val note: String?,
        val end: Int,
    ) {
        fun toMention(position: Int): Mention {
            val amount = amountOf(quantity)
            val (unit, leftover) = unitOf(unit)
            // A unit this product has no column for is kept as words beside the amount rather
            // than dropped: "2 gousses" is a working ingredient, "2" is a wrong one.
            val complement = listOfNotNull(leftover, note?.takeIf { it.isNotBlank() })
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            return Mention(name.trim(), amount, unit, complement, position)
        }

        /** The timer in seconds, or null when it said something that is not a duration. */
        fun toSeconds(): Int? {
            val value = amountOf(quantity) ?: return null
            val scale = when (fold(unit).removeSuffix("s")) {
                "hour", "hr", "h", "heure" -> 3600
                "minute", "min", "mn", "m" -> 60
                "second", "sec", "s" -> 1
                // Cooklang's own default for a bare `~{25}` is minutes, and it is what a
                // recipe means by a number next to a step.
                "" -> 60
                else -> return null
            }
            return (value * scale).toInt().takeIf { it > 0 }
        }

        /** The timer put back into the sentence, so the wording still says how long. */
        fun spelledOut(): String =
            listOf(name, quantity, unit).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Reads the token starting at [start], or null when what follows the marker is not one.
     *
     * A marker with nothing after it is punctuation — an email address in a tip, a `#` in a
     * temperature — and is put back into the wording by the caller rather than eaten.
     */
    private fun readToken(line: String, start: Int): Token? {
        // `@&flour`, `@?salt`: the reference and optional modifiers of the newer spec. Read
        // and discarded, since neither changes what the ingredient is.
        var i = start + 1
        while (i < line.length && line[i] in "&?+-") i++

        val nameStart = i
        val brace = line.indexOf('{', i)
        val name: String
        if (brace >= 0 && line.substring(i, brace).isNameBody()) {
            name = line.substring(nameStart, brace)
            i = brace
        } else {
            // The unbraced form, which ends at the first space or punctuation.
            while (i < line.length && (line[i].isLetterOrDigit() || line[i] in "'’-_")) i++
            name = line.substring(nameStart, i)
            return if (name.isBlank()) null else Token(name, "", "", null, i)
        }

        val close = line.indexOf('}', i)
        if (close < 0) return if (name.isBlank()) null else Token(name, "", "", null, i)
        val body = line.substring(i + 1, close)
        val percent = body.indexOf('%')
        val quantity = if (percent >= 0) body.substring(0, percent) else body
        val unit = if (percent >= 0) body.substring(percent + 1) else ""
        i = close + 1

        // `(sifted)`, the preparation note, when it follows immediately.
        var note: String? = null
        if (i < line.length && line[i] == '(') {
            val end = line.indexOf(')', i)
            if (end >= 0) {
                note = line.substring(i + 1, end).trim()
                i = end + 1
            }
        }

        return if (name.isBlank() && quantity.isBlank()) null else Token(name, quantity.trim(), unit.trim(), note, i)
    }

    /**
     * Whether what sits between a marker and a `{` is a name rather than a sentence.
     *
     * Empty counts: `~{25%minutes}` is the format's ordinary anonymous timer, and `@{}` is a
     * legal if pointless ingredient. It is only reached when the brace follows the marker
     * immediately, so accepting it costs nothing elsewhere.
     */
    private fun String.isNameBody(): Boolean =
        length <= 80 && none { it in "@#~{}." }

    /** Removes the comments, which may not be read as wording. */
    private fun strip(source: String): String =
        source
            .replace(Regex("\\[-.*?-]", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("--") }

    /**
     * A paragraph as one step, or as the fewest steps it fits in.
     *
     * `recipe_steps.text` holds [RECIPE_STEP_TEXT_MAX_LENGTH] characters, and a Cooklang
     * paragraph has no such bound — a file written by hand routinely runs a whole method into
     * one. Cut at a sentence end where there is one within reach, and at a word otherwise;
     * never mid-word, and never silently shortened.
     */
    private fun splitStep(text: String): List<String> {
        if (text.length <= RECIPE_STEP_TEXT_MAX_LENGTH) return listOf(text)
        val pieces = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > RECIPE_STEP_TEXT_MAX_LENGTH) {
            val window = rest.take(RECIPE_STEP_TEXT_MAX_LENGTH)
            val sentence = SENTENCE_END.findAll(window).lastOrNull()?.range?.last?.plus(1)
            val cut = sentence ?: window.lastIndexOf(' ').takeIf { it > 0 } ?: RECIPE_STEP_TEXT_MAX_LENGTH
            pieces += rest.take(cut).trim()
            rest = rest.drop(cut).trim()
        }
        if (rest.isNotEmpty()) pieces += rest
        return pieces
    }

    // ── Assembling ────────────────────────────────────────────────────────────

    /**
     * A parsed file as the recipe the editor should open on.
     *
     * The whole of this method's difficulty is that the result has to be a [RecipeDTO] the
     * ordinary save path *accepts*, and that path refuses three things a `.cook` file says
     * happily. Producing a recipe the user cannot then save would be the worst outcome of the
     * three, so each is resolved here rather than left to fail at the end:
     *
     *  - **An amount and a unit imply each other** (`RecipeIngredientService.validateAmount`).
     *    `@eggs{3}` names no unit, so it arrives as [AmountUnit.NONE] with an amount, which is
     *    refused — a bare count is [AmountUnit.UNIT], and that is what a count becomes here. An
     *    amount that is zero, negative or unreadable takes its unit away with it.
     *  - **A step's claims must add up to the row's amount**
     *    (`RecipeIngredientService.validateStepIngredients`). The row's amount is therefore
     *    *derived* — it is the sum of what the steps claimed, never a number of this method's
     *    own — which makes the two agree by construction rather than by luck. See [amountsOf].
     *  - **A catalogue entry may not be measurable the way the file measured it**
     *    (`resolveIngredient`). Flour in grams is fine; flour in "cloves" is not, and the file
     *    is the one holding the recipe somebody wants to cook. So the amount wins and the row
     *    stays free text, which works and is honest, rather than the amount being dropped to
     *    keep a link the cook never asked for.
     *
     * @param locale which language to look the ingredients up in. A file names them in the
     *   language it was written in, and the catalogue is the only thing that can say whether
     *   this product knows that name — so a French file imported as EN simply matches less,
     *   and everything still arrives as free text.
     */
    fun toRecipe(parsed: Parsed, locale: Locale): CooklangImportInfo {
        // One row per ingredient, in the order the file first mentioned it, which is the order
        // a reader met them in and so the least surprising one to see in the editor.
        val grouped = LinkedHashMap<String, MutableList<Mention>>()
        parsed.mentions.forEach { grouped.getOrPut(fold(it.name)) { mutableListOf() }.add(it) }

        val matches = ingredientService.findByNames(grouped.values.map { it.first().name }, locale)

        val rows = mutableListOf<RecipeDTO.RecipeIngredientDTO>()
        val names = mutableListOf<String>()
        // position in `rows` -> the steps that claimed it, and for how much
        val claims = mutableListOf<List<Pair<Int, Float?>>>()
        var unmatched = 0

        grouped.forEach { (folded, mentions) ->
            val name = mentions.first().name
            val (amount, unit, perStep) = amountsOf(mentions)
            val complement = mentions.mapNotNull { it.complement }.distinct()
                .joinToString(", ").takeIf { it.isNotBlank() }

            val match = matches[folded] ?: matches[folded.removeSuffix("s")]
            // The catalogue holds it, and can be measured the way the file measured it.
            val usable = match?.takeIf { unit.type in it.allowedTypes }
            if (usable == null) unmatched++

            rows += RecipeDTO.RecipeIngredientDTO(
                id = usable?.id,
                customName = if (usable == null) name.take(CUSTOM_INGREDIENT_NAME_MAX_LENGTH) else null,
                unit = unit,
                amount = amount,
                complement = complement,
            )
            names += (usable?.name?.get(locale) ?: name)
            claims += perStep
        }

        val steps = parsed.steps.mapIndexed { index, step ->
            RecipeDTO.RecipeStepDTO(
                text = step.text,
                durationSeconds = step.durationSeconds,
                ingredients = claims.flatMapIndexed { position, byStep ->
                    byStep.filter { it.first == index }
                        .map { RecipeDTO.RecipeStepIngredientDTO(position, it.second) }
                },
            )
        }

        val metadata = parsed.metadata
        return CooklangImportInfo(
            recipe = RecipeDTO(
                title = metadata[KEY_TITLE].orEmpty().trim(),
                description = metadata[KEY_DESCRIPTION].orEmpty().trim(),
                dishClass = dishClassOf(metadata[KEY_COURSE]),
                yield = numberIn(metadata[KEY_SERVINGS]),
                preparationTime = numberIn(metadata[KEY_PREP_TIME]),
                cookingTime = numberIn(metadata[KEY_COOK_TIME]),
                cookingTemperature = numberIn(metadata[KEY_TEMPERATURE]),
                ingredients = rows,
                steps = steps.toMutableList(),
                tips = metadata[KEY_TIPS].orEmpty().trim(),
            ),
            ingredientNames = names,
            unmatchedIngredients = unmatched,
            stepsWereSplit = parsed.stepsWereSplit,
        )
    }

    /** An ingredient's row amount and unit, and what each step that used it may claim. */
    private data class Amounts(
        val amount: Float?,
        val unit: AmountUnit,
        val perStep: List<Pair<Int, Float?>>,
    )

    /**
     * Adds up everything a file said about one ingredient.
     *
     * The row's amount is the sum of the mentions, converted onto the first one's unit where
     * they differ but measure the same kind of thing — a file may well say `200%g` in one step
     * and `0.3%kg` in another, and 200 g + 0.3 kg is 500 g rather than a contradiction.
     *
     * **A step only claims a number when the steps account for the whole row.** An ingredient
     * declared up front *and* used in a step would otherwise have a row amount the step's
     * claim falls short of, which `validateStepIngredients` refuses — correctly, since the
     * claim really does not add up. The step then says "uses flour" without saying how much,
     * which is true, and the amount is still on the row where the cook reads it. The same
     * applies when the units cannot be reconciled: the link survives, the arithmetic does not
     * pretend.
     */
    private fun amountsOf(mentions: List<Mention>): Amounts {
        val unit = mentions.map { it.unit }.firstOrNull { it != AmountUnit.NONE } ?: AmountUnit.NONE
        val links = mentions.mapNotNull { mention -> mention.step?.let { it to mention } }

        // What each mention is worth on `unit`'s ladder, or null when it said no number and
        // `false` overall when one of them cannot be put on it at all.
        val convertible = mentions.all { it.amount == null || it.unit == AmountUnit.NONE || it.unit.type == unit.type }
        fun valueOf(mention: Mention): Float? =
            mention.amount?.takeIf { it > 0f }?.let {
                if (mention.unit == AmountUnit.NONE || mention.unit == unit) it
                else it * mention.unit.factorToBase / unit.factorToBase
            }

        val stated = if (convertible) mentions.mapNotNull { valueOf(it) } else emptyList()
        val total = stated.sum().takeIf { stated.isNotEmpty() && it > 0f }

        // A count with no unit named is a count, not a unitless number: the save path refuses
        // an amount without a unit, and `@eggs{3}` plainly has one.
        val effective = when {
            total == null -> AmountUnit.NONE
            unit == AmountUnit.NONE -> AmountUnit.UNIT
            else -> unit
        }

        val stepsOwnItAll = convertible && links.size == mentions.size
        val perStep = links.map { (step, mention) ->
            step to (valueOf(mention)?.takeIf { stepsOwnItAll && total != null })
        }
        return Amounts(total.takeIf { effective != AmountUnit.NONE }, effective, perStep)
    }

    /** The first whole number in a metadata value — "30 min", "180 °C", "serves 4". */
    private fun numberIn(value: String?): Int? =
        value?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }?.takeIf { it > 0 }

    /**
     * The course a file named, or [DishClass.MAIN_DISH].
     *
     * Falls back rather than refusing: `course` is free text in the format, so a file is far
     * more likely to say "dinner" than one of this product's seven names, and a recipe filed
     * under the wrong heading is one tap from being right.
     */
    private fun dishClassOf(value: String?): DishClass {
        if (value.isNullOrBlank()) return DishClass.MAIN_DISH
        val wanted = fold(value).replace(' ', '_')
        return DishClass.entries.firstOrNull { fold(it.name) == wanted } ?: DishClass.MAIN_DISH
    }

}
