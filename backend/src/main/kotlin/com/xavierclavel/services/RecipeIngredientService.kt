package com.xavierclavel.services

import com.xavierclavel.exceptions.BadRequestCause
import com.xavierclavel.exceptions.BadRequestException
import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.Ingredient
import com.xavierclavel.models.jointables.RecipeIngredient
import com.xavierclavel.models.jointables.query.QRecipeIngredient
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
            QRecipeIngredient().recipe.id.eq(recipeId).delete()
            entities.forEach { it.insert() }
            transaction.commit()
        }
    }

    fun updateRecipeIngredients(recipeId: Long, recipeDTO: RecipeDTO) =
        replaceRecipeIngredients(recipeId, validateIngredients(recipeDTO))

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
