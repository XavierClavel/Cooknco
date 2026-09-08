package com.xavierclavel.services

import com.xavierclavel.exceptions.ForbiddenException
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.User
import com.xavierclavel.utils.Configuration
import shared.enums.Locale
import shared.utils.URL.COOKBOOK_VIEW_URL
import shared.utils.URL.INGREDIENT_VIEW_URL
import shared.utils.URL.RECIPE_VIEW_URL
import shared.utils.URL.USER_VIEW_URL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** What a crawler shows for a shared link. */
data class LinkPreview(
    val title: String,
    val description: String,
    val imageUrl: String,
    val canonicalUrl: String,
    /** `og:type`. `article` for a recipe, `profile` for a member, `website` otherwise. */
    val type: String = "website",
)

/**
 * Renders the HTML document behind a shared link, so its preview carries the entity's own
 * picture, name and description.
 *
 * The document is the SPA's own `index.html` with the head block between the two
 * `<!--preview:*-->` markers replaced ([AppShellSource], `frontend/index.html`), which is
 * why a visitor following the link still lands in the app rather than on a stub: only the
 * tags a crawler reads before running any JavaScript differ.
 *
 * Every lookup here is made **as an anonymous visitor** — the entity services are handed a
 * null requestor id, so their visibility filters return exactly what the public may see. A
 * hidden recipe, a private cookbook or a banned member's profile therefore falls back to the
 * site defaults rather than leaking a title into a chat window.
 */
class LinkPreviewService: KoinComponent {
    private val configuration: Configuration by inject()
    private val shellSource: AppShellSource by inject()
    private val recipeService: RecipeService by inject()
    private val userService: UserService by inject()
    private val cookbookService: CookbookService by inject()
    private val ingredientService: IngredientService by inject()

    companion object {
        const val SITE_NAME = "Cook&Co"
        const val MARKER_START = "<!--preview:start-->"
        const val MARKER_END = "<!--preview:end-->"

        /** Facebook and LinkedIn cut around 300; the shorter limit is what Twitter shows. */
        private const val DESCRIPTION_LIMIT = 200
    }

    private val siteUrl get() = configuration.frontend.url.trimEnd('/')

    fun recipePreview(id: Long?, locale: Locale): LinkPreview =
        entityPreview(id, RECIPE_VIEW_URL, "id", locale, { recipeService.getById(null, it, locale) }) {
            LinkPreview(
                title = it.title,
                description = it.description.ifBlank { defaultDescription(locale) },
                imageUrl = imageUrl("recipes", it.id, it.version),
                canonicalUrl = viewUrl(RECIPE_VIEW_URL, "id", it.id),
                type = "article",
            )
        }

    fun userPreview(id: Long?, locale: Locale): LinkPreview =
        entityPreview(id, USER_VIEW_URL, "user", locale, { publicUser(it) }) {
            LinkPreview(
                title = it.username,
                description = it.bio.ifBlank { defaultDescription(locale) },
                imageUrl = imageUrl("users", it.id, it.imageVersion),
                canonicalUrl = viewUrl(USER_VIEW_URL, "user", it.id),
                type = "profile",
            )
        }

    fun cookbookPreview(id: Long?, locale: Locale): LinkPreview =
        entityPreview(id, COOKBOOK_VIEW_URL, "cookbook", locale, { cookbookService.getCookbook(it, null) }) {
            LinkPreview(
                title = it.title,
                description = it.description.ifBlank { defaultDescription(locale) },
                imageUrl = imageUrl("cookbooks", it.id, it.version),
                canonicalUrl = viewUrl(COOKBOOK_VIEW_URL, "cookbook", it.id),
            )
        }

    fun ingredientPreview(id: Long?, locale: Locale): LinkPreview =
        entityPreview(id, INGREDIENT_VIEW_URL, "ingredient", locale, { ingredientService.findById(it) }) {
            LinkPreview(
                title = it.name[locale] ?: it.name[Locale.EN] ?: SITE_NAME,
                description = defaultDescription(locale),
                // Ingredient pictures are chosen by type and ship with the app rather than
                // living on the image volume (`getIngredientImageUrl`), and every type points
                // at the same placeholder today.
                imageUrl = "$siteUrl/ingredients/vegetable.png",
                canonicalUrl = viewUrl(INGREDIENT_VIEW_URL, "ingredient", it.id),
            )
        }

    /**
     * A member the public may see. [UserService.getUser] answers for anyone, including banned
     * accounts and those who asked not to be public, so the check the recipe and cookbook
     * services do for themselves has to happen here.
     *
     * The entity rather than its `UserInfo`: `toInfo()` counts five lazy collections to fill
     * in numbers a preview never shows.
     */
    private fun publicUser(id: Long): User? =
        userService.findEntityById(id)
            ?.takeIf { !it.isBanned && it.isAccountPublic }

    /**
     * Looks an entity up and describes it, falling back to the site defaults when it does not
     * exist or is not ours to show. The services throw [com.xavierclavel.exceptions.NotFoundException]
     * and [com.xavierclavel.exceptions.ForbiddenException] for those two cases; neither is worth
     * an error page, because the visitor following the link needs the app to load and say so
     * itself.
     */
    private fun <T> entityPreview(
        id: Long?,
        route: String,
        idParam: String,
        locale: Locale,
        find: (Long) -> T?,
        describe: (T) -> LinkPreview,
    ): LinkPreview {
        val fallback = { defaultPreview(locale, id?.let { viewUrl(route, idParam, it) }) }
        if (id == null) return fallback()
        val entity = try {
            find(id)
        } catch (e: NotFoundException) {
            null
        } catch (e: ForbiddenException) {
            null
        }
        return entity?.let(describe) ?: fallback()
    }

    /** What a link with no entity behind it shows: the site itself. */
    fun defaultPreview(locale: Locale = Locale.EN, canonicalUrl: String? = null) = LinkPreview(
        title = SITE_NAME,
        description = defaultDescription(locale),
        imageUrl = "$siteUrl/og-default.png",
        canonicalUrl = canonicalUrl ?: siteUrl,
    )

    private fun defaultDescription(locale: Locale) = when (locale) {
        Locale.FR -> "Partagez vos recettes, composez vos livres de cuisine et suivez vos amis sur Cook&Co."
        Locale.EN -> "Share your recipes, build your cookbooks and follow your friends on Cook&Co."
    }

    private fun viewUrl(route: String, idParam: String, id: Long) = "$siteUrl/$route?$idParam=$id"

    /**
     * Mirrors the app's own image naming (`getRecipeImageUrl` and friends): a version of 0
     * means the entity never got a picture of its own, and the bucket's backoffice-managed
     * default stands in.
     */
    private fun imageUrl(bucket: String, id: Long, version: Long) =
        if (version == 0L) "$siteUrl/image/$bucket/default.webp"
        else "$siteUrl/image/$bucket/$id-v$version.webp"

    /**
     * The [preview] rendered into the app shell.
     *
     * @return null when the shell cannot be read, which the caller turns into a 502 so that
     *   nginx serves the static `index.html` instead — the app still works, only the
     *   entity-specific tags are missing.
     */
    suspend fun renderDocument(preview: LinkPreview): String? {
        val shell = shellSource.fetch() ?: return null
        val head = renderHead(preview)
        val start = shell.indexOf(MARKER_START)
        val end = shell.indexOf(MARKER_END)
        return when {
            start >= 0 && end > start -> shell.substring(0, start) + head + shell.substring(end + MARKER_END.length)
            // A shell built before the markers existed: better a duplicated <title> than no
            // preview at all.
            else -> shell.replaceFirst("</head>", "$head</head>")
        }
    }

    fun renderHead(preview: LinkPreview): String = with(preview) {
        val shortDescription = truncate(description)
        """
        <title>${escape(title)}</title>
        <meta name="description" content="${escape(shortDescription)}">
        <link rel="canonical" href="${escape(canonicalUrl)}">
        <meta property="og:site_name" content="${escape(SITE_NAME)}">
        <meta property="og:type" content="${escape(type)}">
        <meta property="og:title" content="${escape(title)}">
        <meta property="og:description" content="${escape(shortDescription)}">
        <meta property="og:url" content="${escape(canonicalUrl)}">
        <meta property="og:image" content="${escape(imageUrl)}">
        <meta property="og:image:alt" content="${escape(title)}">
        <meta name="twitter:card" content="summary_large_image">
        <meta name="twitter:title" content="${escape(title)}">
        <meta name="twitter:description" content="${escape(shortDescription)}">
        <meta name="twitter:image" content="${escape(imageUrl)}">
        """.trimIndent()
    }

    /** Cut on a word boundary, so a preview never ends mid-word. */
    private fun truncate(text: String): String {
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= DESCRIPTION_LIMIT) return collapsed
        val cut = collapsed.take(DESCRIPTION_LIMIT)
        return (cut.substringBeforeLast(' ', cut).trimEnd() + "…")
    }

    /**
     * Titles and descriptions are whatever a member typed, and they land in an attribute
     * value inside a document we assemble by hand — so every character that could end the
     * attribute or open a tag goes out as an entity.
     */
    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
