package shared.infodto

import shared.enums.AmountUnit
import shared.enums.MeasurementType
import kotlinx.serialization.Serializable

/**
 * Unit metadata as served to clients, so neither the web app nor the mobile app has to restate which
 * family a unit belongs to or how it converts.
 */
@Serializable
data class UnitInfo(
    val name: AmountUnit,
    val type: MeasurementType,
    val factorToBase: Float,
) {
    companion object {
        fun of(unit: AmountUnit) = UnitInfo(
            name = unit,
            type = unit.type,
            factorToBase = unit.factorToBase,
        )

        fun all() = AmountUnit.entries.map { of(it) }
    }
}
