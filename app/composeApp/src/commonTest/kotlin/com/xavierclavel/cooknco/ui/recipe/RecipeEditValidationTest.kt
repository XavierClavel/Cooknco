package com.xavierclavel.cooknco.ui.recipe

import com.xavierclavel.cooknco.data.UnitRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the editor will not let a cook leave a page with.
 *
 * Each of these used to reach the server and come back as an error on the photo page, three
 * pages away from the field it was about, or — for the steps — to be dropped by the save
 * without a word. What is held here is that the rule is the server's, so a page the editor
 * lets the cook leave is one the save then accepts, and that each problem names the page it
 * is fixed on.
 */
class RecipeEditValidationTest {

    private fun form(
        title: String = "Tarte aux pommes",
        description: String = "",
        ingredients: List<EditIngredient> = emptyList(),
        steps: List<StepItem> = emptyList(),
        tips: String = "",
    ) = RecipeEditUiState(
        title = title,
        description = description,
        ingredients = ingredients,
        steps = steps,
        tips = tips,
        units = UnitRepository.DEFAULT_UNITS,
    )

    private fun custom(name: String, amount: Float? = null, unit: String = "NONE") =
        EditIngredient(customName = name, ingredientName = name, query = name, unit = unit, amount = amount)

    private fun step(
        id: String,
        text: String = "Mélanger",
        used: List<Pair<Int, String>> = emptyList(),
    ) = StepItem(
        id = id,
        text = text,
        attachments = if (used.isEmpty()) emptySet() else setOf(StepAttachment.INGREDIENTS),
        ingredients = used.map { (index, amount) -> StepIngredientDraft(index, amount) },
    )

    // ── Basics ────────────────────────────────────────────────────────────────

    @Test
    fun a_recipe_needs_a_title() {
        assertEquals(listOf(EditProblem.TitleMissing), form(title = "  ").problems())
        assertEquals(emptyList(), form(title = "Tarte").problems())
    }

    @Test
    fun the_title_and_the_description_are_held_to_the_web_editor_s_lengths() {
        assertEquals(emptyList(), form(title = "a".repeat(TITLE_MAX_LENGTH)).problems())
        assertEquals(listOf(EditProblem.TitleTooLong), form(title = "a".repeat(TITLE_MAX_LENGTH + 1)).problems())
        assertEquals(
            listOf(EditProblem.DescriptionTooLong),
            form(description = "a".repeat(DESCRIPTION_MAX_LENGTH + 1)).problems(),
        )
    }

    // ── Ingredients ───────────────────────────────────────────────────────────

    @Test
    fun a_unit_needs_an_amount_beside_it() {
        val problems = form(
            ingredients = listOf(
                custom("sel"),
                custom("farine", unit = "GRAM"),
                custom("sucre", amount = 0f, unit = "GRAM"),
                custom("lait", amount = 25f, unit = "CENTILITER"),
            ),
        ).problems()

        assertEquals(listOf(EditProblem.AmountMissing(1), EditProblem.AmountMissing(2)), problems)
        assertTrue(problems.all { it.page == EditorStep.INGREDIENTS })
    }

    @Test
    fun what_is_half_typed_in_the_search_field_is_not_checked() {
        // The search row is not on the recipe, and a search left unfinished is dropped.
        val search = EditIngredient(query = "far", ingredientName = "far", unit = "GRAM")
        assertEquals(emptyList(), form(ingredients = listOf(search)).problems())
    }

    @Test
    fun a_catalogue_ingredient_cannot_be_counted_in_a_unit_it_is_not_measured_in() {
        val flour = EditIngredient(
            ingredientId = 7,
            ingredientName = "farine",
            unit = "TABLESPOON",
            amount = 2f,
            allowedTypes = listOf("NONE", "WEIGHT"),
        )
        assertEquals(listOf(EditProblem.UnitNotAllowed(0)), form(ingredients = listOf(flour)).problems())
        assertEquals(emptyList(), form(ingredients = listOf(flour.copy(unit = "GRAM"))).problems())

        // A custom row carries no capabilities, and takes any unit.
        assertEquals(
            emptyList(),
            form(ingredients = listOf(custom("farine", amount = 2f, unit = "TABLESPOON"))).problems(),
        )
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    @Test
    fun a_blank_step_carrying_something_needs_words_and_an_empty_one_is_left_alone() {
        val timer = StepItem(id = "a", text = "", attachments = setOf(StepAttachment.TIMER), durationSeconds = 600)
        val empty = StepItem(id = "b", text = " ")

        assertEquals(
            listOf(EditProblem.StepTextMissing("a")),
            form(steps = listOf(timer, empty)).problems(),
        )
    }

    @Test
    fun a_step_and_the_tips_are_held_to_their_lengths() {
        val long = step("a", text = "a".repeat(STEP_TEXT_MAX_LENGTH + 1))
        assertEquals(
            listOf(EditProblem.StepTextTooLong("a"), EditProblem.TipsTooLong),
            form(steps = listOf(long), tips = "a".repeat(TIPS_MAX_LENGTH + 1)).problems(),
        )
        assertEquals(emptyList(), form(steps = listOf(step("a", text = "a".repeat(STEP_TEXT_MAX_LENGTH)))).problems())
    }

    @Test
    fun spelling_out_more_than_the_row_lists_is_refused_in_every_step_that_did() {
        // A blank share beside them, which absorbs nothing when there is nothing left: this is
        // refused for spending too much, not for leaving a gap — and the blank step is not one
        // of the boxes to change.
        val flour = custom("farine", amount = 100f, unit = "GRAM")
        val problems = form(
            ingredients = listOf(flour),
            steps = listOf(
                step("a", used = listOf(0 to "60")),
                step("b", used = listOf(0 to "")),
                step("c", used = listOf(0 to "60")),
            ),
        ).problems()

        val share = problems.single() as EditProblem.SharesDoNotAddUp
        assertEquals(0, share.ingredient)
        assertEquals(listOf("a", "c"), share.steps)
        assertEquals(120f, share.used)
        assertEquals(100f, share.listed)
        assertEquals(false, share.short)
        assertEquals(EditorStep.STEPS, share.page)
    }

    @Test
    fun spelling_out_every_share_and_falling_short_is_refused() {
        val flour = custom("farine", amount = 100f, unit = "GRAM")
        val share = form(ingredients = listOf(flour), steps = listOf(step("a", used = listOf(0 to "30"))))
            .problems().single() as EditProblem.SharesDoNotAddUp

        assertEquals(30f, share.used)
        assertTrue(share.short)
    }

    @Test
    fun a_blank_share_absorbs_what_is_left_and_rounding_is_not_a_mistake() {
        val flour = custom("farine", amount = 100f, unit = "GRAM")
        listOf(
            listOf(step("a", used = listOf(0 to "30")), step("b", used = listOf(0 to ""))),
            listOf(step("a", used = listOf(0 to "")), step("b", used = listOf(0 to ""))),
            listOf(step("a", used = listOf(0 to "100"))),
            // A hundredth short: what thirds of a hundred come to once somebody types them.
            listOf(
                step("a", used = listOf(0 to "33.33")),
                step("b", used = listOf(0 to "33.33")),
                step("c", used = listOf(0 to "33.33")),
            ),
        ).forEach { steps ->
            assertEquals(emptyList(), form(ingredients = listOf(flour), steps = steps).problems(), "$steps")
        }
    }

    @Test
    fun only_shares_the_save_would_send_are_added_up() {
        // A blank step is not saved, so what it claims is not either; and a row with no
        // amount has no share to claim, which the save does not send.
        val flour = custom("farine", amount = 100f, unit = "GRAM")
        val salt = custom("sel")
        assertEquals(
            emptyList(),
            form(
                ingredients = listOf(flour, salt),
                steps = listOf(step("a", used = listOf(0 to "100", 1 to "5")), step("b", text = "", used = listOf(0 to "50"))),
            ).problems().filterIsInstance<EditProblem.SharesDoNotAddUp>(),
        )
    }

    // ── Order ─────────────────────────────────────────────────────────────────

    @Test
    fun problems_come_page_by_page_and_in_the_order_each_page_draws_them() {
        val problems = form(
            title = "",
            ingredients = listOf(custom("farine", amount = 100f, unit = "GRAM"), custom("sucre", unit = "GRAM")),
            steps = listOf(
                StepItem(id = "a", text = "", attachments = setOf(StepAttachment.PHOTO)),
                step("b", used = listOf(0 to "20")),
            ),
            tips = "a".repeat(TIPS_MAX_LENGTH + 1),
        ).problems()

        assertEquals(
            listOf(
                EditProblem.TitleMissing,
                EditProblem.AmountMissing(1),
                EditProblem.StepTextMissing("a"),
                EditProblem.SharesDoNotAddUp(ingredient = 0, steps = listOf("b"), used = 20f, listed = 100f),
                EditProblem.TipsTooLong,
            ),
            problems,
        )
    }

    @Test
    fun nothing_is_shown_for_a_page_the_cook_has_not_tried_to_leave() {
        val state = form(title = "", ingredients = listOf(custom("farine", unit = "GRAM")))

        assertEquals(emptyList(), state.shownProblems())
        assertEquals(
            listOf(EditProblem.AmountMissing(0)),
            state.copy(checkedPages = setOf(EditorStep.INGREDIENTS)).shownProblems(),
        )
    }
}
