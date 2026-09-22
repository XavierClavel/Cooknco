package shared.infodto

import shared.dto.RecipeDTO
import kotlinx.serialization.Serializable

/**
 * A `.cook` file, read into what the recipe editor should be filled in with.
 *
 * The import creates nothing — see `CooklangService` — so this is the whole of what a client
 * gets back, and it is deliberately the same [RecipeDTO] the editor would send to save. What
 * the user does next is check it and press save, which goes through
 * `RecipeController.createRecipe` like any other new recipe.
 *
 * [ingredientNames] is positional against [recipe]'s ingredients, on the same reasoning as
 * `RecipeDTO.RecipeStepIngredientDTO.index`: inside one request a position is the only thing
 * both sides can agree on, since none of these rows has an id yet. It is here because a row
 * matched to the catalogue carries an id and no name, and a screen has to print something.
 */
@Serializable
data class CooklangImportInfo(
    val recipe: RecipeDTO,

    /** What each of [recipe]'s ingredients is called, in that order. */
    val ingredientNames: List<String> = emptyList(),

    /**
     * How many ingredients the catalogue did not obviously hold, and which therefore came
     * through as free text.
     *
     * Reported rather than left to be counted, because it is the one thing worth telling the
     * user about an import that otherwise looks complete: those rows work, they simply carry
     * no nutrition and will not be found by a search.
     */
    val unmatchedIngredients: Int = 0,

    /**
     * Whether a step was cut in two because the file's paragraph was longer than a step may
     * be. The editor says so, so that a cook who sees their method in more pieces than they
     * wrote it knows why, and can join them back up.
     */
    val stepsWereSplit: Boolean = false,
)
