package shared.enums

/**
 * A unit an ingredient amount can be expressed in.
 *
 * [factorToBase] converts an amount into the base unit of its [type] — grams for [MeasurementType.WEIGHT],
 * millilitres for [MeasurementType.VOLUME], pieces for [MeasurementType.AMOUNT] — which is what makes
 * scaling, display formatting and cross-unit comparison possible without per-unit special cases.
 *
 * This enum is the single source of truth for unit metadata: clients read it from `GET /api/v1/unit`
 * rather than restating the families or the conversions.
 *
 * [system] and [isDisplayUnit] are what let a reader see an amount in the units they cook in
 * ([shared.utils.UnitConversion]), and they answer two different questions:
 *
 * - [system] is the ladder this unit *belongs to*, and so whether an amount written in it is
 *   converted away for a reader on the other ladder. Null means it belongs to neither and is
 *   never converted in either direction: a countable piece has no metric or imperial form, and
 *   a tablespoon is a tablespoon to everybody — "2 tbsp" reading "30 mL" to half the world
 *   would be a worse recipe, not a localised one.
 * - [isDisplayUnit] is whether a conversion may *land* on it. Every display unit is on its
 *   system's ladder, but not every unit on a ladder is a display unit: a recipe may be written
 *   in centilitres, and is read back in millilitres or litres like every other metric volume.
 */
enum class AmountUnit(
    val type: MeasurementType,
    val factorToBase: Float,
    val system: UnitSystem? = null,
    val isDisplayUnit: Boolean = false,
) {
    NONE(MeasurementType.NONE, 0f),

    UNIT(MeasurementType.AMOUNT, 1f),

    GRAM(MeasurementType.WEIGHT, 1f, UnitSystem.METRIC, isDisplayUnit = true),
    KILOGRAM(MeasurementType.WEIGHT, 1_000f, UnitSystem.METRIC, isDisplayUnit = true),
    OUNCE(MeasurementType.WEIGHT, 28.349523f, UnitSystem.IMPERIAL, isDisplayUnit = true),
    POUND(MeasurementType.WEIGHT, 453.59237f, UnitSystem.IMPERIAL, isDisplayUnit = true),

    MILLILITERS(MeasurementType.VOLUME, 1f, UnitSystem.METRIC, isDisplayUnit = true),
    // Metric, and offered in the editor, but never converted *into*: the product has always
    // rolled millilitres up to litres and never to centilitres, and a reader who asked for
    // metric asked for the ladder they already knew.
    CENTILITER(MeasurementType.VOLUME, 10f, UnitSystem.METRIC),
    LITER(MeasurementType.VOLUME, 1_000f, UnitSystem.METRIC, isDisplayUnit = true),
    FLUID_OUNCE(MeasurementType.VOLUME, 29.57353f, UnitSystem.IMPERIAL, isDisplayUnit = true),
    TEASPOON(MeasurementType.VOLUME, 5f),
    TABLESPOON(MeasurementType.VOLUME, 15f),
    CUP(MeasurementType.VOLUME, 240f, UnitSystem.IMPERIAL, isDisplayUnit = true),
    ;

    /** Converts [amount] of this unit into the base unit of [type]. */
    fun toBase(amount: Float) = amount * factorToBase

    companion object {
        fun of(type: MeasurementType) = entries.filter { it.type == type }
    }
}
