package com.xavierclavel.services

import com.xavierclavel.utils.Configuration
import com.xavierclavel.utils.PdfTemplates
import com.xavierclavel.utils.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.enums.AmountUnit
import shared.enums.ImageBucket
import shared.enums.Locale
import shared.enums.PdfDocumentKind
import shared.enums.PdfVariable
import shared.infodto.RecipeInfo
import shared.infodto.RecipeIngredientInfo
import shared.utils.URL.RECIPE_VIEW_URL
import java.text.Normalizer

/**
 * Renders a recipe as the printable sheet behind the export button.
 *
 * The sheet itself is HTML — a layout from [PdfTemplateService], filled in with the values
 * below and printed by [PdfRenderer]. Nothing here decides what the page looks like, which
 * is the point: the look is an operator's to change from the backoffice, and this only
 * decides what a layout has to work with.
 */
class ExportService: KoinComponent {
    private val imageService: ImageService by inject()
    private val defaultImageService: DefaultImageService by inject()
    private val pdfTemplateService: PdfTemplateService by inject()
    private val pdfRenderer: PdfRenderer by inject()
    private val configuration: Configuration by inject()

    companion object {
        /** Combining marks, which is what an accent decomposes into under NFD. */
        private val DIACRITICS = Regex("\\p{Mn}+")
        private val NOT_SLUG = Regex("[^a-z0-9]+")

        /** The names a layout refers to these two by. Sent as files beside the document. */
        private const val PHOTO = "photo.jpg"
        private const val FONT = "Roboto-Regular.ttf"

        const val SITE_NAME = "Cook&Co"
    }

    /** The font the packaged layouts declare. Read once: it is the same bytes every time. */
    private val robotoFont = ExportService::class.java.getResource("/fonts/$FONT")!!.readBytes()

    /**
     * The sheet for a recipe, as PDF.
     *
     * @param body the layout to use, or null for the one in service. The backoffice passes
     *   the draft sitting in its editor, so a preview shows what an operator is about to
     *   save rather than what is saved.
     */
    suspend fun generatePDF(recipe: RecipeInfo, locale: Locale, body: String? = null): ByteArray {
        val photo = photoOf(recipe)
        val template = body ?: pdfTemplateService.bodyOf(PdfDocumentKind.RECIPE, locale)
        val html = PdfTemplates.render(template, modelOf(recipe, locale, photo != null))

        return pdfRenderer.render(
            html = html,
            assets = buildMap {
                put(FONT, robotoFont)
                photo?.let { put(PHOTO, it) }
            },
        )
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

    // -------------------------------------------------------------------- values

    /**
     * Everything a layout may name, and nothing else: a name absent from here prints as
     * nothing, which is why [PdfDocumentKind.RECIPE] lists exactly these.
     *
     * Values go in raw — Mustache escapes them on the way out — so a recipe titled with a
     * tag prints the tag rather than applying it.
     */
    private fun modelOf(recipe: RecipeInfo, locale: Locale, hasPhoto: Boolean): Map<String, Any?> {
        val units = UnitLabels.of(locale)
        return mapOf(
            PdfVariable.TITLE to recipe.title,
            PdfVariable.DESCRIPTION to recipe.description,
            PdfVariable.AUTHOR to recipe.owner.username,
            PdfVariable.PHOTO to if (hasPhoto) PHOTO else null,

            PdfVariable.YIELD to recipe.yield,
            PdfVariable.PREPARATION_TIME to recipe.preparationTime,
            PdfVariable.COOKING_TIME to recipe.cookingTime,
            PdfVariable.COOKING_TEMPERATURE to recipe.cookingTemperature,

            PdfVariable.HAS_INGREDIENTS to recipe.ingredients.isNotEmpty(),
            PdfVariable.INGREDIENTS to recipe.ingredients.map {
                mapOf(
                    "amount" to units.amountOf(it),
                    "name" to it.name,
                    "complement" to it.complement.orEmpty(),
                    // The three above run together, for a layout that wants one line
                    "text" to units.format(it),
                )
            },

            PdfVariable.HAS_STEPS to recipe.steps.isNotEmpty(),
            PdfVariable.STEPS to recipe.steps.mapIndexed { index, step ->
                mapOf("index" to index + 1, "text" to step)
            },

            PdfVariable.TIPS to recipe.tips,

            PdfVariable.URL to "${configuration.frontend.url.trimEnd('/')}/$RECIPE_VIEW_URL?id=${recipe.id}",
            PdfVariable.SITE_NAME to SITE_NAME,
        )
    }

    /**
     * The recipe's picture as JPEG, falling back to the bucket's default: `imageVersion` is
     * 0 until someone uploads one, and a recipe having no picture is no reason to refuse its
     * export.
     *
     * Decoding is the one step here that depends on bytes nobody validated since they were
     * written, and on a native webp codec, so any failure costs the picture and not the whole
     * sheet — the layout's `{{#photo}}` section simply drops out. `LinkageError` is caught
     * alongside the exceptions for the codec's sake: a native library missing or built for
     * another architecture is an environment fault, and a recipe sheet is still worth having
     * without its picture.
     */
    private fun photoOf(recipe: RecipeInfo): ByteArray? = try {
        val webp = imageService.findRecipeImage(recipe.id, recipe.version)
            ?: defaultImageService.read(ImageBucket.RECIPE).first
        imageService.webpToJpeg(webp)
    } catch (e: Exception) {
        logger.error(e) { "Could not embed the image of recipe ${recipe.id} in its export" }
        null
    } catch (e: LinkageError) {
        logger.error(e) { "Could not embed the image of recipe ${recipe.id} in its export" }
        null
    }

    /**
     * The unit words a sheet needs, per locale.
     *
     * All that is left of the sheet's wording: every heading now lives in the layout, which
     * is written per locale. These stay in code because they are part of formatting a value
     * rather than text an operator would reword — and because the clients format amounts the
     * same way, from the same rules.
     */
    private class UnitLabels(
        val teaspoons: String,
        val tablespoons: String,
        val cups: String,
    ) {
        companion object {
            fun of(locale: Locale) = when (locale) {
                Locale.EN -> UnitLabels(teaspoons = "tsp", tablespoons = "tbsp", cups = "cups")
                Locale.FR -> UnitLabels(teaspoons = "c. à café", tablespoons = "c. à soupe", cups = "tasses")
            }
        }

        /** `250g flour (sifted)` — an amount of nothing, or of no unit, simply drops out. */
        fun format(ingredient: RecipeIngredientInfo): String = listOfNotNull(
            amountOf(ingredient).takeIf { it.isNotEmpty() },
            ingredient.name,
            ingredient.complement?.takeIf { it.isNotBlank() }?.let { "($it)" },
        ).joinToString(" ")

        /** Just the `250g`, so a layout can put amounts in a column of their own. */
        fun amountOf(ingredient: RecipeIngredientInfo): String =
            ingredient.amount?.takeIf { it > 0f }?.let { formatAmount(it, ingredient.unit) } ?: ""

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
