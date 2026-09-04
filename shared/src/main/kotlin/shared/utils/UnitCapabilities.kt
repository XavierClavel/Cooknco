package shared.utils

import shared.enums.AmountUnit
import shared.enums.MeasurementType

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
     * otherwise grams, otherwise whatever the ingredient does support.
     */
    fun defaultUnitFor(declared: AmountUnit?, allowedTypes: Set<MeasurementType>): AmountUnit =
        declared?.takeIf { it.type in allowedTypes }
            ?: AmountUnit.GRAM.takeIf { it.type in allowedTypes }
            ?: AmountUnit.entries.firstOrNull { it != AmountUnit.NONE && it.type in allowedTypes }
            ?: AmountUnit.NONE
}
