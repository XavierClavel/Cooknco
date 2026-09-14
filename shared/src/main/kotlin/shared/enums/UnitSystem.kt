package shared.enums

/**
 * The family of units a cook reads amounts in.
 *
 * A recipe is *stored* in whatever its author wrote — see `RecipeIngredient.unit` — and this
 * only decides what a reader is shown. That separation is the whole feature: a French cook
 * writing 800 g and an American one reading 1.76 lb are looking at the same recipe, and
 * neither can rewrite the other's copy by opening it.
 *
 * Two values and no third: this says which of the two ladders in [AmountUnit] an amount is
 * rendered on, not which country it came from. Units that belong to neither ladder —
 * spoons, and countable pieces — are left exactly as written for both.
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    ;

    companion object {
        /**
         * What an account that has never said anything reads in.
         *
         * Metric because that is what every recipe in the product was authored in while
         * there was no choice to make, so nothing anybody is already reading changes.
         */
        val DEFAULT = METRIC
    }
}
