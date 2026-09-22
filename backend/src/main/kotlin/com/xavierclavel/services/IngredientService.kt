package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Ingredient
import com.xavierclavel.models.jointables.query.QRecipeIngredient
import com.xavierclavel.models.localization.LocalizedIngredientName
import com.xavierclavel.models.localization.query.QLocalizedIngredientName
import com.xavierclavel.models.query.QIngredient
import shared.dto.AbsorbCustomIngredientResult
import shared.dto.IngredientDTO
import shared.infodto.CustomIngredientUsage
import io.ebean.DB
import io.ebean.Paging
import org.koin.core.component.KoinComponent
import com.xavierclavel.utils.DbTransaction.deleteAndGet
import com.xavierclavel.utils.DbTransaction.insertAndGet
import com.xavierclavel.utils.DbTransaction.updateAndGet
import com.xavierclavel.utils.sqlStringLiteral
import shared.enums.AmountUnit
import shared.enums.Locale
import java.text.Normalizer
import shared.enums.Sort
import shared.infodto.IngredientInfo

class IngredientService: KoinComponent {
    fun countAll() =
        QIngredient().findCount()

    fun countRecipes(id: Long) =
        QRecipeIngredient().ingredient.id.eq(id).findCount()

    /**
     * How many recipes use each of [ingredientIds], counted in one pass so the admin
     * catalogue table does not issue a count per row.
     *
     * @return count per ingredient id; unused ingredients are absent from the map
     */
    fun countRecipesByIngredient(ingredientIds: Collection<Long>): Map<Long, Int> {
        if (ingredientIds.isEmpty()) return emptyMap()
        val counts = mutableMapOf<Long, Int>()
        QRecipeIngredient()
            .select(QRecipeIngredient.Alias.id)
            .ingredient.fetch(QIngredient.Alias.id)
            .ingredient.id.`in`(ingredientIds)
            .findList()
            .forEach { link -> link.ingredient?.id?.let { counts.merge(it, 1, Int::plus) } }
        return counts
    }

    fun findEntityById(ingredientId: Long) : Ingredient? =
        QIngredient().id.eq(ingredientId).findOne()

    fun findById(ingredientId: Long) : IngredientInfo? =
        QIngredient().id.eq(ingredientId).findOne()?.toInfo()

    fun createIngredient(ingredientDTO: IngredientDTO): IngredientInfo {
        validate(ingredientDTO)
        val ingredient = Ingredient().mergeDTO(ingredientDTO).insertAndGet()
        ingredientDTO.name.forEach {
            val localizedIngredientName = LocalizedIngredientName(ingredient = ingredient, locale = it.key, name = it.value)
            localizedIngredientName.insert()
        }
        return QIngredient().id.eq(ingredient.id).findOne()!!.toInfo()
    }


    fun updateIngredient(id:Long, ingredientDTO: IngredientDTO): IngredientInfo {
        validate(ingredientDTO)
        val ingredient = findEntityById(id) ?: throw NotFoundException(NotFoundCause.INGREDIENT_NOT_FOUND)

        ingredientDTO.name.forEach {
            val localizedIngredientName = QLocalizedIngredientName()
                .ingredient.id.eq(ingredient.id)
                .locale.eq(it.key)
                .findOneOrEmpty()
                .orElse(LocalizedIngredientName(ingredient = ingredient, locale = it.key))
            localizedIngredientName.name = it.value
            localizedIngredientName.save()
        }
        ingredient
            .mergeDTO(ingredientDTO)
            .updateAndGet()
            .toInfo()
        return QIngredient().id.eq(ingredient.id).findOne()!!.toInfo()
    }



    /**
     * A conversion factor doubles as the "this ingredient can be measured this way" flag, so a
     * non-positive one would claim a capability while making the conversion nonsensical.
     */
    private fun validate(ingredientDTO: IngredientDTO) {
        val factors = listOf(ingredientDTO.gramsPerUnit, ingredientDTO.gramsPerMilliliter)
        if (factors.any { it != null && it <= 0f }) {
            throw BadRequestException(BadRequestCause.INVALID_CONVERSION_FACTOR)
        }

        val defaultUnit = ingredientDTO.defaultUnit
        if (defaultUnit != null && defaultUnit.type !in ingredientDTO.allowedTypes()) {
            throw BadRequestException(BadRequestCause.UNIT_NOT_ALLOWED_FOR_INGREDIENT)
        }
    }

    /**
     * The free-text ingredient names users typed, most used first. Names are grouped ignoring case
     * and accents so "Yuzu" and "yuzú" count as the same candidate.
     */
    fun findCustomIngredientUsage(paging: Paging): Pair<Int, List<CustomIngredientUsage>> {
        val total = DB.sqlQuery("""
            select count(*) as total from (
                select 1 from recipe_ingredients
                where custom_name is not null
                group by lower(unaccent(custom_name))
            ) grouped
            """.trimIndent())
            .findOne()
            ?.getInteger("total") ?: 0

        val items = DB.sqlQuery("""
            select min(custom_name) as display_name, count(*) as uses
            from recipe_ingredients
            where custom_name is not null
            group by lower(unaccent(custom_name))
            order by uses desc, display_name asc
            limit :limit offset :offset
            """.trimIndent())
            .setParameter("limit", paging.pageSize())
            .setParameter("offset", paging.pageIndex() * paging.pageSize())
            .findList()
            .map { CustomIngredientUsage(name = it.getString("display_name"), uses = it.getInteger("uses")) }

        return Pair(total, items)
    }

    /**
     * Re-points every recipe row using [customName] as free text at the real ingredient [ingredientId],
     * so recipes that predate the catalog entry gain its nutrition data.
     *
     * A row is only re-pointed if its unit is one the ingredient allows. A free-text row accepts any
     * unit — it has no capability data — so absorbing blindly would produce rows the write path then
     * rejects, leaving the owner unable to save their own recipe, and a quantity no conversion can
     * turn into nutrition. Rows that do not fit stay free text and are reported back, so the fix is
     * to give the ingredient the missing conversion and absorb again rather than to lose the amount.
     */
    fun absorbCustomIngredient(ingredientId: Long, customName: String): AbsorbCustomIngredientResult {
        if (customName.isBlank()) throw BadRequestException(BadRequestCause.INVALID_REQUEST)
        val ingredient = findEntityById(ingredientId)
            ?: throw NotFoundException(NotFoundCause.INGREDIENT_NOT_FOUND)

        // Names are compared case- and accent-insensitively, which no query bean operator
        // expresses, so the comparison itself is a raw fragment over the alias-derived column.
        val nameMatch = "lower(unaccent(${QRecipeIngredient.Alias.customName})) = lower(unaccent(?))"

        val allowedUnits = AmountUnit.entries.filter { it.type in ingredient.allowedTypes() }

        val matched = QRecipeIngredient()
            .raw(nameMatch, customName)
            .findCount()

        val converted = QRecipeIngredient()
            .raw(nameMatch, customName)
            .unit.`in`(allowedUnits)
            .asUpdate()
            .set(QRecipeIngredient.Alias.ingredient.id, ingredient.id)
            .setNull(QRecipeIngredient.Alias.customName)
            .update()

        return AbsorbCustomIngredientResult(convertedRows = converted, skippedRows = matched - converted)
    }

    fun deleteById(ingredientId: Long): Boolean =
        QIngredient().id.eq(ingredientId).findOne()?.deleteAndGet() != null


    /**
     * Ingredients matching [searchString], in the order [sort] asks for.
     *
     * `BEST_MATCH` is the default and the only order that makes sense while someone is
     * typing — it is what the trigram similarity already computed to decide what matches at
     * all. It is also meaningless without a term to be similar *to*, so a blank search falls
     * back to alphabetical rather than ordering by similarity to nothing.
     */
    fun search(
        searchString: String,
        paging: Paging,
        locale: Locale,
        sort: Sort = Sort.BEST_MATCH,
    ): Pair<Int,List<IngredientInfo>> {
        val query = QIngredient()
            .apply {
                if (searchString.isBlank()) return@apply
                this.and()
                    .translations.locale.eq(locale)
                    .raw("word_similarity(unaccent(?), unaccent(${QIngredient.Alias.translations.name})) > 0.3", searchString)
                    .endAnd()
            }
            .query()

        val effectiveSort = if (sort == Sort.BEST_MATCH && searchString.isBlank()) Sort.NAME_ASCENDING else sort
        when (effectiveSort) {
            // Ebean copies orderBy strings into SQL verbatim (no parameter binding),
            // so the search term is inlined as an injection-proof hex literal
            Sort.BEST_MATCH -> query.orderBy(
                "word_similarity(unaccent(${sqlStringLiteral(searchString)}), unaccent(${QIngredient.Alias.translations.name})) desc"
            )
            // The name lives on the translation rows, so ordering by it needs the locale's
            // row picked out — the same predicate the search itself filters on.
            Sort.NAME_DESCENDING -> query.orderBy("${QIngredient.Alias.translations.name} desc")
            else -> query.orderBy("${QIngredient.Alias.translations.name} asc")
        }

        return Pair(query.findCount(), query.setPaging(paging).findList().map{it.toInfo()})
    }

    /**
     * The catalogue entries named exactly by any of [names], in [locale].
     *
     * "Exactly" in the sense the scanner means it: case and accents aside, and a trailing
     * plural aside, because a file says "Pommes" where the catalogue says "pomme". Nothing
     * looser belongs here — [search] is fuzzy by design, so that it can find "farine" from
     * "fari", and its best answer to "sel" is a salt of some kind rather than salt. An
     * ingredient quietly replaced by a near neighbour reads as correct and is wrong in the
     * nutrition, so a name this does not place is left to the caller as free text.
     *
     * One query for the whole list rather than one each: an import matches every ingredient
     * of a recipe at once. `unaccent` and `lower` are PostgreSQL's, which no query bean
     * predicate expresses — the column is named through `Alias` and the names are bound, as
     * `raw()` requires.
     *
     * @return the matching entries, keyed by the folded name they were found under. Both the
     *   singular and the plural of a hit are keys of it, so a caller looks up whichever form
     *   it holds without folding twice.
     */
    fun findByNames(names: Collection<String>, locale: Locale): Map<String, IngredientInfo> {
        val wanted = names.map { fold(it) }.filter { it.isNotBlank() }.toSet()
        if (wanted.isEmpty()) return emptyMap()

        // Both forms of each name, so that the catalogue's singular is found from a file's
        // plural and the other way round. The comparison below is what decides; this only
        // has to be a superset of it.
        val candidates = wanted.flatMap { listOf(it, it.removeSuffix("s"), it + "s") }.toSet()
        val placeholders = candidates.joinToString(",") { "?" }

        val found = QIngredient()
            .and()
                .translations.locale.eq(locale)
                .raw(
                    "lower(unaccent(${QIngredient.Alias.translations.name})) in ($placeholders)",
                    *candidates.toTypedArray(),
                )
            .endAnd()
            .findList()

        val byName = mutableMapOf<String, IngredientInfo>()
        found.forEach { ingredient ->
            val name = ingredient.translations.find { it.locale == locale }?.name ?: return@forEach
            val key = fold(name).removeSuffix("s")
            val info = ingredient.toInfo()
            // Singular and plural both point at it. `putIfAbsent`, so that two catalogue
            // entries differing only by a plural do not silently take each other's place —
            // the first is kept and the second stays unmatched, which is free text rather
            // than the wrong ingredient.
            byName.putIfAbsent(key, info)
            byName.putIfAbsent(key + "s", info)
        }
        return byName
    }

    /** Case, accents and surrounding space removed — the form names are compared in. */
    private fun fold(value: String): String =
        DIACRITICS.replace(Normalizer.normalize(value.trim(), Normalizer.Form.NFD), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")

    private companion object {
        /** Combining marks, which is what an accent decomposes into under NFD. */
        private val DIACRITICS = Regex("\\p{Mn}+")
    }




}