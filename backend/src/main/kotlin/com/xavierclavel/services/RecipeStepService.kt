package com.xavierclavel.services

import com.xavierclavel.exceptions.NotFoundCause
import com.xavierclavel.exceptions.NotFoundException
import com.xavierclavel.models.RecipeStep
import com.xavierclavel.models.query.QRecipeStep
import org.koin.core.component.KoinComponent

/**
 * Steps, reached on their own.
 *
 * Only images need this: everything else about a step arrives and leaves inside its recipe,
 * which is why there was no such service until a picture had to be posted to one step.
 */
class RecipeStepService: KoinComponent {

    fun getEntityById(id: Long): RecipeStep =
        QRecipeStep().id.eq(id).findOne() ?: throw NotFoundException(NotFoundCause.RECIPE_NOT_FOUND)
}
