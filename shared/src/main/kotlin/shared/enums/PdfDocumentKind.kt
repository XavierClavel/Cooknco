package shared.enums

/**
 * A PDF the application knows how to produce.
 *
 * The mirror of [EmailTemplateKind], with one deliberate difference: there are no operator
 * added kinds. A wording only has to reach an inbox, so a mail nobody sends yet is harmless;
 * a document has to be *filled in* by code that knows what its variables mean, so a kind
 * with no producer behind it could never render anything.
 */
enum class PdfDocumentKind(val key: String, val variables: List<String>) {
    RECIPE(
        "recipe",
        listOf(
            PdfVariable.TITLE,
            PdfVariable.DESCRIPTION,
            PdfVariable.AUTHOR,
            PdfVariable.PHOTO,
            PdfVariable.YIELD,
            PdfVariable.PREPARATION_TIME,
            PdfVariable.COOKING_TIME,
            PdfVariable.COOKING_TEMPERATURE,
            PdfVariable.HAS_INGREDIENTS,
            PdfVariable.INGREDIENTS,
            PdfVariable.HAS_STEPS,
            PdfVariable.STEPS,
            PdfVariable.TIPS,
            PdfVariable.URL,
            PdfVariable.SITE_NAME,
        ),
    ),

    /**
     * A whole cookbook, printed as one book: a cover, then every recipe in it.
     *
     * Its variables come in two groups, and the list below is their union because that is
     * what the backoffice offers as chips. The first eight are the book's own; everything
     * after [PdfVariable.RECIPES] resolves only *inside* that section, where it names the
     * recipe being printed. [PdfVariable.TITLE] and [PdfVariable.DESCRIPTION] therefore
     * appear in both groups, and Mustache resolves the innermost — inside the section they
     * are the recipe's, outside they are the book's. The packaged layout shows both.
     */
    COOKBOOK(
        "cookbook",
        listOf(
            PdfVariable.TITLE,
            PdfVariable.DESCRIPTION,
            PdfVariable.COVER,
            PdfVariable.RECIPE_COUNT,
            PdfVariable.URL,
            PdfVariable.SITE_NAME,
            PdfVariable.HAS_RECIPES,
            PdfVariable.RECIPES,

            // Inside {{#recipes}} only, where they name that recipe.
            PdfVariable.AUTHOR,
            PdfVariable.PHOTO,
            PdfVariable.YIELD,
            PdfVariable.PREPARATION_TIME,
            PdfVariable.COOKING_TIME,
            PdfVariable.COOKING_TEMPERATURE,
            PdfVariable.HAS_INGREDIENTS,
            PdfVariable.INGREDIENTS,
            PdfVariable.HAS_STEPS,
            PdfVariable.STEPS,
            PdfVariable.TIPS,
        ),
    ),
    ;

    /**
     * Packaged under `backend/src/main/resources`, unlike a mail's: the backend is the only
     * thing that renders a document, where a mail's wording is also read by mail-service.
     */
    fun resource(locale: Locale) = "/pdf/${key}_${locale.name.lowercase()}.html"

    companion object {
        fun of(key: String): PdfDocumentKind? = entries.find { it.key == key }
    }
}

/**
 * The names a document template may refer to.
 *
 * Its own object rather than constants on [PdfDocumentKind] for the same reason as
 * [MailPlaceholder]: an enum entry cannot read its own companion.
 *
 * A plain value is written `{{name}}`. A value that may be absent, and a list, are written
 * as a section — `{{#cookingTime}}…{{/cookingTime}}`, `{{#steps}}…{{/steps}}` — whose body
 * is dropped when there is nothing to show and repeated once per row for a list.
 */
object PdfVariable {
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val AUTHOR = "author"

    /** The recipe's picture, as the name of a file sent alongside: `<img src="{{photo}}">`. */
    const val PHOTO = "photo"

    const val YIELD = "yield"
    const val PREPARATION_TIME = "preparationTime"
    const val COOKING_TIME = "cookingTime"
    const val COOKING_TEMPERATURE = "cookingTemperature"

    /**
     * A section, one pass per ingredient, holding `text`, `amount`, `name` and `complement`.
     *
     * Wrap the heading and the list itself in [HAS_INGREDIENTS] rather than in this one: a
     * section over a list repeats its body per row, so anything meant to appear once — a
     * `<h2>`, the opening `<ul>` — has to sit outside it.
     */
    const val INGREDIENTS = "ingredients"
    const val HAS_INGREDIENTS = "hasIngredients"

    /** A section, one pass per step, holding `index` and `text`. See [HAS_INGREDIENTS]. */
    const val STEPS = "steps"
    const val HAS_STEPS = "hasSteps"

    const val TIPS = "tips"

    /** Where the recipe lives on the site, for a footer or a QR-less "read it online" line. */
    const val URL = "url"
    const val SITE_NAME = "siteName"

    // ----------------------------------------------------------------- cookbook

    /** The cookbook's own picture, as the name of a file sent alongside. See [PHOTO]. */
    const val COVER = "cover"

    /** How many recipes the book holds, for a cover line. */
    const val RECIPE_COUNT = "recipeCount"

    /**
     * A section, one pass per recipe, holding that recipe's own [TITLE], [PHOTO],
     * [INGREDIENTS], [STEPS] and the rest of the recipe names.
     *
     * Wrap anything meant to appear once — the heading over a table of contents — in
     * [HAS_RECIPES] rather than in this one, for the reason [HAS_INGREDIENTS] gives.
     */
    const val RECIPES = "recipes"
    const val HAS_RECIPES = "hasRecipes"
}
