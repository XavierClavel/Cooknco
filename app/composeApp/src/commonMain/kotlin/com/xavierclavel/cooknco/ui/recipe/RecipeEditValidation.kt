package com.xavierclavel.cooknco.ui.recipe

/** The editor's four pages, in the order the cook goes through them. */
enum class EditorStep {
    BASICS,
    INGREDIENTS,
    STEPS,
    PHOTO,
}

/**
 * Something on the form that the save would refuse, or would quietly drop — and the page it
 * is fixed on.
 *
 * The page is the point. Everything here used to surface after PUBLISH, on the photo page,
 * three pages away from the field that caused it, and as whatever the server said. A problem
 * that knows its page is one the cook hears about while they are still looking at the field,
 * because [RecipeEditViewModel.next] does not let them leave a page that holds one.
 *
 * The server stays the authority. These mirror the rules it is known to apply, so that a save
 * which reaches it is one it accepts; one it still refuses — an ingredient deleted from the
 * catalogue in the meantime — reports as before.
 */
sealed interface EditProblem {
    val page: EditorStep

    data object TitleMissing : EditProblem {
        override val page = EditorStep.BASICS
    }

    data object TitleTooLong : EditProblem {
        override val page = EditorStep.BASICS
    }

    data object DescriptionTooLong : EditProblem {
        override val page = EditorStep.BASICS
    }

    /** A unit with no amount beside it. The server refuses one without the other. */
    data class AmountMissing(val ingredient: Int) : EditProblem {
        override val page = EditorStep.INGREDIENTS
    }

    /**
     * A catalogue ingredient counted in a unit it cannot be measured in.
     *
     * The picker only offers the ones that fit, so this is reached the other way: a scan that
     * kept the page's unit for an ingredient it then matched, or a catalogue entry whose
     * capabilities changed since the recipe was written.
     */
    data class UnitNotAllowed(val ingredient: Int) : EditProblem {
        override val page = EditorStep.INGREDIENTS
    }

    /**
     * A step with no words but with something attached to it.
     *
     * A blank step is not saved at all, so this one would take its timer, its ingredients or
     * its picture with it — something the cook added and would never see again. A blank step
     * carrying nothing is simply left out, as it always was.
     */
    data class StepTextMissing(val step: String) : EditProblem {
        override val page = EditorStep.STEPS
    }

    data class StepTextTooLong(val step: String) : EditProblem {
        override val page = EditorStep.STEPS
    }

    /**
     * The shares the steps spell out of one ingredient do not come to what its row lists —
     * the rule `RecipeIngredientService.validateStepIngredients` applies on the server.
     *
     * [steps] are the ones that spelled out a number of it, in order, which are the boxes to
     * change. [used] is what they add up to, and [listed] the row's amount.
     */
    data class SharesDoNotAddUp(
        val ingredient: Int,
        val steps: List<String>,
        val used: Float,
        val listed: Float,
    ) : EditProblem {
        override val page = EditorStep.STEPS

        /** Under rather than over, which a blank share would absorb — and is how to fix it. */
        val short get() = used < listed
    }

    data object TipsTooLong : EditProblem {
        override val page = EditorStep.STEPS
    }
}

/** The step a problem is to be fixed in, for the ones that are about a step. */
val EditProblem.stepId: String?
    get() = when (this) {
        is EditProblem.StepTextMissing -> step
        is EditProblem.StepTextTooLong -> step
        is EditProblem.SharesDoNotAddUp -> steps.first()
        else -> null
    }

/**
 * The longest text each field holds, as the web editor counts them.
 *
 * Its limits rather than the columns', which are looser for the title (255): a recipe saved
 * from the app with a 150-character title would otherwise be one the website then refuses to
 * save until it is shortened. The step's is also `shared.dto.RECIPE_STEP_TEXT_MAX_LENGTH`,
 * which this build cannot import — `shared` is JVM-only.
 */
internal const val TITLE_MAX_LENGTH = 100
internal const val DESCRIPTION_MAX_LENGTH = 255
internal const val STEP_TEXT_MAX_LENGTH = 255
internal const val TIPS_MAX_LENGTH = 511

/**
 * Below a tenth of a unit, two amounts are the same one: 33.3 + 33.3 + 33.4 is not 100 in
 * floats and never will be. The server allows the same.
 */
private const val SHARE_TOLERANCE = 0.1f

/**
 * Whether a row goes out with the save: a catalogue entry, or a custom one with a name.
 *
 * Not the search row, whatever is half-typed in it — abandoning a search drops it, as it
 * should — so neither the checks below nor a step's list of ingredients look at that one.
 */
internal val EditIngredient.isSaved get() = ingredientId != null || !customName.isNullOrBlank()

/**
 * The amount a row is saved with: none when its unit says there is none.
 *
 * One place for it because three things have to agree on it — the payload, the check on the
 * row, and what the steps may claim a share of.
 */
internal val EditIngredient.savedAmount get() = if (unit == "NONE") null else amount

/** What a step's share is saved as. Blank, zero or unreadable all mean "the rest of it". */
internal val StepIngredientDraft.savedAmount get() = amount.toFloatOrNull()?.takeIf { it > 0f }

/**
 * Everything wrong with the form, page by page, and within a page in the order it is drawn —
 * so the first one of a page is the one to scroll to.
 */
fun RecipeEditUiState.problems(): List<EditProblem> = buildList {
    if (title.isBlank()) add(EditProblem.TitleMissing)
    if (title.trim().length > TITLE_MAX_LENGTH) add(EditProblem.TitleTooLong)
    if (description.trim().length > DESCRIPTION_MAX_LENGTH) add(EditProblem.DescriptionTooLong)

    ingredients.forEachIndexed { index, row ->
        if (!row.isSaved) return@forEachIndexed
        if (row.unit != "NONE" && (row.amount ?: 0f) <= 0f) add(EditProblem.AmountMissing(index))
        // An empty list is a custom row, which carries no capabilities and takes any unit. A
        // unit missing from the catalogue is left to the server rather than guessed at.
        val unitType = units.firstOrNull { it.name == row.unit }?.type
        if (row.allowedTypes.isNotEmpty() && unitType != null && unitType !in row.allowedTypes) {
            add(EditProblem.UnitNotAllowed(index))
        }
    }

    val shares = sharesThatDoNotAddUp()
    steps.forEach { step ->
        if (step.text.isBlank() && step.attachments.isNotEmpty()) add(EditProblem.StepTextMissing(step.id))
        if (step.text.trim().length > STEP_TEXT_MAX_LENGTH) add(EditProblem.StepTextTooLong(step.id))
        addAll(shares.filter { it.stepId == step.id })
    }
    if (tips.trim().length > TIPS_MAX_LENGTH) add(EditProblem.TipsTooLong)
}

/** The problems the cook is shown: those of the pages they have tried to leave. */
fun RecipeEditUiState.shownProblems(): List<EditProblem> = problems().filter { it.page in checkedPages }

/**
 * The server's `validateStepIngredients`, over what [RecipeEditViewModel.save] would send.
 *
 * Spelling out more than the row lists is wrong whatever the blanks say; spelling out every
 * share and still missing the total leaves nothing to absorb the difference. Everything else —
 * one blank, two, blanks beside numbers — is allowed. A row listed without an amount has no
 * share to claim, but the box is not offered for one and the save sends none, so there is
 * nothing to check.
 */
private fun RecipeEditUiState.sharesThatDoNotAddUp(): List<EditProblem.SharesDoNotAddUp> {
    val claims = steps
        .filter { it.text.isNotBlank() && StepAttachment.INGREDIENTS in it.attachments }
        .flatMap { step -> step.ingredients.map { used -> used.index to (step.id to used.savedAmount) } }
        .groupBy({ it.first }, { it.second })

    return claims.mapNotNull { (index, claimed) ->
        val row = ingredients.getOrNull(index)?.takeIf { it.isSaved } ?: return@mapNotNull null
        val listed = row.savedAmount ?: return@mapNotNull null
        val spelledOut = claimed.filter { it.second != null }
        val used = spelledOut.sumOf { it.second!!.toDouble() }.toFloat()
        val overspent = used - listed > SHARE_TOLERANCE
        val shortWithNothingToAbsorbIt =
            spelledOut.size == claimed.size && kotlin.math.abs(used - listed) > SHARE_TOLERANCE
        if (!overspent && !shortWithNothingToAbsorbIt) return@mapNotNull null
        EditProblem.SharesDoNotAddUp(
            ingredient = index,
            steps = spelledOut.map { it.first }.distinct(),
            used = used,
            listed = listed,
        )
    }
}
