package shared.infodto

import shared.enums.AmountUnit
import shared.enums.MeasurementType
import shared.enums.UnitSystem
import kotlinx.serialization.Serializable

/**
 * Unit metadata as served to clients, so neither the web app nor the mobile app has to restate which
 * family a unit belongs to or how it converts.
 *
 * [system] and [isDisplayUnit] are here for the same reason the rest is: converting an amount
 * into the units its reader cooks in needs no table beyond these two fields and
 * [factorToBase], so a client does the conversion without ever naming a unit in its own code.
 * Adding a unit is then a change to [AmountUnit] alone — see [shared.utils.UnitConversion].
 */
@Serializable
data class UnitInfo(
    val name: AmountUnit,
    val type: MeasurementType,
    val factorToBase: Float,
    /** The ladder this unit belongs to, or null when it belongs to neither and is never converted. */
    val system: UnitSystem? = null,
    /** Whether a conversion may land on this unit, as opposed to merely start from it. */
    val isDisplayUnit: Boolean = false,
) {
    companion object {
        fun of(unit: AmountUnit) = UnitInfo(
            name = unit,
            type = unit.type,
            factorToBase = unit.factorToBase,
            system = unit.system,
            isDisplayUnit = unit.isDisplayUnit,
        )

        fun all() = AmountUnit.entries.map { of(it) }
    }
}
