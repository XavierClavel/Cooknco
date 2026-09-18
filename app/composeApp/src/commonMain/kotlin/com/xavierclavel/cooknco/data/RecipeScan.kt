package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.platform.OcrLine
import kotlin.math.abs

/** One ingredient read off a scanned page, before anything has tried to match it to the catalogue. */
data class ScannedIngredient(
    val name: String,
    val amount: Float? = null,
    /** A unit name as the editor and the server spell it — `GRAM`, `CUP`, `NONE`. */
    val unit: String = "NONE",
    val complement: String? = null,
)

/**
 * A recipe as far as a scanned page could be read.
 *
 * Every field may be missing, and a missing one means "the page did not say, or this could
 * not make it out" — never "the page said none". Nothing downstream treats a null as a
 * failure: all of this lands in the editor, in front of a cook who is holding the page it
 * came from.
 */
data class ScannedRecipe(
    val title: String? = null,
    val description: String? = null,
    val yield: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val temperatureCelsius: Int? = null,
    val ingredients: List<ScannedIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val tips: String? = null,
) {
    /** Nothing worth prefilling: the scan read text, but none of it was a recipe. */
    val isEmpty: Boolean
        get() = title == null && ingredients.isEmpty() && steps.isEmpty()
}

/**
 * Reads a recipe out of the lines a scanner brought back.
 *
 * Best effort, in the sense [StepDurations] is: a page this cannot make sense of yields a
 * title and a pile of steps rather than nothing, and a page it reads well still lands in the
 * editor to be checked. It is offered, never applied — which is what lets it guess at all.
 *
 * The order of the work matters, because each stage is what makes the next possible:
 *
 *  1. **Columns**, from the boxes. A page with its ingredients beside its method produces
 *     lines that interleave the two the moment they are read top to bottom, and no amount of
 *     wording analysis recovers the order afterwards.
 *  2. **The title**, from the line heights, before anything is merged. It is the one part of
 *     a recipe with no wording that identifies it: it is simply the biggest text at the top.
 *  3. **Rejoining**, because a reader breaks a sentence where the column ends rather than
 *     where the sentence does.
 *  4. **Classifying**, line by line, with the section headers as a hint rather than a
 *     requirement — plenty of cards have no "Ingredients" over their ingredients, and the
 *     shape of "100 g de sucre" says what it is on its own.
 *
 * There is **no second copy of this**, unlike [StepDurations]: nothing on the server or the
 * web parses a scan, because nothing there has a camera. Should that change, the thing to
 * move is this file, not to restate its rules somewhere else.
 */
object RecipeScan {

    fun parse(lines: List<OcrLine>): ScannedRecipe {
        if (lines.isEmpty()) return ScannedRecipe()
        val title = titleOf(lines)
        val text = order(lines).flatMap { block -> join(block.filterNot { it === title }) }
        return read(text, title?.text?.trim()?.takeIf { it.isNotEmpty() })
    }

    // ── Reading order ─────────────────────────────────────────────────────────

    /**
     * Splits the scan into the blocks a reader's eye takes in turn: what spans the page, then
     * the left column, then the right. Pages are read in order, and a column is found within
     * one page — clustering across a spread would file page two's left column under page one's.
     */
    private fun order(lines: List<OcrLine>): List<List<OcrLine>> =
        lines.groupBy { it.page }
            .entries
            .sortedBy { it.key }
            .flatMap { orderPage(it.value) }

    private fun orderPage(lines: List<OcrLine>): List<List<OcrLine>> {
        val gutter = gutterOf(lines) ?: return listOf(lines.sortedBy { it.top })
        val left = lines.filter { it.right <= gutter }.sortedBy { it.top }
        val right = lines.filter { it.left >= gutter }.sortedBy { it.top }
        // A line crossing the gutter spans the page: a title, or a header over both columns.
        val spanning = lines.filter { it.left < gutter && it.right > gutter }.sortedBy { it.top }
        val columnTop = minOf(left.firstOrNull()?.top ?: 1f, right.firstOrNull()?.top ?: 1f)
        val above = spanning.filter { it.top < columnTop }
        val below = spanning.filter { it.top >= columnTop }
        return listOf(above, left, right, below).filter { it.isNotEmpty() }
    }

    /**
     * Finds the vertical gutter between two columns, or null when the page has one column.
     *
     * A gutter is an x the text does not cross. Some lines legitimately do — the title, a
     * header over both columns — so the test is "few" rather than "none": the candidate
     * crossed least wins, nearest the middle breaking ties. Both sides then have to look like
     * columns rather than like a stray word beside the text, which is what the line count and
     * the height they span are for. A page that fails either is read straight down, which is
     * the right answer for the single-column card most recipes are on.
     */
    private fun gutterOf(lines: List<OcrLine>): Float? {
        if (lines.size < MIN_COLUMN_LINES * 2) return null

        var gutter = GUTTER_FROM
        var crossings = Int.MAX_VALUE
        var x = GUTTER_FROM
        while (x <= GUTTER_TO) {
            val crossed = lines.count { it.left < x && it.right > x }
            if (crossed < crossings || (crossed == crossings && abs(x - 0.5f) < abs(gutter - 0.5f))) {
                crossings = crossed
                gutter = x
            }
            x += GUTTER_STEP
        }
        if (crossings > lines.size * MAX_SPANNING_SHARE) return null

        val left = lines.filter { it.right <= gutter }
        val right = lines.filter { it.left >= gutter }
        if (left.size < MIN_COLUMN_LINES || right.size < MIN_COLUMN_LINES) return null
        if (span(left) < MIN_COLUMN_SPAN || span(right) < MIN_COLUMN_SPAN) return null
        return gutter
    }

    private fun span(lines: List<OcrLine>) = lines.maxOf { it.bottom } - lines.minOf { it.top }

    // ── The title ─────────────────────────────────────────────────────────────

    /**
     * The biggest line in the top band of the first page, when there is one that stands out.
     *
     * Height rather than position, because "the first line" is as often a byline, a page
     * number or the tail of the previous recipe. And **distinctly** bigger than the body,
     * because otherwise every page has a title: a card set in one size throughout has none to
     * find, and the tallest line in its top band is just its first step. Taking that would be
     * worse than leaving the field blank — the step would be gone from the method as well as
     * wrong in the title.
     *
     * The rest of the filtering is what a title can be at all: short, not a section header,
     * not an ingredient, not the line of times, and not a sentence — a name does not end in a
     * full stop.
     */
    private fun titleOf(lines: List<OcrLine>): OcrLine? {
        val body = lines.map { it.height }.sorted()[lines.size / 2]
        return lines.asSequence()
            .filter { it.page == 0 && it.top <= TITLE_BAND }
            .filter { it.height >= body * TITLE_MIN_RATIO }
            .filter { it.text.trim().length in TITLE_LENGTH }
            .filter { it.text.trim().lastOrNull() !in SENTENCE_END }
            .filter { sectionOf(it.text) == null }
            .filter { !looksLikeIngredient(it.text) }
            .filter { !isMetadataLine(it.text) }
            .maxWithOrNull(compareBy({ it.height }, { -it.top }))
    }

    // ── Rejoining broken lines ────────────────────────────────────────────────

    /**
     * Puts back together what the reader broke at the edge of the column.
     *
     * A continuation is the *tail* of a sentence, so it starts lowercase and the line before
     * it is long — which is what keeps a bulletless list of lowercase ingredients ("farine",
     * "sucre", "œufs") from being merged into one. The vertical gap has to be an ordinary one
     * too: text set further apart than its neighbours is a new thing, whatever it starts with.
     */
    private fun join(block: List<OcrLine>): List<String> {
        if (block.isEmpty()) return emptyList()
        val maxGap = block.map { it.height }.sorted()[block.size / 2] * MAX_CONTINUATION_GAP
        val out = mutableListOf<String>()
        var previous: OcrLine? = null
        for (line in block) {
            val text = line.text.trim()
            if (text.isEmpty()) continue
            val last = out.lastOrNull()
            val gap = previous?.let { line.top - it.bottom }
            if (last != null && gap != null && continues(last, text, gap, maxGap)) {
                out[out.lastIndex] = if (last.endsWith("-")) last.dropLast(1) + text else "$last $text"
            } else {
                out += text
            }
            previous = line
        }
        return out
    }

    private fun continues(previous: String, line: String, gap: Float, maxGap: Float): Boolean {
        if (gap > maxGap) return false
        if (previous.endsWith("-")) return true
        if (previous.lastOrNull() in SENTENCE_END) return false
        if (previous.length < MIN_CONTINUED_LENGTH && !previous.endsWith(",")) return false
        val first = line.firstOrNull() ?: return false
        return first.isLetter() && first.isLowerCase()
    }

    // ── Classifying ───────────────────────────────────────────────────────────

    private enum class Section { UNKNOWN, INGREDIENTS, STEPS, TIPS }

    private fun read(lines: List<String>, title: String?): ScannedRecipe {
        var section = Section.UNKNOWN
        var yields: Int? = null
        var prep: Int? = null
        var cook: Int? = null
        var temperature: Int? = null
        val description = mutableListOf<String>()
        val ingredients = mutableListOf<ScannedIngredient>()
        val steps = mutableListOf<String>()
        val tips = mutableListOf<String>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val header = sectionOf(line)
            if (header != null) {
                section = header
                continue
            }

            // Read before anything else: "Cuisson : 40 min" is a field wherever it sits, and
            // taking it for a step would put the oven in the method twice. A *long* line is a
            // sentence that happens to name a temperature — "préchauffer le four à 180 °C" —
            // so the oven is noted and the sentence stays a step.
            val metadata = metadataOf(line)
            temperature = temperature ?: metadata.temperature
            if (metadata.isField && line.length <= MAX_METADATA_LENGTH) {
                yields = yields ?: metadata.yields
                prep = prep ?: metadata.prep
                cook = cook ?: metadata.cook
                continue
            }

            if (section == Section.TIPS) {
                tips += strip(line)
                continue
            }

            val stripped = strip(line)
            val isStep = when (section) {
                Section.STEPS -> true
                Section.INGREDIENTS -> false
                // No header has said which half of the recipe this is, so the line's own
                // shape decides: a number followed by a stop and a sentence is a step, a
                // number followed by a thing is an ingredient.
                else -> when {
                    NUMBERED.containsMatchIn(line) && stripped.length > MIN_STEP_LENGTH -> true
                    else -> !looksLikeIngredient(line)
                }
            }

            when {
                !isStep -> ingredient(stripped)?.let { ingredients += it }
                // Prose that opens a page, under no header and before any ingredient, is the
                // blurb under the title rather than the method: a recipe does not start
                // cooking before it has said what with. Whether it really was one is not
                // known yet — a card whose method is a paragraph and whose ingredients are
                // named inside it opens exactly the same way — so it is held back and decided
                // below, once there is a whole recipe to decide against.
                section == Section.UNKNOWN && ingredients.isEmpty() && steps.isEmpty() &&
                    !BULLET.containsMatchIn(line) && !NUMBERED.containsMatchIn(line) ->
                    description += stripped
                else -> steps += stripped
            }
        }

        // A blurb introduces the ingredients that follow it. With none, there is nothing for
        // it to have introduced, and prose on a page with no ingredients is the method.
        if (ingredients.isEmpty()) {
            steps.addAll(0, description)
            description.clear()
        }

        return ScannedRecipe(
            title = title,
            description = description.joinToString(" ").trim().takeIf { it.isNotEmpty() },
            yield = yields,
            prepMinutes = prep,
            cookMinutes = cook,
            temperatureCelsius = temperature,
            ingredients = ingredients,
            steps = steps,
            tips = tips.joinToString(" ").trim().takeIf { it.isNotEmpty() },
        )
    }

    /** Drops the bullet or the number a list marks its items with; the text is the item. */
    private fun strip(line: String): String =
        line.replace(BULLET, "").replace(NUMBERED, "").trim()

    /**
     * Whether the line is a section header, and which.
     *
     * A header stands on its own line, which is what tells "Préparation" over the method from
     * "Préparation : 20 min" beside the times and from "Préparer les pommes" in the method
     * itself — hence the length bound and the whole-line match.
     */
    private fun sectionOf(line: String): Section? {
        val folded = fold(line).trim().trim(':', '.', '-', '–', '—', '*', '•').trim()
        if (folded.isEmpty() || folded.length > MAX_HEADER_LENGTH) return null
        return when {
            INGREDIENT_HEADERS.matches(folded) -> Section.INGREDIENTS
            STEP_HEADERS.matches(folded) -> Section.STEPS
            TIP_HEADERS.matches(folded) -> Section.TIPS
            else -> null
        }
    }

    // ── The recipe's own numbers ──────────────────────────────────────────────

    private class Metadata(
        val yields: Int? = null,
        val prep: Int? = null,
        val cook: Int? = null,
        val temperature: Int? = null,
    ) {
        /** Whether the line carries a field of the recipe rather than merely naming a temperature. */
        val isField: Boolean get() = yields != null || prep != null || cook != null
    }

    private fun isMetadataLine(line: String) = metadataOf(line).let { it.isField || it.temperature != null }

    /**
     * Reads the line for the numbers that belong to the recipe rather than to a step.
     *
     * One line can carry several — "Pour 4 personnes · Préparation : 15 min · Cuisson : 30 min"
     * is how most cards print them — so every field is looked for on every line rather than
     * the first match winning.
     */
    private fun metadataOf(line: String): Metadata {
        val folded = fold(line)
        val yields = YIELD.find(folded)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull { it.isNotEmpty() }
            ?.toIntOrNull()
            ?.takeIf { it in 1..99 }
        return Metadata(
            yields = yields,
            prep = minutesAfter(folded, PREP),
            cook = minutesAfter(folded, COOK),
            temperature = temperatureOf(folded),
        )
    }

    /**
     * The duration stated after a label, in minutes.
     *
     * Its own rule rather than [StepDurations], which answers a different question — what a
     * step's *wording* implies — and reads one number by design. A label is followed by a
     * duration written out in full, and "1h30" on a recipe card is ninety minutes.
     *
     * Only what sits between this label and the next is read, so the second field on a line
     * of three does not take its minutes from the third — and only when nothing but
     * punctuation separates the two, which is what tells a field from a sentence.
     */
    private fun minutesAfter(folded: String, label: Regex): Int? {
        val at = label.find(folded) ?: return null
        val rest = folded.substring(at.range.last + 1)
        val stop = OTHER_LABEL.find(rest)?.range?.first ?: rest.length
        LABEL_HOURS.find(rest)?.takeIf { attached(rest, it, stop) }?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return@let
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            return (hours * 60 + minutes).takeIf { it > 0 }
        }
        LABEL_MINUTES.find(rest)?.takeIf { attached(rest, it, stop) }?.let { match ->
            return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
        }
        return null
    }

    private fun attached(rest: String, match: MatchResult, stop: Int): Boolean =
        match.range.first < stop && LABEL_GAP.matches(rest.substring(0, match.range.first))

    private fun temperatureOf(folded: String): Int? {
        // Before Celsius, whose degree sign it shares: "350 °F" would otherwise read as 350 °C.
        FAHRENHEIT.find(folded)?.let { match ->
            val f = match.groupValues[1].toIntOrNull() ?: return@let
            return (f - 32) * 5 / 9
        }
        CELSIUS.find(folded)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.takeIf { it in 30..300 }
        }
        // French ovens are still marked in thermostat notches, thirty degrees apart.
        THERMOSTAT.find(folded)?.let { match ->
            val notch = match.groupValues[1].toIntOrNull() ?: return@let
            return (notch * 30).takeIf { notch in 1..10 }
        }
        return null
    }

    // ── Ingredients ───────────────────────────────────────────────────────────

    /**
     * Whether the line is an item of a list rather than a sentence.
     *
     * A bullet says so outright. Failing that, a quantity at the front does: "100 g de sucre"
     * is an ingredient wherever it is written. A full stop at the end says the opposite
     * loudly enough to overrule both — a bulleted step is still a step.
     */
    private fun looksLikeIngredient(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val body = strip(trimmed)
        if (body.isEmpty() || body.length > MAX_INGREDIENT_LENGTH) return false
        if (body.lastOrNull() in SENTENCE_END) return false
        return BULLET.containsMatchIn(trimmed) || amountOf(body) != null
    }

    /**
     * Splits "100 g de sucre en poudre, tamisé" into its parts.
     *
     * The amount comes off the front, then the unit, then the French particle that belongs to
     * the measurement rather than to the name ("100 g **de** sucre"). What is left is the
     * ingredient, up to the first comma or bracket — which is where a cook stops naming the
     * thing and starts saying what to do to it.
     */
    private fun ingredient(line: String): ScannedIngredient? {
        var rest = line.trim().trimStart(*BULLET_CHARS).trim()
        val amount = amountOf(rest)
        if (amount != null) rest = rest.substring(amount.length).trimStart()

        var unit = "NONE"
        var descriptor: String? = null
        for ((pattern, name) in UNITS) {
            val match = pattern.find(fold(rest)) ?: continue
            unit = name
            // "1 gousse d'ail" is one clove of garlic: the catalogue knows the garlic, so the
            // clove is kept beside it rather than dropped with the rest of the measurement.
            if (name in COUNTERS) descriptor = rest.substring(0, match.value.length).trim()
            rest = rest.substring(match.value.length).trimStart()
            break
        }
        PARTICLE.find(fold(rest))?.let { rest = rest.substring(it.value.length).trimStart() }

        val split = rest.indexOfFirst { it == ',' || it == '(' }
        val name = (if (split >= 0) rest.substring(0, split) else rest)
            .trim()
            .trimEnd('.', ';', ':', ')')
            .trim()
            .take(MAX_NAME_LENGTH)
        if (name.isEmpty()) return null

        val tail = (if (split >= 0) rest.substring(split + 1) else "")
            .trim()
            .trimEnd('.', ';', ')')
            .trim()
        val complement = listOfNotNull(descriptor, tail.takeIf { it.isNotEmpty() })
            .joinToString(", ")
            .take(MAX_NAME_LENGTH)

        return ScannedIngredient(
            name = name,
            amount = amount?.let { quantity(it) },
            // The server rejects a unit with no amount behind it, and an amount with no unit
            // is a count of something.
            unit = when {
                amount == null -> "NONE"
                unit == "NONE" -> "UNIT"
                else -> unit
            },
            complement = complement.takeIf { it.isNotEmpty() },
        )
    }

    /** The quantity at the front of the line, as it was written, or null when there is none. */
    private fun amountOf(line: String): String? =
        AMOUNTS.firstNotNullOfOrNull { it.find(line)?.value?.takeIf { match -> match.isNotEmpty() } }

    /** What that quantity comes to. A range is its lower end: less is the recoverable mistake. */
    private fun quantity(raw: String): Float? {
        val text = raw.trim()
        MIXED_FRACTION.matchEntire(text)?.let {
            val whole = it.groupValues[1].toFloatOrNull() ?: return null
            val numerator = it.groupValues[2].toFloatOrNull() ?: return null
            val denominator = it.groupValues[3].toFloatOrNull()?.takeIf { d -> d != 0f } ?: return null
            return whole + numerator / denominator
        }
        SIMPLE_FRACTION.matchEntire(text)?.let {
            val numerator = it.groupValues[1].toFloatOrNull() ?: return null
            val denominator = it.groupValues[2].toFloatOrNull()?.takeIf { d -> d != 0f } ?: return null
            return numerator / denominator
        }
        VULGAR_FRACTION.matchEntire(text)?.let {
            val whole = it.groupValues[1].toFloatOrNull() ?: 0f
            val fraction = VULGAR[it.groupValues[2].firstOrNull()] ?: return null
            return whole + fraction
        }
        RANGE.matchEntire(text)?.let { return it.groupValues[1].replace(',', '.').toFloatOrNull() }
        return text.replace(',', '.').toFloatOrNull()
    }

    // ── Folding ───────────────────────────────────────────────────────────────

    /**
     * Lowercases and strips accents, one character for one, so an index into the folded text
     * is an index into the original. Internal rather than private because matching a scanned
     * ingredient to the catalogue has to fold the two names the same way this folded them. Everything matched against a vocabulary is matched
     * folded: a reader that drops an accent, and a card set in capitals, say the same words.
     */
    internal fun fold(text: String): String = buildString(text.length) {
        for (c in text) {
            val lower = c.lowercaseChar()
            append(ACCENTS[lower] ?: lower)
        }
    }

    // ── Vocabulary and bounds ─────────────────────────────────────────────────

    private const val TITLE_BAND = 0.30f
    private val TITLE_LENGTH = 3..60

    /** How much taller than the body text a line has to be before it counts as the title. */
    private const val TITLE_MIN_RATIO = 1.25f

    private const val GUTTER_FROM = 0.28f
    private const val GUTTER_TO = 0.72f
    private const val GUTTER_STEP = 0.01f
    private const val MIN_COLUMN_LINES = 4

    /**
     * How much of the page's height a column has to run down.
     *
     * Low, because the line count above is what actually rules out a stray word beside the
     * text, and a short recipe printed in two columns is still printed in two columns. What
     * this catches is the other shape: a caption or a page number sitting alone in a margin,
     * which clears four lines only by accident.
     */
    private const val MIN_COLUMN_SPAN = 0.15f
    private const val MAX_SPANNING_SHARE = 0.20f

    private const val MAX_CONTINUATION_GAP = 1.6f
    private const val MIN_CONTINUED_LENGTH = 25

    private const val MAX_HEADER_LENGTH = 28
    private const val MAX_METADATA_LENGTH = 70
    private const val MAX_INGREDIENT_LENGTH = 70
    private const val MIN_STEP_LENGTH = 30

    /** Matches `CUSTOM_INGREDIENT_NAME_MAX_LENGTH`, which is what the editor stores. */
    private const val MAX_NAME_LENGTH = 50

    private val SENTENCE_END = setOf('.', '!', '?')
    private val BULLET_CHARS = charArrayOf('-', '–', '—', '•', '*', '·', '‣', '>')
    private val BULLET = Regex("^\\s*[-–—•*·‣>]\\s*")
    private val NUMBERED = Regex("^\\s*\\d{1,2}\\s*[.)°]\\s+")

    private val INGREDIENT_HEADERS = Regex(
        "(les )?ingredients?|pour la (pate|garniture|sauce|creme|farce|marinade)[a-z ]*|" +
            "il vous faut|what you need|you will need|shopping list"
    )
    private val STEP_HEADERS = Regex(
        "(la )?preparation|instructions?|etapes?|steps?|method|methode|directions?|" +
            "realisation|how to|marche a suivre"
    )
    private val TIP_HEADERS = Regex("notes?|astuces?|conseils?|tips?|le conseil[a-z ]*|variantes?")

    /**
     * How many the recipe is for.
     *
     * "Serves 4" says it on its own, but "pour 4" does not — French uses the same word for
     * "for", and a step reading "pour 3 cuillères dans le moule" would otherwise set the
     * yield to three. So the two that are unambiguous stand alone and the two that are not
     * have to name what they are counting.
     */
    private val YIELD = Regex(
        "(?:serves|donne)\\s+(\\d{1,3})" +
            "|(?:pour|for)\\s+(\\d{1,3})\\s*(?:personnes?|pers|parts?|portions?|servings?|people)" +
            "|(\\d{1,3})\\s*(?:personnes?|parts?|portions?|servings?)"
    )

    // Bounded on the right so that a label is a label and not the start of a verb: without
    // it, "préparer les pommes" is a preparation time and "cook for 20 minutes" is a cooking
    // one, and both steps vanish into the header fields.
    private val PREP = Regex("prep(?:aration)?(?![a-z])")
    private val COOK = Regex("(?:cuisson|cook(?:ing)?)(?![a-z])")

    /** Where the next labelled field starts, so a duration is not read off the one after it. */
    private val OTHER_LABEL = Regex(
        "(?:prep(?:aration)?|cuisson|cook(?:ing)?|repos|resting|attente|total)(?![a-z])"
    )

    /**
     * What may stand between a label and its duration: punctuation, and the word some cards
     * put there. Anything else means the number belongs to a sentence rather than to a field
     * — "cook **for** 20 minutes" is a step, "cooking time: 20 min" is not.
     */
    private val LABEL_GAP = Regex("[\\s:=·.\\-–—]*(?:time|totale?)?[\\s:=·.\\-–—]*")
    private val LABEL_HOURS = Regex("(\\d{1,2})\\s*(?:h|hrs?|hours?|heures?)\\s*(\\d{1,2})?")
    private val LABEL_MINUTES = Regex("(\\d{1,3})\\s*(?:mn|min|mins|minutes?)")

    private val CELSIUS = Regex("(\\d{2,3})\\s*(?:°|º)\\s*c?(?![a-z0-9])")
    private val FAHRENHEIT = Regex("(\\d{3})\\s*(?:°|º)\\s*f(?![a-z0-9])")
    private val THERMOSTAT = Regex("th(?:ermostat)?\\.?\\s*(\\d{1,2})")

    private val PARTICLE = Regex("^(?:de |d'|d’|des |du |de la |of )")

    private val VULGAR = mapOf(
        '½' to 0.5f, '⅓' to 1f / 3f, '⅔' to 2f / 3f, '¼' to 0.25f, '¾' to 0.75f,
        '⅕' to 0.2f, '⅙' to 1f / 6f, '⅛' to 0.125f,
    )
    private const val VULGAR_CHARS = "½⅓⅔¼¾⅕⅙⅛"

    private val MIXED_FRACTION = Regex("(\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)")
    private val SIMPLE_FRACTION = Regex("(\\d+)\\s*/\\s*(\\d+)")
    private val VULGAR_FRACTION = Regex("(\\d+)?\\s*([$VULGAR_CHARS])")
    private val RANGE = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:-|–|—|a|à|to)\\s*\\d+(?:[.,]\\d+)?")

    /**
     * The quantity spellings, longest first: "1 1/2" has to be tried before "1", and "2-3"
     * before the "2" that starts it, or the rest of the number is read as the ingredient.
     */
    private val AMOUNTS = listOf(
        MIXED_FRACTION, RANGE, SIMPLE_FRACTION, VULGAR_FRACTION, Regex("(\\d+(?:[.,]\\d+)?)"),
    ).map { Regex("^${it.pattern}") }

    /** Units that count a thing rather than measure it, whose word is worth keeping. */
    private val COUNTERS = setOf("UNIT")

    /**
     * Unit spellings, folded, most specific first — `gramme` before `g`, or every gramme is
     * read as a `g` followed by an ingredient called "ramme".
     *
     * The lookahead is what stops a one-letter unit swallowing the start of a word: the `g` of
     * "1 gousse d'ail" is not grams, and the `l` of "1 l'oignon" is not litres.
     */
    private val UNITS: List<Pair<Regex, String>> = listOf(
        "kilogrammes?|kilogramss?|kilos?|kgs?" to "KILOGRAM",
        "grammes?|grams?|gr|g" to "GRAM",
        "livres?|pounds?|lbs?" to "POUND",
        "onces?|ounces?|oz" to "OUNCE",
        "cuilleres? a soupe|cuilleree?s? a soupe|c\\.?\\s*a\\.?\\s*s\\.?|cs|tablespoons?|tbsps?|tbs" to "TABLESPOON",
        "cuilleres? a cafe|cuilleree?s? a cafe|c\\.?\\s*a\\.?\\s*c\\.?|cc|teaspoons?|tsps?" to "TEASPOON",
        "millilitres?|milliliters?|mls?" to "MILLILITERS",
        "centilitres?|centiliters?|cls?" to "CENTILITER",
        "fluid ounces?|fl\\.?\\s*oz" to "FLUID_OUNCE",
        "litres?|liters?|l" to "LITER",
        "tasses?|cups?" to "CUP",
        "pincees?|pinch(?:es)?|sachets?|gousses?|brins?|feuilles?|tranches?|bottes?" to "UNIT",
    ).map { (alternatives, unit) ->
        Regex("^(?:$alternatives)(?![a-z0-9'’])") to unit
    }

    private val ACCENTS = mapOf(
        'à' to 'a', 'â' to 'a', 'ä' to 'a', 'á' to 'a', 'ã' to 'a', 'å' to 'a',
        'ç' to 'c',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'î' to 'i', 'ï' to 'i', 'í' to 'i', 'ì' to 'i',
        'ô' to 'o', 'ö' to 'o', 'ó' to 'o', 'õ' to 'o', 'ò' to 'o',
        'ù' to 'u', 'û' to 'u', 'ü' to 'u', 'ú' to 'u',
        'ÿ' to 'y', 'ñ' to 'n',
    )
}
