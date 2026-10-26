package com.xavierclavel.services

import shared.enums.Locale
import shared.infodto.CiqualFoodInfo
import shared.infodto.CiqualSource
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * The Anses Ciqual table, the reference composition of the foods eaten in France, which the
 * catalogue's nutrition is copied from.
 *
 * It is a file bundled with the backend (`resources/ciqual/`), read once and held in memory:
 * 3 484 read-only rows, which only the backoffice searches, and which change when Anses
 * publishes a new edition — roughly every five years — by replacing the file. There is no
 * table and no API call behind it.
 *
 * The values are published under the Licence Ouverte 2.0 on condition that every reuse cites
 * "Anses. <edition>. Table de composition nutritionnelle des aliments Ciqual", which is what
 * [CiqualSource] carries to the pages that show them.
 */
class CiqualService {
    companion object {
        /** The edition bundled, and the year the citation names. */
        const val EDITION = 2025
        const val MAX_RESULTS = 50

        /**
         * One published value, as it appears in the file.
         *
         * `-` is a constituent Ciqual did not measure, and is unknown rather than zero — it is
         * missing for a quarter of the foods' cholesterol. `traces` and `< x` are measured and
         * below what the method can see: the bounds that are not small (`< 700` mg of cholesterol)
         * sit on foods that hold none at all (vegetable purées), so they read as zero too.
         * Anything else is a decimal with a comma, and a value that is not one fails the load:
         * the file is ours, and a test reads every row of it.
         */
        fun parseValue(raw: String): Float? = when {
            raw == "-" -> null
            raw.equals("traces", ignoreCase = true) -> 0f
            raw.startsWith("<") -> 0f
            else -> raw.replace(',', '.').toFloatOrNull() ?: error("unreadable Ciqual value '$raw'")
        }

        /** Lowercase, accents and ligatures off, so "œuf" finds Ciqual's "Oeuf". */
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")

        private fun wordsOf(text: String): List<String> =
            normalize(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    }

    private class Entry(val food: CiqualFoodInfo, val words: List<List<String>>, val isAverage: Boolean)

    private val entries: List<Entry> by lazy { load() }
    private val byCode: Map<Int, CiqualFoodInfo> by lazy { entries.associate { it.food.code to it.food } }

    fun find(code: Int): CiqualFoodInfo? = byCode[code]

    /** What an ingredient linked to [code] in [edition] cites. */
    fun sourceOf(code: Int, edition: Int) = CiqualSource(code, edition, find(code)?.name.orEmpty())

    /**
     * The foods whose French or English name holds a word starting with each word of [query],
     * best first.
     *
     * Best is a name that *starts* with what was asked — "Tomate, crue" before "Sauce tomate" —
     * then Ciqual's "aliment moyen" entries, its averages over every variety of a food, which
     * are what a generic catalogue entry such as "Tomate" means; then the shortest name. Not
     * fuzzy: an operator who misspells a food sees nothing and retypes it, which is cheaper
     * than a near miss linked by accident.
     */
    fun search(query: String, limit: Int): List<CiqualFoodInfo> {
        val terms = wordsOf(query)
        if (terms.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry ->
                val matching = entry.words.filter { name -> terms.all { term -> name.any { it.startsWith(term) } } }
                if (matching.isEmpty()) return@mapNotNull null
                val leads = matching.any { it.first().startsWith(terms.first()) }
                Triple(entry, leads, matching.minOf { it.size })
            }
            .sortedWith(
                compareBy<Triple<Entry, Boolean, Int>> { !it.second }
                    .thenBy { !it.first.isAverage }
                    .thenBy { it.third }
                    .thenBy { it.first.food.name[Locale.FR]?.length ?: Int.MAX_VALUE }
                    .thenBy { it.first.food.code }
            )
            .take(limit.coerceIn(1, MAX_RESULTS))
            .map { it.first.food }
    }

    private fun load(): List<Entry> {
        val resource = "/ciqual/ciqual-$EDITION.tsv"
        val lines = (javaClass.getResourceAsStream(resource) ?: error("$resource is not on the classpath"))
            .bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.first().split("\t")
        return lines.drop(1).map { line ->
            val cells = line.split("\t")
            check(cells.size == header.size) { "Ciqual row has ${cells.size} columns, not ${header.size}: $line" }
            val row = header.zip(cells).toMap()
            fun value(column: String) = parseValue(row.getValue(column))

            val name = mapOf(Locale.FR to row.getValue("name_fr"), Locale.EN to row.getValue("name_en"))
                .filterValues { it.isNotEmpty() }
            val mono = value("monounsaturated_fat")
            val poly = value("polyunsaturated_fat")
            val food = CiqualFoodInfo(
                code = row.getValue("code").toInt(),
                name = name,
                group = mapOf(Locale.FR to row.getValue("group_fr"), Locale.EN to row.getValue("group_en"))
                    .filterValues { it.isNotEmpty() },
                calories = value("energy_kcal")?.roundToInt(),
                carbohydrates = value("carbohydrates"),
                cholesterol = value("cholesterol_mg"),
                saturatedFat = value("saturated_fat"),
                // A sum of which half is unknown would understate the whole, so it is unknown too
                unsaturatedFat = if (mono != null && poly != null) mono + poly else null,
                sugars = value("sugars"),
                fibers = value("fibers"),
                proteins = value("proteins"),
                salt = value("salt"),
            )
            Entry(
                food = food,
                words = name.values.map { wordsOf(it) },
                isAverage = row.getValue("name_fr").contains("(aliment moyen)"),
            )
        }
    }
}
