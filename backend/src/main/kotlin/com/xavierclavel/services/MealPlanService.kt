package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.MealPlanEntry
import com.xavierclavel.models.Recipe
import com.xavierclavel.models.User
import com.xavierclavel.models.query.QMealPlanEntry
import com.xavierclavel.utils.DbTransaction.insertAndGet
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import shared.dto.MEAL_PLAN_MAX_SERVINGS
import shared.dto.MEAL_PLAN_TITLE_MAX_LENGTH
import shared.dto.MealPlanEntryDTO
import shared.dto.MealPlanEntryEditDTO
import shared.enums.MealSlot
import shared.infodto.MealPlanEntryInfo
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * An account's meal plan: which dishes, on which day, for which meal. See [MealPlanEntry].
 *
 * Every method takes the account it acts for and touches only its rows. An entry of somebody
 * else's is answered exactly as one that does not exist, because a plan is private and its
 * ids are not the caller's to probe. Premium is the controller's to check, not this.
 */
class MealPlanService : KoinComponent {
    private val recipeService: RecipeService by inject()

    /**
     * Every dish planned from [from] to [to], both included, in the order a day is eaten in.
     *
     * Bounded rather than paged: a plan is read a week or a month at a time, never scrolled
     * through, and a page boundary falling inside a day would split a meal. The bound is what
     * keeps it a read of a few dozen rows.
     */
    fun list(ownerId: Long, from: String?, to: String?): List<MealPlanEntryInfo> {
        val start = parseDate(from)
        val end = parseDate(to)
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw BadRequestException(BadRequestCause.MEAL_PLAN_RANGE_INVALID)
        }
        val entries = QMealPlanEntry()
            .owner.id.eq(ownerId)
            .date.between(start, end)
            .findList()
        return describeAll(ownerId, entries)
    }

    fun create(owner: User, dto: MealPlanEntryDTO): MealPlanEntryInfo {
        val date = parseDate(dto.date)
        val servings = checkServings(dto.servings)
        val recipeId = dto.recipeId
        val recipe: Recipe? = recipeId?.let {
            recipeService.findReadable(owner.id, setOf(it))[it]
                // Not told apart from a recipe that does not exist, as with an entry: whether
                // a recipe the caller cannot open is there at all is not theirs to learn.
                ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)
        }
        val entry = MealPlanEntry(
            owner = owner,
            date = date,
            slot = dto.slot,
            position = nextPosition(owner.id, date, dto.slot),
            recipe = recipe,
            title = recipe?.title?.take(MEAL_PLAN_TITLE_MAX_LENGTH) ?: checkTitle(dto.title),
            // A recipe states how many it makes, and that is what planning it means unless
            // the cook says otherwise. Copied rather than read through, so editing the recipe
            // later does not change a meal already planned. A yield nothing bounds is only
            // copied when an edit would accept it back, or the dish could never be moved.
            servings = servings ?: recipe?.yield?.takeIf { it in 1..MEAL_PLAN_MAX_SERVINGS },
        ).insertAndGet()
        return describe(owner.id, entry)
    }

    /**
     * Moves a dish, changes how many it is for, or renames one the cook sees as words.
     *
     * A dish moved to another meal goes to the end of it, as a new one would. One that stays
     * where it is keeps its place.
     *
     * "Sees as words" is what the plan shows, not what the row holds: a recipe the cook can
     * no longer open reaches them as its title alone, with nothing to tell it from a dish they
     * typed, so it renames like one. Renaming it lets go of the recipe for good — the name is
     * theirs now, and a recipe coming back into reach must not quietly take it over again.
     */
    fun update(ownerId: Long, id: Long, dto: MealPlanEntryEditDTO): MealPlanEntryInfo {
        val entry = findOwned(ownerId, id)
        val date = parseDate(dto.date)
        val servings = checkServings(dto.servings)
        if (dto.title != null && !isReadableRecipe(ownerId, entry)) {
            entry.title = checkTitle(dto.title)
            entry.recipe = null
        }
        if (date != entry.date || dto.slot != entry.slot) {
            entry.position = nextPosition(ownerId, date, dto.slot)
            entry.date = date
            entry.slot = dto.slot
        }
        entry.servings = servings
        entry.update()
        return describe(ownerId, entry)
    }

    fun delete(ownerId: Long, id: Long) {
        findOwned(ownerId, id).delete()
    }

    /**
     * What this account has planned before that was not a recipe, most recent first and once
     * each — "Leftovers" and "Pizza night" are typed every week, and should be typed once.
     *
     * Read off the last [SUGGESTION_SCAN] such dishes rather than grouped in SQL: the rows are
     * one account's, the distinct is case-insensitive, and the newest spelling is the one shown.
     */
    fun suggestions(ownerId: Long, query: String?): List<String> =
        QMealPlanEntry()
            .select(QMealPlanEntry.Alias.title)
            .owner.id.eq(ownerId)
            .recipe.id.isNull()
            .apply { if (!query.isNullOrBlank()) title.icontains(query.trim()) }
            .orderBy().date.desc().id.desc()
            .setMaxRows(SUGGESTION_SCAN)
            .findList()
            .map { it.title }
            .distinctBy { it.lowercase() }
            .take(SUGGESTION_LIMIT)

    private fun describe(viewerId: Long, entry: MealPlanEntry): MealPlanEntryInfo =
        describeAll(viewerId, listOf(entry)).single()

    /**
     * The entries as the reader is shown them, at two queries' cost however many there are:
     * one for the entries, one asking which of their recipes the reader can still open.
     */
    private fun describeAll(viewerId: Long, entries: List<MealPlanEntry>): List<MealPlanEntryInfo> {
        // `recipe` is a reference built off the foreign key, so its id costs nothing to read
        val readable = recipeService.findReadable(viewerId, entries.mapNotNull { it.recipe?.id }.toSet())
        return entries
            .sortedWith(compareBy({ it.date }, { it.slot.ordinal }, { it.position }, { it.id }))
            .map { entry -> entry.toInfo(entry.recipe?.id?.let { readable[it] }) }
    }

    private fun isReadableRecipe(viewerId: Long, entry: MealPlanEntry): Boolean {
        val recipeId = entry.recipe?.id ?: return false
        return recipeService.findReadable(viewerId, setOf(recipeId)).containsKey(recipeId)
    }

    private fun findOwned(ownerId: Long, id: Long): MealPlanEntry =
        QMealPlanEntry()
            .id.eq(id)
            .owner.id.eq(ownerId)
            .findOne()
            ?: throw NotFoundException(NotFoundCause.MEAL_PLAN_ENTRY_NOT_FOUND)

    private fun nextPosition(ownerId: Long, date: LocalDate, slot: MealSlot): Int =
        QMealPlanEntry()
            .select(QMealPlanEntry.Alias.position)
            .owner.id.eq(ownerId)
            .date.eq(date)
            .slot.eq(slot)
            .orderBy().position.desc()
            .setMaxRows(1)
            .findOne()
            ?.let { it.position + 1 }
            ?: 0

    private fun parseDate(value: String?): LocalDate =
        try {
            LocalDate.parse(value ?: throw BadRequestException(BadRequestCause.MEAL_PLAN_DATE_INVALID))
        } catch (_: DateTimeParseException) {
            throw BadRequestException(BadRequestCause.MEAL_PLAN_DATE_INVALID)
        }

    private fun checkTitle(title: String?): String {
        val trimmed = title?.trim().orEmpty()
        if (trimmed.isEmpty()) throw BadRequestException(BadRequestCause.MEAL_PLAN_TITLE_EMPTY)
        if (trimmed.length > MEAL_PLAN_TITLE_MAX_LENGTH) throw BadRequestException(BadRequestCause.MEAL_PLAN_TITLE_TOO_LONG)
        return trimmed
    }

    private fun checkServings(servings: Int?): Int? {
        if (servings != null && servings !in 1..MEAL_PLAN_MAX_SERVINGS) {
            throw BadRequestException(BadRequestCause.MEAL_PLAN_SERVINGS_INVALID)
        }
        return servings
    }

    companion object {
        /** Two months: the longest a calendar screen shows at once, with room to spare. */
        const val MAX_RANGE_DAYS = 62L
        const val SUGGESTION_LIMIT = 8
        const val SUGGESTION_SCAN = 200
    }
}
