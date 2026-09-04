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
            .select("id")
            .fetch("ingredient", "id")
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

        val nameMatch = """
            custom_name is not null
              and lower(unaccent(custom_name)) = lower(unaccent(:customName))
            """.trimIndent()

        val allowedUnits = AmountUnit.entries.filter { it.type in ingredient.allowedTypes() }
        // Named placeholders rather than an inlined list: Ebean binds them, and mixing in a
        // positional one is not allowed alongside the named parameters above.
        val unitParams = allowedUnits.indices.joinToString(", ") { ":unit$it" }

        val matched = DB.sqlQuery("select count(*) as total from recipe_ingredients where $nameMatch")
            .setParameter("customName", customName)
            .findOne()!!
            .getInteger("total")

        val converted = DB.sqlUpdate("""
            update recipe_ingredients
            set ingredient_id = :ingredientId, custom_name = null
            where $nameMatch
              and unit in ($unitParams)
            """.trimIndent())
            .setParameter("ingredientId", ingredient.id)
            .setParameter("customName", customName)
            .apply { allowedUnits.forEachIndexed { index, unit -> setParameter("unit$index", unit.name) } }
            .execute()

        return AbsorbCustomIngredientResult(convertedRows = converted, skippedRows = matched - converted)
    }

    fun deleteById(ingredientId: Long): Boolean =
        QIngredient().id.eq(ingredientId).findOne()?.deleteAndGet() != null


    fun search(searchString: String, paging: Paging, locale: Locale): Pair<Int,List<IngredientInfo>> {
        val query = QIngredient()
            .apply {
                if (searchString.isBlank()) return@apply
                this.and()
                    .translations.locale.eq(locale)
                    .raw("word_similarity(unaccent(?), unaccent(${QIngredient.Alias.translations.name})) > 0.3", searchString)
                    .endAnd()
            }
            .query()

        // Ebean copies orderBy strings into SQL verbatim (no parameter binding),
        // so the search term is inlined as an injection-proof hex literal
        query.orderBy("word_similarity(unaccent(${sqlStringLiteral(searchString)}), unaccent(${QIngredient.Alias.translations.name})) desc")

        return Pair(query.findCount(), query.setPaging(paging).findList().map{it.toInfo()})
    }



    //Ebean.find(MyClass.class).order("id").findPagedList(page,size);

}