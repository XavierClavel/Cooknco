package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
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
import shared.enums.UnitSystem
import shared.infodto.CookbookInfo
import shared.infodto.RecipeInfo
import shared.infodto.RecipeIngredientInfo
import shared.utils.URL.COOKBOOK_VIEW_URL
import shared.utils.URL.RECIPE_VIEW_URL
import shared.utils.UnitConversion
import java.text.Normalizer

/**
 * Renders a recipe as the printable sheet behind the export button, and a cookbook as a
 * whole book of them.
 *
 * The documents themselves are HTML — a layout from [PdfTemplateService], filled in with
 * the values below and printed by [PdfRenderer]. Nothing here decides what a page looks
 * like, which is the point: the look is an operator's to change from the backoffice, and
 * this only decides what a layout has to work with.
 *
 * The two kinds share one set of names: a recipe inside a book is described by exactly the
 * values the single sheet is, so an operator who has written one layout can read the other.
 */
class ExportService: KoinComponent {
    private val imageService: ImageService by inject()
    private val defaultImageService: DefaultImageService by inject()
    private val pdfTemplateService: PdfTemplateService by inject()
    private val pdfRenderer: PdfRenderer by inject()
    private val recipeService: RecipeService by inject()
    private val configuration: Configuration by inject()

    companion object {
        /** Combining marks, which is what an accent decomposes into under NFD. */
        private val DIACRITICS = Regex("\\p{Mn}+")
        private val NOT_SLUG = Regex("[^a-z0-9]+")

        /** The names a layout refers to these two by. Sent as files beside the document. */
        private const val PHOTO = "photo.jpg"
        private const val FONT = "Roboto-Regular.ttf"

        /** The book's own picture. Its recipes are named one by one, by [photoNameOf]. */
        private const val COVER = "cover.jpg"

        const val SITE_NAME = "Cook&Co"

        /**
         * What a recipe's picture is called inside a cookbook.
         *
         * Named after the recipe rather than its position, so two recipes cannot collide
         * and reordering the book changes nothing about which file is which.
         */
        private fun photoNameOf(recipeId: Long) = "recipe-$recipeId.jpg"
    }

    /** The font the packaged layouts declare. Read once: it is the same bytes every time. */
    private val robotoFont = ExportService::class.java.getResource("/fonts/$FONT")!!.readBytes()

    /**
     * The sheet for a recipe, as PDF.
     *
     * @param unitSystem which ladder to print the amounts on. A parameter rather than a
     *   lookup of whoever asked: a sheet is a thing to print and hand over, and the export
     *   is admin-only, so what it should read in is the caller's to say — the same reason
     *   [locale] is one.
     * @param body the layout to use, or null for the one in service. The backoffice passes
     *   the draft sitting in its editor, so a preview shows what an operator is about to
     *   save rather than what is saved.
     */
    suspend fun generatePDF(
        recipe: RecipeInfo,
        locale: Locale,
        unitSystem: UnitSystem = UnitSystem.DEFAULT,
        body: String? = null,
    ): ByteArray {
        val photo = photoOf(recipe)
        val template = body ?: pdfTemplateService.bodyOf(PdfDocumentKind.RECIPE, locale)
        val html = PdfTemplates.render(template, modelOf(recipe, locale, unitSystem, photo != null))

        return pdfRenderer.render(
            html = html,
            assets = buildMap {
                put(FONT, robotoFont)
                photo?.let { put(PHOTO, it) }
            },
        )
    }

    /**
     * The whole cookbook, as one PDF: a cover, then every recipe it holds.
     *
     * Refused rather than shortened past [Configuration.Pdf.maxCookbookRecipes] — see that
     * field for why the bound exists and why it is a refusal.
     *
     * Every recipe's picture is posted alongside the document under its own name, from the
     * thumbnail bucket rather than the full-size one: a book prints a recipe's picture at a
     * fraction of the page, and a hundred full-size photographs is tens of megabytes to
     * push through the renderer for pixels nobody sees.
     *
     * @param body the layout to use, or null for the one in service. See [generatePDF].
     */
    suspend fun generateCookbookPDF(
        cookbook: CookbookInfo,
        locale: Locale,
        unitSystem: UnitSystem = UnitSystem.DEFAULT,
        body: String? = null,
    ): ByteArray {
        val max = configuration.pdf.maxCookbookRecipes
        // One more than the bound, so the same read that fetches the book also says whether
        // it is over it.
        val recipes = recipeService.findByCookbook(cookbook.id, max + 1)
        if (recipes.size > max) throw BadRequestException(BadRequestCause.COOKBOOK_TOO_LARGE_TO_EXPORT)

        val cover = pictureOf(ImageBucket.COOKBOOK, cookbook.id, cookbook.version, "cookbook ${cookbook.id}")
        val photos = recipes.associate { recipe ->
            recipe.id to pictureOf(
                ImageBucket.RECIPE_THUMBNAIL,
                recipe.id,
                recipe.imageVersion,
                "recipe ${recipe.id}",
            )
        }

        val template = body ?: pdfTemplateService.bodyOf(PdfDocumentKind.COOKBOOK, locale)
        val html = PdfTemplates.render(
            template,
            cookbookModelOf(
                cookbook,
                recipes.map { it.toInfo(locale) },
                locale,
                unitSystem,
                hasCover = cover != null,
                photos = photos,
            ),
        )

        return pdfRenderer.render(
            html = html,
            assets = buildMap {
                put(FONT, robotoFont)
                cover?.let { put(COVER, it) }
                photos.forEach { (id, bytes) -> bytes?.let { put(photoNameOf(id), it) } }
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
    fun filenameOf(recipe: RecipeInfo): String = filename(recipe.title, "recipe-${recipe.id}")

    /** The same, for a book. See [filenameOf]. */
    fun filenameOf(cookbook: CookbookInfo): String = filename(cookbook.title, "cookbook-${cookbook.id}")

    private fun filename(title: String, fallback: String): String {
        val slug = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NOT_SLUG, "-")
            .trim('-')
            .take(60)
            .trim('-')
        return "${slug.ifEmpty { fallback }}.pdf"
    }

    // -------------------------------------------------------------------- values

    /**
     * Everything a recipe sheet may name, and nothing else: a name absent from here prints
     * as nothing, which is why [PdfDocumentKind.RECIPE] lists exactly these.
     */
    private fun modelOf(
        recipe: RecipeInfo,
        locale: Locale,
        unitSystem: UnitSystem,
        hasPhoto: Boolean,
    ): Map<String, Any?> =
        recipeValues(recipe, locale, unitSystem, if (hasPhoto) PHOTO else null) +
            mapOf(PdfVariable.SITE_NAME to SITE_NAME)

    /**
     * Everything a book may name.
     *
     * Its recipes are the same maps the single sheet is rendered from, one per pass of the
     * `{{#recipes}}` section. Mustache resolves a name against the innermost context that
     * has it, so `{{title}}` inside that section is the recipe's and outside it is the
     * book's — which is what [PdfDocumentKind.COOKBOOK] documents and the packaged layout
     * shows both halves of.
     */
    private fun cookbookModelOf(
        cookbook: CookbookInfo,
        recipes: List<RecipeInfo>,
        locale: Locale,
        unitSystem: UnitSystem,
        hasCover: Boolean,
        photos: Map<Long, ByteArray?>,
    ): Map<String, Any?> = mapOf(
        PdfVariable.TITLE to cookbook.title,
        PdfVariable.DESCRIPTION to cookbook.description,
        PdfVariable.COVER to if (hasCover) COVER else null,
        PdfVariable.RECIPE_COUNT to recipes.size,

        PdfVariable.HAS_RECIPES to recipes.isNotEmpty(),
        PdfVariable.RECIPES to recipes.map { recipe ->
            recipeValues(
                recipe,
                locale,
                unitSystem,
                photo = photoNameOf(recipe.id).takeIf { photos[recipe.id] != null },
            )
        },

        PdfVariable.URL to "${configuration.frontend.url.trimEnd('/')}/$COOKBOOK_VIEW_URL?cookbook=${cookbook.id}",
        PdfVariable.SITE_NAME to SITE_NAME,
    )

    /**
     * One recipe, as the names a layout reads it by.
     *
     * Values go in raw — Mustache escapes them on the way out — so a recipe titled with a
     * tag prints the tag rather than applying it.
     *
     * @param photo the name the picture was posted under, or null when there is none to
     *   show, which is what makes the layout's `{{#photo}}` section drop out
     */
    private fun recipeValues(
        recipe: RecipeInfo,
        locale: Locale,
        unitSystem: UnitSystem,
        photo: String?,
    ): Map<String, Any?> {
        val units = UnitLabels.of(locale, unitSystem)
        return mapOf(
            PdfVariable.TITLE to recipe.title,
            PdfVariable.DESCRIPTION to recipe.description,
            PdfVariable.AUTHOR to recipe.owner.username,
            PdfVariable.PHOTO to photo,

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
                // The step's duration is deliberately not offered to the layout. A printed
                // sheet has no timer to start, the wording already says how long it takes,
                // and a value a layout may name has to be declared in
                // PdfDocumentKind.RECIPE.variables as well as here.
                mapOf("index" to index + 1, "text" to step.text)
            },

            PdfVariable.TIPS to recipe.tips,

            PdfVariable.URL to "${configuration.frontend.url.trimEnd('/')}/$RECIPE_VIEW_URL?id=${recipe.id}",
        )
    }

    private fun photoOf(recipe: RecipeInfo): ByteArray? =
        pictureOf(ImageBucket.RECIPE, recipe.id, recipe.version, "recipe ${recipe.id}")

    /**
     * An entity's picture as JPEG, falling back to the bucket's default: `imageVersion` is
     * 0 until someone uploads one, and having no picture is no reason to refuse an export.
     *
     * Decoding is the one step here that depends on bytes nobody validated since they were
     * written, and on a native webp codec, so any failure costs the picture and not the whole
     * document — the layout's `{{#photo}}` section simply drops out. `LinkageError` is caught
     * alongside the exceptions for the codec's sake: a native library missing or built for
     * another architecture is an environment fault, and a sheet is still worth having
     * without its picture.
     *
     * @param what how the failure names its subject in the log; ids alone read the same for
     *   a recipe, its thumbnail and a cookbook
     */
    private fun pictureOf(bucket: ImageBucket, id: Long, version: Long, what: String): ByteArray? = try {
        val webp = imageService.findImage(bucket, id, version)
            ?: defaultImageService.read(bucket).first
        imageService.webpToJpeg(webp)
    } catch (e: Exception) {
        logger.error(e) { "Could not embed the image of $what in its export" }
        null
    } catch (e: LinkageError) {
        logger.error(e) { "Could not embed the image of $what in its export" }
        null
    }

    /**
     * The unit words a sheet needs, per locale, on the ladder the sheet was asked for.
     *
     * All that is left of the sheet's wording: every heading now lives in the layout, which
     * is written per locale. These stay in code because they are part of formatting a value
     * rather than text an operator would reword — and because the clients format amounts the
     * same way, from the same rules.
     */
    private class UnitLabels(
        val unitSystem: UnitSystem,
        val teaspoons: String,
        val tablespoons: String,
        val cups: String,
    ) {
        companion object {
            fun of(locale: Locale, unitSystem: UnitSystem) = when (locale) {
                Locale.EN -> UnitLabels(unitSystem, teaspoons = "tsp", tablespoons = "tbsp", cups = "cups")
                Locale.FR -> UnitLabels(unitSystem, teaspoons = "c. à café", tablespoons = "c. à soupe", cups = "tasses")
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
         * Mirrors what the clients display (`formatAmount` in the web app): the amount goes
         * on the reader's ladder — which is also what rolls grams and millilitres up to the
         * larger unit — and a whole number keeps no decimals.
         */
        private fun formatAmount(amount: Float, unit: AmountUnit): String {
            val (scaled, scaledUnit) = UnitConversion.displayIn(amount, unit, unitSystem)
            val rounded = "%.2f".format(java.util.Locale.ROOT, scaled).trimEnd('0').trimEnd('.')
            return "$rounded${symbolOf(scaledUnit)}"
        }

        private fun symbolOf(unit: AmountUnit) = when (unit) {
            AmountUnit.NONE, AmountUnit.UNIT -> ""
            AmountUnit.GRAM -> "g"
            AmountUnit.KILOGRAM -> "kg"
            AmountUnit.OUNCE -> "oz"
            AmountUnit.POUND -> "lb"
            AmountUnit.MILLILITERS -> "mL"
            AmountUnit.CENTILITER -> "cL"
            AmountUnit.LITER -> "L"
            AmountUnit.FLUID_OUNCE -> " fl oz"
            AmountUnit.TEASPOON -> " $teaspoons"
            AmountUnit.TABLESPOON -> " $tablespoons"
            AmountUnit.CUP -> " $cups"
        }
    }
}
