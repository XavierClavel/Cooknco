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
 */
enum class AmountUnit(val type: MeasurementType, val factorToBase: Float) {
    NONE(MeasurementType.NONE, 0f),

    UNIT(MeasurementType.AMOUNT, 1f),

    GRAM(MeasurementType.WEIGHT, 1f),
    KILOGRAM(MeasurementType.WEIGHT, 1_000f),
    POUND(MeasurementType.WEIGHT, 453.59237f),

    MILLILITERS(MeasurementType.VOLUME, 1f),
    CENTILITER(MeasurementType.VOLUME, 10f),
    LITER(MeasurementType.VOLUME, 1_000f),
    TEASPOON(MeasurementType.VOLUME, 5f),
    TABLESPOON(MeasurementType.VOLUME, 15f),
    CUP(MeasurementType.VOLUME, 240f),
    ;

    /** Converts [amount] of this unit into the base unit of [type]. */
    fun toBase(amount: Float) = amount * factorToBase

    companion object {
        fun of(type: MeasurementType) = entries.filter { it.type == type }
    }
}
