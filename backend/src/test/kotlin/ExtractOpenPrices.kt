package main

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Turns the Open Prices dumps into the table the backend bundles,
 * `backend/src/main/resources/open-prices/prices-fr.tsv`: for each Open Food Facts category, the
 * median price paid for it in France over the year before the run.
 *
 * Run with `sh ./gradlew :backend:extractOpenPrices --args="<directory>"`, where the directory
 * holds the daily dumps `prices.jsonl.gz`, `products.jsonl.gz` and `locations.jsonl.gz` from
 * https://prices.openfoodfacts.org/data/ and the category taxonomy `categories.json` from
 * https://static.openfoodfacts.org/data/taxonomies/categories.json.
 *
 * A price reaches a category one of two ways. A loose product, sold without a barcode, is
 * recorded against its category, per kilo or per piece. A packaged one is recorded against its
 * barcode, and counts towards every category Open Food Facts files the product under, divided by
 * the weight or volume printed on the pack. A category sold loose at all takes its loose prices
 * alone, and is left out if there are too few of them: the packaged products filed under onions
 * are fried onions and soups, at eight times the price of an onion.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "usage: extractOpenPrices <directory with the Open Prices dumps and categories.json>" }
    val directory = File(args[0])
    val until = LocalDate.now()
    val since = until.minusYears(1)

    val countries = jsonLines(File(directory, "locations.jsonl.gz"))
        .associate { it.long("id") to it.text("osm_address_country_code") }
    val packs = jsonLines(File(directory, "products.jsonl.gz"))
        .mapNotNull { product ->
            val quantity = product.text("product_quantity")?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val per = when (product.text("product_quantity_unit")?.lowercase()) {
                "g" -> "KILOGRAM"
                "ml" -> "LITER"
                else -> return@mapNotNull null
            }
            val categories = product["categories_tags"].strings()
            product.text("code")!! to Pack(per, quantity / 1000, categories)
        }
        .toMap()
    val names = Json.parseToJsonElement(File(directory, "categories.json").readText()).jsonObject

    val loose = Samples()
    val packed = Samples()
    for (price in jsonLines(File(directory, "prices.jsonl.gz"))) {
        if (price.text("duplicate_of") != null || price.text("currency") != "EUR") continue
        if (countries[price.long("location_id")] != "FR") continue
        val date = price.text("date")?.let(LocalDate::parse) ?: continue
        if (date < since || date > until) continue
        // The shelf price, not the offer: an estimate is of an ordinary day's shopping
        val paid = (price.text("price_without_discount") ?: price.text("price"))?.toDoubleOrNull()
            ?.takeIf { it > 0 } ?: continue

        val category = price.text("category_tag")
        if (category != null) {
            val per = when (price.text("price_per")) {
                "KILOGRAM" -> "KILOGRAM"
                "UNIT" -> "UNIT"
                else -> continue
            }
            loose.add(category, per, paid)
        } else {
            val pack = packs[price.text("product_code")] ?: continue
            pack.categories.forEach { packed.add(it, pack.per, paid / pack.quantity) }
        }
    }

    val rows = (loose.categories() + packed.categories()).sorted().mapNotNull { category ->
        val isLoose = category in loose.categories()
        val (per, values) = (if (isLoose) loose else packed).best(category) ?: return@mapNotNull null
        val name = names[category]?.jsonObject?.get("name")?.jsonObject
        // A category the taxonomy does not hold is a tag somebody typed; a loose one is still a
        // product sold loose, and is named after its tag
        if (name == null && !isLoose) return@mapNotNull null
        listOf(
            category,
            name?.text("fr").orEmpty(),
            name?.text("en") ?: category.substringAfter(':').replace('-', ' '),
            per,
            String.format(Locale.ROOT, "%.2f", values.sorted().median()),
            values.size.toString(),
        )
    }

    val output = File("backend/src/main/resources/open-prices/prices-fr.tsv")
    output.parentFile.mkdirs()
    output.bufferedWriter().use { out ->
        out.write("# Open Prices, by Open Food Facts (prices.openfoodfacts.org). Open Database License (ODbL) 1.0.\n")
        out.write("# Median prices paid in France from $since to $until. Written by backend/src/test/kotlin/ExtractOpenPrices.kt.\n")
        out.write(listOf("category", "name_fr", "name_en", "per", "price", "prices").joinToString("\t"))
        out.write("\n")
        rows.forEach { out.write(it.joinToString("\t") { cell -> cell.replace('\t', ' ') }); out.write("\n") }
    }
    println("${rows.size} categories written to $output")
}

/** Fewer prices than this and a median is one shop's label. */
private const val MIN_PRICES = 5

private class Pack(val per: String, val quantity: Double, val categories: List<String>)

/** Prices by category, then by what they are per. */
private class Samples {
    private val values = mutableMapOf<String, MutableMap<String, MutableList<Double>>>()

    fun add(category: String, per: String, price: Double) {
        values.getOrPut(category) { mutableMapOf() }.getOrPut(per) { mutableListOf() } += price
    }

    fun categories(): Set<String> = values.keys

    /** The way [category] is most often priced, if that is often enough to take a median of. */
    fun best(category: String): Pair<String, List<Double>>? =
        values[category]?.maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= MIN_PRICES }
            ?.toPair()
}

private fun List<Double>.median(): Double =
    if (size % 2 == 1) this[size / 2] else (this[size / 2 - 1] + this[size / 2]) / 2

private fun jsonLines(file: File): Sequence<JsonObject> =
    GZIPInputStream(file.inputStream()).bufferedReader().lineSequence()
        .filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

private fun JsonObject.text(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content

private fun JsonObject.long(key: String): Long? = text(key)?.toLongOrNull()

private fun JsonElement?.strings(): List<String> =
    this?.takeIf { it !is JsonNull }?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
