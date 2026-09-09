package com.xavierclavel.services

import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.xavierclavel.utils.logger
import shared.enums.AmountUnit
import shared.enums.ImageBucket
import shared.enums.Locale
import shared.infodto.RecipeInfo
import shared.infodto.RecipeIngredientInfo
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import java.text.Normalizer

/** Renders a recipe as the printable sheet behind the export button. */
class ExportService: KoinComponent {
    private val imageService: ImageService by inject()
    private val defaultImageService: DefaultImageService by inject()

    private val robotoFont = ExportService::class.java.getResource("/fonts/Roboto-Regular.ttf")!!.readBytes()
    private val titleColor = DeviceRgb(255, 111, 89)

    companion object {
        /** Combining marks, which is what an accent decomposes into under NFD. */
        private val DIACRITICS = Regex("\\p{Mn}+")
        private val NOT_SLUG = Regex("[^a-z0-9]+")
    }

    fun generatePDF(recipe: RecipeInfo, locale: Locale): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val font = PdfFontFactory.createFont(robotoFont, PdfEncodings.IDENTITY_H)

        PdfWriter(outputStream).use { writer ->
            val document = Document(PdfDocument(writer))
            document.setFont(font)
            document.writeRecipe(recipe, Labels.of(locale))
            document.close()
        }

        return outputStream.toByteArray()
    }

    /**
     * What the browser saves the export as: the recipe's title, slugged.
     *
     * A title is free text in any language, and it reaches the client through a
     * `Content-Disposition` header, so it is reduced to ASCII letters, digits and dashes
     * rather than sent as it was typed. A title that survives none of that — emoji only,
     * say — falls back to the id, because a nameless download is worse than an ugly one.
     */
    fun filenameOf(recipe: RecipeInfo): String {
        val slug = Normalizer.normalize(recipe.title.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NOT_SLUG, "-")
            .trim('-')
            .take(60)
            .trim('-')
        return "${slug.ifEmpty { "recipe-${recipe.id}" }}.pdf"
    }

    private fun Document.writeRecipe(recipe: RecipeInfo, labels: Labels) {
        addTitle(recipe.title)
        addParagraph("${labels.by} ${recipe.owner.username}")
        addImage(recipe)
        addParagraph(recipe.description)

        listOfNotNull(
            recipe.yield?.let { "${labels.servings}: $it" },
            recipe.preparationTime?.let { "${labels.preparation}: $it ${labels.minutes}" },
            recipe.cookingTime?.let { "${labels.cooking}: $it ${labels.minutes}" },
            recipe.cookingTemperature?.let { "${labels.temperature}: $it °C" },
        ).forEach { addParagraph(it) }

        if (recipe.ingredients.isNotEmpty()) {
            addSectionTitle(labels.ingredients)
            recipe.ingredients.forEach { addParagraph("• ${labels.format(it)}") }
        }

        if (recipe.steps.isNotEmpty()) {
            addSectionTitle(labels.steps)
            recipe.steps.forEachIndexed { index, step -> addParagraph("${index + 1}. $step") }
        }

        if (recipe.tips.isNotBlank()) {
            addSectionTitle(labels.tips)
            addParagraph(recipe.tips)
        }
    }

    /**
     * The recipe's picture, falling back to the bucket's default: `imageVersion` is 0 until
     * someone uploads one, and a recipe having no picture is no reason to refuse its export.
     *
     * Decoding and embedding is the one step here that depends on bytes nobody validated
     * since they were written, and on a native webp codec, so any failure costs the picture
     * and not the whole sheet. `LinkageError` is caught alongside the exceptions for the
     * codec's sake: a native library missing or built for another architecture is an
     * environment fault, and a recipe sheet is still worth having without its picture.
     *
     * `setAutoScale` is what keeps a 1600x1200 upload on the page: iText lays an image out
     * at its pixel size otherwise, which runs off an A4 sheet.
     */
    private fun Document.addImage(recipe: RecipeInfo) {
        val image = try {
            val webp = imageService.findRecipeImage(recipe.id, recipe.version)
                ?: defaultImageService.read(ImageBucket.RECIPE).first
            Image(ImageDataFactory.create(imageService.webpToJpeg(webp)))
        } catch (e: Exception) {
            logger.error(e) { "Could not embed the image of recipe ${recipe.id} in its export" }
            return
        } catch (e: LinkageError) {
            logger.error(e) { "Could not embed the image of recipe ${recipe.id} in its export" }
            return
        }
        add(image.setAutoScale(true))
    }

    private fun Document.addTitle(title: String) = this.add(Paragraph(
        Text(title)
            .setFontColor(titleColor)
            .setFontSize(17f)
            .setBold()
    ))

    /** Blank fields are skipped rather than printed as an empty line. */
    private fun Document.addParagraph(text: String) {
        if (text.isBlank()) return
        add(Paragraph(text))
    }

    private fun Document.addSectionTitle(title: String) = this.add(Paragraph(
        Text(title)
            .setFontColor(titleColor)
            .setFontSize(14f)
            .setBold()
    ))

    /**
     * The wording of an exported sheet, per locale.
     *
     * The backend has no general translation catalogue — mails carry their own templates,
     * and every other string a client shows is translated by that client — so the handful
     * of words a recipe sheet needs live here.
     */
    private class Labels(
        val by: String,
        val servings: String,
        val preparation: String,
        val cooking: String,
        val temperature: String,
        val ingredients: String,
        val steps: String,
        val tips: String,
        val minutes: String,
        val teaspoons: String,
        val tablespoons: String,
        val cups: String,
    ) {
        companion object {
            fun of(locale: Locale) = when (locale) {
                Locale.EN -> Labels(
                    by = "By",
                    servings = "Servings",
                    preparation = "Preparation",
                    cooking = "Cooking",
                    temperature = "Temperature",
                    ingredients = "Ingredients",
                    steps = "Steps",
                    tips = "Tips",
                    minutes = "min",
                    teaspoons = "tsp",
                    tablespoons = "tbsp",
                    cups = "cups",
                )
                Locale.FR -> Labels(
                    by = "Par",
                    servings = "Portions",
                    preparation = "Préparation",
                    cooking = "Cuisson",
                    temperature = "Température",
                    ingredients = "Ingrédients",
                    steps = "Étapes",
                    tips = "Astuces",
                    minutes = "min",
                    teaspoons = "c. à café",
                    tablespoons = "c. à soupe",
                    cups = "tasses",
                )
            }
        }

        /** `250g flour (sifted)` — an amount of nothing, or of no unit, simply drops out. */
        fun format(ingredient: RecipeIngredientInfo): String = listOfNotNull(
            ingredient.amount?.takeIf { it > 0f }?.let { formatAmount(it, ingredient.unit) },
            ingredient.name,
            ingredient.complement?.takeIf { it.isNotBlank() }?.let { "($it)" },
        ).joinToString(" ")

        /**
         * Mirrors what the clients display (`formatAmount` in the web app): grams and
         * millilitres roll up to the larger unit once they reach a thousand, and a whole
         * number keeps no decimals.
         */
        private fun formatAmount(amount: Float, unit: AmountUnit): String {
            val (scaled, scaledUnit) = when {
                unit == AmountUnit.GRAM && amount >= 1_000f -> amount / 1_000f to AmountUnit.KILOGRAM
                unit == AmountUnit.MILLILITERS && amount >= 1_000f -> amount / 1_000f to AmountUnit.LITER
                else -> amount to unit
            }
            val rounded = "%.2f".format(java.util.Locale.ROOT, scaled).trimEnd('0').trimEnd('.')
            return "$rounded${symbolOf(scaledUnit)}"
        }

        private fun symbolOf(unit: AmountUnit) = when (unit) {
            AmountUnit.NONE, AmountUnit.UNIT -> ""
            AmountUnit.GRAM -> "g"
            AmountUnit.KILOGRAM -> "kg"
            AmountUnit.POUND -> "lb"
            AmountUnit.MILLILITERS -> "mL"
            AmountUnit.CENTILITER -> "cL"
            AmountUnit.LITER -> "L"
            AmountUnit.TEASPOON -> " $teaspoons"
            AmountUnit.TABLESPOON -> " $tablespoons"
            AmountUnit.CUP -> " $cups"
        }
    }
}
