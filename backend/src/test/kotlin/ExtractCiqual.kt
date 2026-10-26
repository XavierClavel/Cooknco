package main

import java.io.File
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Turns the Ciqual table as Anses publishes it into the file the backend bundles,
 * `backend/src/main/resources/ciqual/ciqual-<edition>.tsv`.
 *
 * Run with `sh ./gradlew :backend:extractCiqual --args="<directory> <edition>"`, where the
 * directory holds the XML files downloaded from the edition's recherche.data.gouv.fr page
 * (`alim_*.xml`, `alim_grp_*.xml`, `compo_*.xml`). The XML is read rather than the spreadsheet
 * because the spreadsheet names every food in French only.
 *
 * Nothing is interpreted here: a value is written exactly as published — `12,5`, `traces`,
 * `< 0,5`, `-` — and reading it is [com.xavierclavel.services.CiqualService]'s job, where the
 * test suite holds it. This only drops the 64 constituents nobody reads and the 66 MB that
 * carries them.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: extractCiqual <directory with the Ciqual XML files> <edition>" }
    val directory = File(args[0])
    val edition = args[1].toInt()

    fun published(prefix: String): File = directory.listFiles()!!
        .singleOrNull { Regex("""${prefix}(_[\d_]+)?\.xml""").matches(it.name) }
        ?: error("no single $prefix*.xml in $directory")

    val groups = readRecords(published("alim_grp"), "ALIM_GRP")
        .associate { it.getValue("alim_ssgrp_code") to (it["alim_ssgrp_nom_fr"] to it["alim_ssgrp_nom_eng"]) }
    val foods = readRecords(published("alim"), "ALIM").sortedBy { it.getValue("alim_code").toInt() }

    val values = mutableMapOf<String, MutableMap<String, String>>()
    readRecords(published("compo"), "COMPO") { it["const_code"] in CONSTITUENTS.values }
        .forEach { values.getOrPut(it.getValue("alim_code")) { mutableMapOf() }[it.getValue("const_code")] = it["teneur"] ?: "-" }

    val output = File("backend/src/main/resources/ciqual/ciqual-$edition.tsv")
    output.parentFile.mkdirs()
    output.bufferedWriter().use { out ->
        out.write("# Anses. $edition. Table de composition nutritionnelle des aliments Ciqual.\n")
        out.write("# Licence Ouverte / Open Licence 2.0 (Etalab). Written by backend/src/test/kotlin/ExtractCiqual.kt.\n")
        out.write((listOf("code", "name_fr", "name_en", "group_fr", "group_en") + CONSTITUENTS.keys).joinToString("\t"))
        out.write("\n")
        for (food in foods) {
            val code = food.getValue("alim_code")
            val group = groups[food["alim_ssgrp_code"]]
            val row = listOf(code, food["alim_nom_fr"], food["alim_nom_eng"], group?.first, group?.second) +
                CONSTITUENTS.values.map { values[code]?.get(it) ?: "-" }
            out.write(row.joinToString("\t") { it.orEmpty() })
            out.write("\n")
        }
    }
    println("${foods.size} foods written to $output")
}

/** The columns written, by the Ciqual constituent code each is read from. */
private val CONSTITUENTS = linkedMapOf(
    "energy_kcal" to "328",          // Energie, Règlement UE N° 1169/2011 (kcal/100 g)
    "proteins" to "25000",           // Protéines, N x facteur de Jones (g/100 g)
    "carbohydrates" to "31000",      // Glucides (g/100 g)
    "sugars" to "32000",             // Sucres (g/100 g)
    "fibers" to "34100",             // Fibres alimentaires (g/100 g)
    "saturated_fat" to "40302",      // AG saturés (g/100 g)
    "monounsaturated_fat" to "40303", // AG monoinsaturés (g/100 g)
    "polyunsaturated_fat" to "40304", // AG polyinsaturés (g/100 g)
    "cholesterol_mg" to "75100",     // Cholestérol (mg/100 g)
    "salt" to "10004",               // Sel chlorure de sodium (g/100 g)
)

/**
 * Every `<[element]>` record of a Ciqual file as field name to trimmed text, whitespace
 * collapsed — names in the published files carry line breaks. A field marked
 * `missing=" "` reads as absent.
 */
private fun readRecords(
    file: File,
    element: String,
    keep: (Map<String, String>) -> Boolean = { true },
): List<Map<String, String>> {
    val reader = XMLInputFactory.newInstance().createXMLStreamReader(file.inputStream())
    val records = mutableListOf<Map<String, String>>()
    var record: MutableMap<String, String>? = null
    var field: String? = null
    val text = StringBuilder()
    while (reader.hasNext()) {
        when (reader.next()) {
            XMLStreamConstants.START_ELEMENT -> when {
                reader.localName == element -> record = mutableMapOf()
                record != null -> { field = reader.localName; text.clear() }
            }
            XMLStreamConstants.CHARACTERS -> if (field != null) text.append(reader.text)
            XMLStreamConstants.END_ELEMENT -> when {
                reader.localName == element -> { record?.let { if (keep(it)) records += it }; record = null }
                reader.localName == field -> {
                    val value = text.trim().split(Regex("\\s+")).joinToString(" ")
                    if (value.isNotEmpty()) record!![field!!] = value
                    field = null
                }
            }
        }
    }
    reader.close()
    return records
}
