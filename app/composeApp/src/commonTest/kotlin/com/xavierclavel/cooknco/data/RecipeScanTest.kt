package com.xavierclavel.cooknco.data

import com.xavierclavel.cooknco.platform.OcrLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The parser over pages laid out the way a reader hands them over: text with a box, never a
 * string. Every fixture here is built from the geometry, because the geometry is half the
 * rule — a two-column page and a one-column page holding the same words are different
 * recipes, and a flat list of lines cannot tell them apart.
 */
class RecipeScanTest {

    private val lineHeight = 0.03f
    private val lineGap = 0.015f

    /** Lays texts out one under the other, inside the given horizontal band. */
    private fun column(
        texts: List<String>,
        left: Float = 0f,
        right: Float = 1f,
        from: Float = 0f,
        height: Float = lineHeight,
        gap: Float = lineGap,
        page: Int = 0,
    ): List<OcrLine> = texts.mapIndexed { index, text ->
        val top = from + index * (height + gap)
        OcrLine(text = text, page = page, left = left, top = top, right = right, bottom = top + height)
    }

    private fun heading(text: String, height: Float = 0.06f, page: Int = 0) =
        listOf(OcrLine(text = text, page = page, left = 0.1f, top = 0f, right = 0.9f, bottom = height))

    private fun ingredientNamed(recipe: ScannedRecipe, name: String) =
        recipe.ingredients.firstOrNull { it.name == name }

    // ── A plain French card ───────────────────────────────────────────────────

    @Test
    fun `reads a single column french card`() {
        val lines = heading("Tarte aux pommes") + column(
            listOf(
                "Pour 6 personnes",
                "Préparation : 20 min — Cuisson : 40 min",
                "Ingrédients",
                "- 1 pâte brisée",
                "- 6 pommes",
                "- 100 g de sucre",
                "- 2 œufs",
                "Préparation",
                "1. Préchauffer le four à 180 °C.",
                "2. Éplucher les pommes et les couper en lamelles.",
                "3. Étaler la pâte dans un moule.",
            ),
            from = 0.1f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals("Tarte aux pommes", recipe.title)
        assertEquals(6, recipe.yield)
        assertEquals(20, recipe.prepMinutes)
        assertEquals(40, recipe.cookMinutes)
        assertEquals(180, recipe.temperatureCelsius)
        assertEquals(
            listOf("pâte brisée", "pommes", "sucre", "œufs"),
            recipe.ingredients.map { it.name },
        )
        assertEquals(listOf(1f, 6f, 100f, 2f), recipe.ingredients.map { it.amount })
        assertEquals(listOf("UNIT", "UNIT", "GRAM", "UNIT"), recipe.ingredients.map { it.unit })
        assertEquals(3, recipe.steps.size)
        assertEquals("Préchauffer le four à 180 °C.", recipe.steps[0])
        assertEquals("Étaler la pâte dans un moule.", recipe.steps[2])
    }

    @Test
    fun `reads a single column english card`() {
        val lines = heading("Banana Bread") + column(
            listOf(
                "Serves 8",
                "Prep time: 15 min",
                "Cook time: 1h10",
                "Ingredients",
                "• 2 cups flour",
                "• 1/2 cup sugar",
                "• 3 ripe bananas",
                "• 1 tsp baking soda",
                "Method",
                "1. Preheat the oven to 350°F.",
                "2. Mash the bananas in a large bowl.",
            ),
            from = 0.1f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals("Banana Bread", recipe.title)
        assertEquals(8, recipe.yield)
        assertEquals(15, recipe.prepMinutes)
        assertEquals(70, recipe.cookMinutes)
        assertEquals(176, recipe.temperatureCelsius)
        // "ripe" is part of what to buy, so it stays on the name rather than being dropped.
        assertEquals(listOf("flour", "sugar", "ripe bananas", "baking soda"), recipe.ingredients.map { it.name })
        assertEquals(listOf("CUP", "CUP", "UNIT", "TEASPOON"), recipe.ingredients.map { it.unit })
        assertEquals(0.5f, ingredientNamed(recipe, "sugar")?.amount)
        assertEquals(2, recipe.steps.size)
    }

    // ── Two columns ───────────────────────────────────────────────────────────

    @Test
    fun `reads the ingredient column before the method beside it`() {
        // A cookbook page: ingredients left, method right, both starting at the same height.
        // Read top to bottom the two interleave, which is exactly what must not happen.
        val lines = heading("Soupe de courge") +
            column(
                listOf("Ingrédients", "- 1 courge", "- 2 oignons", "- 50 cl de bouillon", "- 20 cl de crème"),
                left = 0.02f,
                right = 0.44f,
                from = 0.15f,
            ) +
            column(
                listOf(
                    "Préparation",
                    "Éplucher la courge et la couper en cubes.",
                    "Faire revenir les oignons.",
                    "Mixer le tout avec la crème.",
                    "Servir bien chaud.",
                ),
                left = 0.56f,
                right = 0.98f,
                from = 0.15f,
            )

        val recipe = RecipeScan.parse(lines)

        assertEquals("Soupe de courge", recipe.title)
        assertEquals(listOf("courge", "oignons", "bouillon", "crème"), recipe.ingredients.map { it.name })
        assertEquals(listOf("UNIT", "UNIT", "CENTILITER", "CENTILITER"), recipe.ingredients.map { it.unit })
        assertEquals(4, recipe.steps.size)
        assertEquals("Éplucher la courge et la couper en cubes.", recipe.steps.first())
        assertEquals("Servir bien chaud.", recipe.steps.last())
    }

    @Test
    fun `a single column page is not split into two`() {
        // Ordinary prose: no gutter, so nothing may be reordered.
        val lines = heading("Omelette") + column(
            listOf(
                "Ingrédients",
                "- 3 œufs",
                "- 10 g de beurre",
                "- sel",
                "- poivre",
                "Préparation",
                "Battre les œufs.",
                "Faire fondre le beurre dans une poêle.",
                "Verser les œufs et cuire deux minutes.",
                "Saler et poivrer.",
            ),
            from = 0.1f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(listOf("œufs", "beurre", "sel", "poivre"), recipe.ingredients.map { it.name })
        assertEquals("Battre les œufs.", recipe.steps.first())
        assertEquals("Saler et poivrer.", recipe.steps.last())
    }

    // ── Broken lines ──────────────────────────────────────────────────────────

    @Test
    fun `rejoins a sentence the reader broke at the column edge`() {
        val lines = column(
            listOf(
                "Préparation",
                "Faire chauffer le beurre dans une grande",
                "poêle, puis ajouter les oignons émincés",
                "et laisser fondre dix minutes.",
                "Servir aussitôt.",
            ),
            from = 0.1f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(
            listOf(
                "Faire chauffer le beurre dans une grande poêle, puis ajouter les oignons " +
                    "émincés et laisser fondre dix minutes.",
                "Servir aussitôt.",
            ),
            recipe.steps,
        )
    }

    @Test
    fun `does not merge a bulletless list of lowercase ingredients`() {
        val lines = column(
            listOf("Ingrédients", "farine", "sucre", "œufs", "beurre demi-sel"),
            from = 0.1f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(listOf("farine", "sucre", "œufs", "beurre demi-sel"), recipe.ingredients.map { it.name })
    }

    @Test
    fun `does not merge lines set further apart than their neighbours`() {
        // The second line starts lowercase and follows a long one, but sits a paragraph away.
        val lines = column(listOf("Préparation"), from = 0.0f) +
            column(listOf("Mélanger la farine et le sucre dans un saladier"), from = 0.10f) +
            column(listOf("battre les œufs à part, puis les incorporer."), from = 0.30f)

        val recipe = RecipeScan.parse(lines)

        assertEquals(2, recipe.steps.size)
    }

    // ── The title ─────────────────────────────────────────────────────────────

    @Test
    fun `takes the biggest line at the top, not the first`() {
        val lines = listOf(
            OcrLine("Recettes d'automne — page 42", page = 0, left = 0f, top = 0f, right = 0.6f, bottom = 0.02f),
            OcrLine("Velouté de potiron", page = 0, left = 0f, top = 0.05f, right = 0.8f, bottom = 0.12f),
        ) + column(listOf("- 1 potiron", "- 50 cl de lait"), from = 0.2f)

        assertEquals("Velouté de potiron", RecipeScan.parse(lines).title)
    }

    @Test
    fun `does not take a section header for the title`() {
        // The heading is the biggest text on the page, and it is not the name of the dish.
        val lines = listOf(
            OcrLine("INGRÉDIENTS", page = 0, left = 0f, top = 0.02f, right = 0.9f, bottom = 0.12f),
        ) + column(listOf("- 200 g de farine", "- 1 œuf"), from = 0.2f)

        assertNull(RecipeScan.parse(lines).title)
    }

    @Test
    fun `does not take the times line for the title`() {
        val lines = listOf(
            OcrLine("Préparation : 20 min", page = 0, left = 0f, top = 0.02f, right = 0.9f, bottom = 0.12f),
        ) + column(listOf("- 200 g de farine"), from = 0.2f)

        val recipe = RecipeScan.parse(lines)

        assertNull(recipe.title)
        assertEquals(20, recipe.prepMinutes)
    }

    // ── Telling a step from an ingredient ─────────────────────────────────────

    @Test
    fun `a numbered sentence is a step even with no header above it`() {
        val lines = column(
            listOf(
                "1. Préchauffer le four et beurrer le moule.",
                "2. Mélanger la farine et les œufs.",
            ),
            from = 0.4f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(2, recipe.steps.size)
        assertTrue(recipe.ingredients.isEmpty())
        assertEquals("Préchauffer le four et beurrer le moule.", recipe.steps.first())
    }

    @Test
    fun `a bulleted sentence is a step, not an ingredient`() {
        val lines = column(
            listOf("• Faire revenir les oignons dans le beurre jusqu'à coloration."),
            from = 0.4f,
        )

        val recipe = RecipeScan.parse(lines)

        assertTrue(recipe.ingredients.isEmpty())
        assertEquals(1, recipe.steps.size)
    }

    @Test
    fun `the ingredients header keeps a short sentence out of the steps`() {
        val lines = column(
            listOf("Ingrédients", "sel et poivre", "huile d'olive"),
            from = 0.4f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(listOf("sel et poivre", "huile d'olive"), recipe.ingredients.map { it.name })
        assertTrue(recipe.steps.isEmpty())
    }

    @Test
    fun `a step naming an oven temperature stays a step`() {
        val lines = column(
            listOf("Préparation", "Préchauffer le four à 180 °C en chaleur tournante."),
            from = 0.2f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(1, recipe.steps.size)
        assertEquals(180, recipe.temperatureCelsius)
    }

    @Test
    fun `an english step is not read as a cooking time`() {
        val lines = column(
            listOf("Method", "Cook for 20 minutes, then leave to rest."),
            from = 0.2f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(1, recipe.steps.size)
        assertNull(recipe.cookMinutes)
    }

    @Test
    fun `preparer is a step and not a preparation time`() {
        val lines = column(
            listOf("Préparation", "Préparer les pommes 15 min avant de commencer."),
            from = 0.2f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(1, recipe.steps.size)
        assertNull(recipe.prepMinutes)
    }

    // ── Amounts ───────────────────────────────────────────────────────────────

    @Test
    fun `reads the quantities a recipe is written with`() {
        val written = mapOf(
            "- 100 g de farine" to (100f to "GRAM"),
            "- 1,5 kg de pommes de terre" to (1.5f to "KILOGRAM"),
            "- 1/2 citron" to (0.5f to "UNIT"),
            "- 1 1/2 cup flour" to (1.5f to "CUP"),
            "- ½ oignon" to (0.5f to "UNIT"),
            "- 2 ½ litres de bouillon" to (2.5f to "LITER"),
            "- 2-3 gousses d'ail" to (2f to "UNIT"),
            "- 250 ml de lait" to (250f to "MILLILITERS"),
            "- 3 c. à s. d'huile" to (3f to "TABLESPOON"),
            "- 1 c. à c. de sel" to (1f to "TEASPOON"),
            "- 2 tbsp olive oil" to (2f to "TABLESPOON"),
            "- 8 oz cream cheese" to (8f to "OUNCE"),
        )

        written.forEach { (line, expected) ->
            val recipe = RecipeScan.parse(column(listOf("Ingrédients", line), from = 0.4f))
            val ingredient = recipe.ingredients.singleOrNull()
            assertNotNull(ingredient, "no ingredient read from \"$line\"")
            assertEquals(expected.first, ingredient.amount, "amount of \"$line\"")
            assertEquals(expected.second, ingredient.unit, "unit of \"$line\"")
        }
    }

    @Test
    fun `reads the ingredient a quantity belongs to`() {
        val written = mapOf(
            "- 100 g de farine" to "farine",
            "- 2 gousses d'ail" to "ail",
            "- 1 pincée de sel" to "sel",
            "- 250 ml de lait entier" to "lait entier",
            "- 2 cups plain flour" to "plain flour",
            "- 3 pommes" to "pommes",
        )

        written.forEach { (line, expected) ->
            val recipe = RecipeScan.parse(column(listOf("Ingrédients", line), from = 0.4f))
            assertEquals(expected, recipe.ingredients.singleOrNull()?.name, "name of \"$line\"")
        }
    }

    @Test
    fun `keeps what follows the ingredient as its complement`() {
        val recipe = RecipeScan.parse(
            column(
                listOf("Ingrédients", "- 200 g de beurre, mou", "- 2 gousses d'ail", "- 100 g de sucre (roux)"),
                from = 0.4f,
            )
        )

        assertEquals("mou", ingredientNamed(recipe, "beurre")?.complement)
        // The clove is what is being counted, and it is kept beside the garlic the catalogue knows.
        assertEquals("gousses", ingredientNamed(recipe, "ail")?.complement)
        assertEquals("roux", ingredientNamed(recipe, "sucre")?.complement)
    }

    @Test
    fun `an ingredient with no amount carries no unit`() {
        val recipe = RecipeScan.parse(column(listOf("Ingrédients", "- sel fin"), from = 0.4f))

        val ingredient = recipe.ingredients.single()
        assertEquals("sel fin", ingredient.name)
        assertNull(ingredient.amount)
        assertEquals("NONE", ingredient.unit)
    }

    @Test
    fun `a one letter unit does not swallow the word it starts`() {
        val recipe = RecipeScan.parse(
            column(listOf("Ingrédients", "- 1 gousse de vanille", "- 2 litres d'eau"), from = 0.4f)
        )

        assertEquals("vanille", recipe.ingredients[0].name)
        assertEquals("eau", recipe.ingredients[1].name)
        assertEquals("LITER", recipe.ingredients[1].unit)
    }

    // ── Temperature ───────────────────────────────────────────────────────────

    @Test
    fun `reads the oven setting however it is written`() {
        val written = mapOf(
            "Préchauffer à 180 °C." to 180,
            "Préchauffer à 210°C." to 210,
            "Preheat the oven to 350°F." to 176,
            "Enfourner à th. 6." to 180,
            "Enfourner à thermostat 7." to 210,
        )

        written.forEach { (line, expected) ->
            val recipe = RecipeScan.parse(column(listOf("Préparation", line), from = 0.2f))
            assertEquals(expected, recipe.temperatureCelsius, "temperature of \"$line\"")
        }
    }

    // ── Sections at the edges ─────────────────────────────────────────────────

    @Test
    fun `collects the blurb under the title as the description`() {
        val lines = heading("Gratin dauphinois") + column(
            listOf(
                "Un classique du Dauphiné, crémeux et doré.",
                "Ingrédients",
                "- 1 kg de pommes de terre",
                "Préparation",
                "Éplucher et émincer les pommes de terre.",
            ),
            from = 0.1f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals("Un classique du Dauphiné, crémeux et doré.", recipe.description)
        assertEquals(1, recipe.ingredients.size)
        assertEquals(1, recipe.steps.size)
    }

    @Test
    fun `collects a notes section as the tips`() {
        val lines = column(
            listOf(
                "Préparation",
                "Cuire au four pendant quarante minutes.",
                "Astuce",
                "Se réchauffe très bien le lendemain.",
            ),
            from = 0.2f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(1, recipe.steps.size)
        assertEquals("Se réchauffe très bien le lendemain.", recipe.tips)
    }

    @Test
    fun `reads a sub-header inside the ingredients as more ingredients`() {
        val lines = column(
            listOf(
                "Ingrédients",
                "- 200 g de farine",
                "Pour la garniture",
                "- 3 pommes",
                "- 50 g de sucre",
            ),
            from = 0.2f,
        )

        val recipe = RecipeScan.parse(lines)

        assertEquals(listOf("farine", "pommes", "sucre"), recipe.ingredients.map { it.name })
        assertTrue(recipe.steps.isEmpty())
    }

    // ── Nothing to read ───────────────────────────────────────────────────────

    @Test
    fun `an empty scan reads as an empty recipe`() {
        val recipe = RecipeScan.parse(emptyList())

        assertTrue(recipe.isEmpty)
        assertNull(recipe.title)
    }

    @Test
    fun `a page with no recipe on it reads as empty`() {
        val recipe = RecipeScan.parse(column(listOf("", "   "), from = 0.1f))

        assertTrue(recipe.isEmpty)
    }

    // ── Several pages ─────────────────────────────────────────────────────────

    @Test
    fun `reads a recipe that runs over two pages`() {
        val lines = heading("Pot-au-feu") +
            column(listOf("Ingrédients", "- 1 kg de bœuf", "- 4 carottes"), from = 0.1f) +
            column(
                listOf("Préparation", "Saisir la viande de tous les côtés.", "Ajouter les légumes."),
                from = 0.05f,
                page = 1,
            )

        val recipe = RecipeScan.parse(lines)

        assertEquals("Pot-au-feu", recipe.title)
        assertEquals(listOf("bœuf", "carottes"), recipe.ingredients.map { it.name })
        assertEquals(2, recipe.steps.size)
    }
}
