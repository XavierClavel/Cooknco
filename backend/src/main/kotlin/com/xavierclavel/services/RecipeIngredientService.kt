package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Ingredient
import com.xavierclavel.models.jointables.RecipeIngredient
import com.xavierclavel.models.jointables.RecipeStepIngredient
import com.xavierclavel.models.jointables.query.QRecipeIngredient
import com.xavierclavel.models.jointables.query.QRecipeStepIngredient
import com.xavierclavel.models.query.QRecipeStep
import io.ebean.DB
import shared.dto.CUSTOM_INGREDIENT_NAME_MAX_LENGTH
import shared.dto.RecipeDTO
import shared.enums.AmountUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** An ingredient row that passed validation: for a custom row [ingredient] is null. */
class ValidatedIngredientRow(
    val ingredient: Ingredient?,
    val customName: String?,
    val amount: Float?,
    val unit: AmountUnit,
    val complement: String?,
)

class RecipeIngredientService: KoinComponent {
    val ingredientService: IngredientService by inject()
    val recipeService: RecipeService by inject()

    fun countAll() =
            QRecipeIngredient().findCount()

    fun findEntitiesByRecipe(recipeId: Long) =
        QRecipeIngredient().where().recipe.id.eq(recipeId).orderBy().sortOrder.asc().findList()

    /**
     * Checks and resolves every ingredient row. Callers that create a recipe run this first, so a bad
     * row is rejected before anything is persisted rather than leaving a recipe with no ingredients.
     */
    fun validateIngredients(recipeDTO: RecipeDTO): List<ValidatedIngredientRow> =
        recipeDTO.ingredients.map { dto ->
            validateAmount(dto)
            ValidatedIngredientRow(
                ingredient = resolveIngredient(dto),
                customName = dto.customName?.trim(),
                amount = dto.amount,
                unit = dto.unit,
                complement = dto.complement,
            )
        }

    /**
     * Replaces the whole ingredient list of a recipe. Rows carry no references of their own, so
     * rewriting them is cheaper than diffing and it keeps [RecipeIngredient.sortOrder] in sync with
     * the order the client sent.
     */
    fun replaceRecipeIngredients(recipeId: Long, rows: List<ValidatedIngredientRow>) {
        val recipe = recipeService.getEntityById(recipeId)
        val entities = rows.mapIndexed { index, row ->
            RecipeIngredient(
                recipe = recipe,
                ingredient = row.ingredient,
                customName = row.customName,
                amount = row.amount,
                unit = row.unit,
                complement = row.complement,
                sortOrder = index,
            )
        }

        DB.beginTransaction().use { transaction ->
            // The links first. recipe_step_ingredients restricts deletes at both ends, so an
            // ingredient row a step still points at cannot go - and the rows about to be
            // deleted here are exactly the ones the recipe's steps were pointing at.
            QRecipeStepIngredient().step.recipe.id.eq(recipeId).delete()
            QRecipeIngredient().recipe.id.eq(recipeId).delete()
            entities.forEach { it.insert() }
            transaction.commit()
        }
    }

    /**
     * Refuses the two things a recipe's steps can say about an ingredient that cannot be true.
     *
     * A blank means "unspecified", not "all of it", so most of what looks like a disagreement
     * is only a gap, and a gap is worked out on the way out rather than rejected here — see
     * `Recipe.blankStepAmounts`. What is left is:
     *
     * - **spelling out more than the line has.** 60 g and 60 g of a 100 g line is wrong
     *   whatever the blanks say, and with one blank it would leave a negative remainder.
     * - **spelling out every share and still missing the total.** With no blank there is
     *   nothing left to absorb the difference, so 30 g and 30 g of 100 g is a recipe that has
     *   lost 40 g rather than one that has not said where it goes.
     *
     * Refused rather than rounded or topped up: only the author knows which of the two
     * numbers is the wrong one. Everything else — one blank, two blanks, blanks beside
     * amounts — is allowed, and costs at most a quantity that is not shown.
     *
     * Runs before anything is written, like [validateIngredients], so a rejection never
     * leaves a half-saved recipe.
     */
    fun validateStepIngredients(recipeDTO: RecipeDTO) {
        val usage = mutableMapOf<Int, MutableList<Float?>>()
        recipeDTO.steps.forEach { step ->
            step.ingredients.forEach { usage.getOrPut(it.index) { mutableListOf() }.add(it.amount) }
        }

        usage.forEach { (position, claimed) ->
            // A position pointing at nothing is dropped when the links are written rather than
            // refused here - see [linkStepIngredients] - so there is nothing to add up either.
            val ingredient = recipeDTO.ingredients.getOrNull(position) ?: return@forEach
            val listed = ingredient.amount

            if (listed == null) {
                // "Salt, to taste" has no amount to divide. A step claiming a number of it is
                // claiming a share of nothing.
                if (claimed.any { it != null }) {
                    throw BadRequestException(BadRequestCause.STEP_AMOUNTS_DO_NOT_ADD_UP)
                }
                return@forEach
            }

            if (claimed.any { it != null && it <= 0f }) {
                throw BadRequestException(BadRequestCause.INVALID_AMOUNT)
            }

            val spelledOut = claimed.filterNotNull().sumOf { it.toDouble() }
            val blanks = claimed.count { it == null }
            // Floats: 33.3 + 33.3 + 33.4 is not 100 and never will be. A tenth of a unit is
            // below anything a recipe means and above anything the arithmetic can lose.
            val tolerance = 0.1
            val overspent = spelledOut - listed.toDouble() > tolerance
            val shortWithNothingToAbsorbIt =
                blanks == 0 && kotlin.math.abs(spelledOut - listed.toDouble()) > tolerance
            if (overspent || shortWithNothingToAbsorbIt) {
                throw BadRequestException(BadRequestCause.STEP_AMOUNTS_DO_NOT_ADD_UP)
            }
        }
    }

    /**
     * Points each step at the ingredients it uses.
     *
     * A third pass, after both lists are on disk, because neither can be written knowing the
     * other: the steps are saved with the recipe and the ingredients are replaced wholesale
     * afterwards, so at no single moment does a caller hold both sets of rows. What the
     * client sent is positions ([RecipeDTO.RecipeStepDTO.ingredientIndexes]), and a position
     * is exactly what [RecipeIngredient.sortOrder] was set to above.
     *
     * A position that points at nothing is dropped rather than refused. The lists are
     * rewritten together on every save, so it can only mean a client that built one of them
     * wrong, and losing a link is a smaller wrong than losing the recipe.
     */
    fun linkStepIngredients(recipeId: Long, steps: List<RecipeDTO.RecipeStepDTO>) {
        if (steps.none { it.ingredients.isNotEmpty() }) return

        val ingredientsByPosition = QRecipeIngredient().recipe.id.eq(recipeId).findList()
            .associateBy { it.sortOrder }
        val stepsByPosition = QRecipeStep().recipe.id.eq(recipeId).findList()
            .associateBy { it.sortOrder }

        DB.beginTransaction().use { transaction ->
            steps.forEachIndexed { position, dto ->
                val step = stepsByPosition[position] ?: return@forEachIndexed
                dto.ingredients
                    .distinctBy { it.index }
                    .forEach { used ->
                        val ingredient = ingredientsByPosition[used.index] ?: return@forEach
                        RecipeStepIngredient(
                            step = step,
                            ingredient = ingredient,
                            amount = used.amount,
                        ).insert()
                    }
            }
            transaction.commit()
        }
    }

    fun updateRecipeIngredients(recipeId: Long, recipeDTO: RecipeDTO) {
        val rows = validateIngredients(recipeDTO)
        validateStepIngredients(recipeDTO)
        replaceRecipeIngredients(recipeId, rows)
        linkStepIngredients(recipeId, recipeDTO.steps)
    }

    /** Returns the referenced ingredient, or null when the row is a custom (free-text) one. */
    private fun resolveIngredient(dto: RecipeDTO.RecipeIngredientDTO): Ingredient? {
        val ingredientId = dto.id
        val customName = dto.customName?.trim()

        // Exactly one of the two identifies the row; anything else is unrenderable.
        if ((ingredientId == null) == customName.isNullOrEmpty()) {
            throw BadRequestException(BadRequestCause.INVALID_INGREDIENT_ROW)
        }

        if (ingredientId == null) {
            if (customName!!.length > CUSTOM_INGREDIENT_NAME_MAX_LENGTH) {
                throw BadRequestException(BadRequestCause.CUSTOM_INGREDIENT_NAME_TOO_LONG)
            }
            // Custom rows have no capability data, so any unit is acceptable.
            return null
        }

        val ingredient = ingredientService.findEntityById(ingredientId)
            ?: throw NotFoundException(NotFoundCause.INGREDIENT_NOT_FOUND)

        if (dto.unit.type !in ingredient.allowedTypes()) {
            throw BadRequestException(BadRequestCause.UNIT_NOT_ALLOWED_FOR_INGREDIENT)
        }
        return ingredient
    }

    /** An amount only means something alongside a unit, and vice versa. */
    private fun validateAmount(dto: RecipeDTO.RecipeIngredientDTO) {
        val hasAmount = dto.amount != null
        if (dto.unit == AmountUnit.NONE && hasAmount) {
            throw BadRequestException(BadRequestCause.INVALID_AMOUNT)
        }
        if (dto.unit != AmountUnit.NONE && (!hasAmount || dto.amount!! <= 0f)) {
            throw BadRequestException(BadRequestCause.INVALID_AMOUNT)
        }
    }
}
