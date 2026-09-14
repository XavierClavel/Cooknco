package shared.utils

import shared.enums.AmountUnit
import shared.enums.MeasurementType
import shared.enums.UnitSystem

/**
 * The one place that decides which units an ingredient may be measured in. A conversion factor being
 * present *is* the capability, so there is no separate flag that could contradict it.
 */
object UnitCapabilities {
    fun allowedTypes(
        gramsPerUnit: Float?,
        gramsPerMilliliter: Float?,
        measurableByWeight: Boolean,
    ): Set<MeasurementType> = buildSet {
        add(MeasurementType.NONE)
        if (gramsPerUnit != null) add(MeasurementType.AMOUNT)
        if (measurableByWeight) add(MeasurementType.WEIGHT)
        if (gramsPerMilliliter != null) add(MeasurementType.VOLUME)
    }

    /**
     * The unit an editor should preselect: the ingredient's own choice when it still applies,
     * otherwise weight, otherwise whatever the ingredient does support — each of them put on
     * the ladder the author cooks on ([UnitConversion.preferredFor]).
     *
     * The declared unit is mapped rather than kept because it cannot have been a choice about
     * the reader: an operator sets it once for the whole catalogue, so "flour is measured by
     * weight, starting small" is what they said, and grams is only how it is said in metric.
     * What they picked *within* a family survives the mapping — see [UnitConversion.preferredFor].
     *
     * Only the preselection. Nothing here rewrites a unit already stored on a recipe: the
     * author picks from this and what they pick is what is saved, whoever reads it after.
     */
    fun defaultUnitFor(
        declared: AmountUnit?,
        allowedTypes: Set<MeasurementType>,
        system: UnitSystem = UnitSystem.DEFAULT,
    ): AmountUnit {
        val unit = declared?.takeIf { it.type in allowedTypes }
            ?: AmountUnit.GRAM.takeIf { it.type in allowedTypes }
            ?: AmountUnit.entries.firstOrNull { it != AmountUnit.NONE && it.type in allowedTypes }
            ?: AmountUnit.NONE
        return UnitConversion.preferredFor(unit, system)
    }
}
