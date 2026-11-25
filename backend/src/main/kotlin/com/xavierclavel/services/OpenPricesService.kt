package com.xavierclavel.services

import com.xavierclavel.models.jointables.RecipeIngredient
import com.xavierclavel.utils.searchWordsOf
import shared.enums.AmountUnit
import shared.enums.Locale
import shared.enums.MeasurementType
import shared.enums.PriceLinkStatus
import shared.infodto.OpenPriceInfo
import shared.infodto.RecipeCostInfo

/**
 * What food costs in France, from Open Prices — the prices Open Food Facts contributors read off
 * their receipts — and what a recipe costs to make at those prices.
 *
 * It is a file bundled with the backend (`resources/open-prices/`), read once and held in memory:
 * one median per Open Food Facts category, which `ExtractOpenPrices.kt` computes from the daily
 * dumps. Nothing calls Open Prices at run time. A catalogue ingredient names its category, and its
 * price is looked up here when a recipe is read rather than copied onto the row, so refreshing the
 * file reprices every recipe at the next deploy.
 *
 * The data is under the Open Database License, which asks every page showing a figure derived
 * from it to say so; the clients print that next to the estimate.
 */
class OpenPricesService {
    companion object {
        const val RESOURCE = "/open-prices/prices-fr.tsv"
        const val MAX_RESULTS = 50
    }

    private class Entry(val price: OpenPriceInfo, val words: List<List<String>>)

    private val entries: List<Entry> by lazy { load() }
    private val byCategory: Map<String, OpenPriceInfo> by lazy { entries.associate { it.price.category to it.price } }

    fun find(category: String): OpenPriceInfo? = byCategory[category]

    /** Every category the table prices per [per]. */
    fun categoriesPer(per: AmountUnit): List<String> =
        entries.filter { it.price.per == per }.map { it.price.category }

    /**
     * Whether an ingredient with these conversions, linked to [category], can be priced. Any
     * amount of it reaches grams, through the conversion its unit needs and an ingredient
     * offering that unit has; what can be missing is the way from grams to a litre or a piece.
     * `AdminService`'s missing-price filter states the same rule in SQL.
     */
    fun statusOf(category: String?, gramsPerUnit: Float?, gramsPerMilliliter: Float?): PriceLinkStatus {
        if (category == null) return PriceLinkStatus.NONE
        val price = find(category) ?: return PriceLinkStatus.NOT_IN_TABLE
        val converts = when (price.per.type) {
            MeasurementType.VOLUME -> gramsPerMilliliter != null
            MeasurementType.AMOUNT -> gramsPerUnit != null
            else -> true
        }
        return if (converts) PriceLinkStatus.PRICED else PriceLinkStatus.NOT_CONVERTIBLE
    }

    /**
     * The categories whose French or English name holds a word starting with each word of
     * [query], best first: a name that starts with what was asked, then the one more prices
     * stand behind, then the shortest.
     */
    fun search(query: String, limit: Int): List<OpenPriceInfo> {
        val terms = searchWordsOf(query)
        if (terms.isEmpty()) return emptyList()
        return entries
            .mapNotNull { entry ->
                val matching = entry.words.filter { name -> terms.all { term -> name.any { it.startsWith(term) } } }
                if (matching.isEmpty()) return@mapNotNull null
                Triple(entry.price, matching.any { it.first().startsWith(terms.first()) }, matching.minOf { it.size })
            }
            .sortedWith(
                compareBy<Triple<OpenPriceInfo, Boolean, Int>> { !it.second }
                    .thenByDescending { it.first.prices }
                    .thenBy { it.third }
                    .thenBy { it.first.category }
            )
            .take(limit.coerceIn(1, MAX_RESULTS))
            .map { it.first }
    }

    /**
     * What [lines] cost, and what one of [portions] does; null when no line could be priced,
     * which is a recipe this has nothing to say about rather than one that costs nothing.
     */
    fun estimate(lines: List<RecipeIngredient>, portions: Int?): RecipeCostInfo? {
        val measured = lines.filter { (it.amount ?: 0f) > 0f && it.unit != AmountUnit.NONE }
        val costs = measured.mapNotNull { line ->
            val ingredient = line.ingredient ?: return@mapNotNull null
            val price = ingredient.openPricesCategory?.let(::find) ?: return@mapNotNull null
            ingredient.convert(line.amount!!, line.unit, price.per.type)?.let { it / price.per.factorToBase * price.price }
        }
        if (costs.isEmpty()) return null
        val total = costs.sum()
        return RecipeCostInfo(
            total = total,
            perPortion = portions?.takeIf { it > 0 }?.let { total / it },
            pricedIngredients = costs.size,
            measuredIngredients = measured.size,
        )
    }

    private fun load(): List<Entry> {
        val lines = (javaClass.getResourceAsStream(RESOURCE) ?: error("$RESOURCE is not on the classpath"))
            .bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.first().split("\t")
        return lines.drop(1).map { line ->
            val cells = line.split("\t")
            check(cells.size == header.size) { "Open Prices row has ${cells.size} columns, not ${header.size}: $line" }
            val row = header.zip(cells).toMap()
            val name = mapOf(Locale.FR to row.getValue("name_fr"), Locale.EN to row.getValue("name_en"))
                .filterValues { it.isNotEmpty() }
            val price = OpenPriceInfo(
                category = row.getValue("category"),
                name = name,
                per = AmountUnit.valueOf(row.getValue("per")),
                price = row.getValue("price").toFloat(),
                prices = row.getValue("prices").toInt(),
            )
            Entry(price, name.values.map { searchWordsOf(it) })
        }
    }
}
